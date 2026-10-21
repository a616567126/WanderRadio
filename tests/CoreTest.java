package com.wanderadio.app;

import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;

/** JVM checks for catalogue data and search behavior; does not simulate Android playback. */
public final class CoreTest {
    private static int checks;
    private static void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
        checks++;
    }
    private static JSONObject sample() throws Exception {
        return new JSONObject("{\"stationuuid\":\"abc\",\"name\":\"Test Jazz\",\"url\":\"https://example.org/original.mp3\",\"url_resolved\":\"https://example.org/live.mp3\",\"countrycode\":\"JP\",\"state\":\"Tokyo\",\"tags\":\"jazz,blues\",\"codec\":\"MP3\",\"bitrate\":128,\"geo_lat\":35.68,\"geo_long\":139.76}");
    }
    public static void main(String[] args) throws Exception {
        Station station=new Station(sample());
        check(station.usable(),"Valid HTTPS stream must be accepted");
        check(station.url.endsWith("live.mp3"),"Resolved URL must be preferred");
        check(station.located(),"Valid coordinates must be mapped");
        check(station.matches("jAzZ") && station.matches("JP") && station.matches("Tokyo"),"Search must match name, tags, country code and state without case sensitivity");
        check(station.country.equals("日本"),"Country must be localized for Chinese UI");
        Station noResolved=new Station(sample().put("url_resolved",""));
        check(noResolved.url.endsWith("original.mp3"),"Missing resolved URL must fall back to original");
        Station invalid=new Station(sample().put("url_resolved","file:///etc/passwd"));
        check(!invalid.usable(),"Non-HTTP stream must be rejected");
        Station missing=new Station(sample().put("geo_lat",JSONObject.NULL).put("geo_long",JSONObject.NULL).put("bitrate",0));
        check(!missing.located() && missing.quality().contains("未知"),"Missing coordinates and bitrate must not be fabricated");
        check(!new Station(sample().put("geo_lat",95)).located(),"Out-of-range latitude must not be mapped");
        Station roundTrip=new Station(station.json());
        check(roundTrip.id.equals(station.id) && roundTrip.url.equals(station.url) && roundTrip.latitude==station.latitude,"Saved station must preserve identity, resolved URL and coordinates");
        List<Station> deduped=StationRepository.parse("["+sample()+","+sample().put("name","Updated")+"]");
        check(deduped.size()==1 && deduped.get(0).name.equals("Updated"),"Duplicate UUID must not create duplicate stations");
        boolean rejected=false;
        try { StationRepository.parse("[]"); } catch(Exception expected) { rejected=true; }
        check(rejected,"Empty catalogue must fail rather than replace cache");
        rejected=false;
        try { StationRepository.parse("not JSON"); } catch(Exception expected) { rejected=true; }
        check(rejected,"Malformed catalogue must fail clearly");
        check(Station.flagFor("CN").equals("🇨🇳") && Station.flagFor("jp").equals("🇯🇵"),"Flags must use normalized country codes");
        check(Station.flagFor("").equals("🌐") && Station.flagFor("123").equals("🌐"),"Invalid country codes must not produce a fake flag");
        check(station.inRegion("JP", "Tokyo") && station.inRegion("JP", "") && station.inRegion("", ""),"Country and state filters must compose and allow all regions");
        check(!station.inRegion("US", "Tokyo") && !station.inRegion("JP", "Osaka"),"Country and state filters must exclude mismatched stations");
        check(station.location().startsWith("🇯🇵"),"Station location must display its country flag");
        if(args.length>0) {
            List<Station> live=StationRepository.parse(Files.readString(Path.of(args[0])));
            check(!live.isEmpty(),"Recorded live API response must parse");
            check(live.stream().allMatch(Station::usable),"Live API response must contain only usable HTTP station addresses");
            System.out.println("Live catalogue parsed: "+live.size()+" entries");
        }
        System.out.println("PASS: "+checks+" checks");
    }
}
