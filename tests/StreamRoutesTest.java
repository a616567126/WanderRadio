package com.wanderadio.app;

import java.util.List;
import org.json.JSONObject;

public final class StreamRoutesTest {
    public static void main(String[] args) throws Exception {
        Station station=new Station(new JSONObject().put("stationuuid","test")
            .put("url","http://origin.example/radio.m3u8")
            .put("url_resolved","http://resolved.example/live.m3u8").put("hls",1));
        List<String> routes=StreamRoutes.forStation(station);
        if(!routes.equals(List.of("https://resolved.example/live.m3u8","https://origin.example/radio.m3u8",
            "http://resolved.example/live.m3u8","http://origin.example/radio.m3u8")))throw new AssertionError(routes);
        if(!new Station(station.json()).hls)throw new AssertionError("HLS metadata lost");
        Station secure=new Station(new JSONObject().put("url","https://example.com/live"));
        if(StreamRoutes.forStation(secure).size()!=1)throw new AssertionError("Duplicate route");
        System.out.println("PASS: HTTPS preference, original fallback, deduplication, HLS persistence");
    }
}
