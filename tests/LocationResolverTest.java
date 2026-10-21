package com.wanderadio.app;

import org.json.JSONObject;
import java.util.*;

public final class LocationResolverTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static Station station(String id, String country, String state, Double lat, Double lon) throws Exception {
        JSONObject value = new JSONObject().put("stationuuid", id).put("url", "https://example.org/live.mp3")
            .put("countrycode", country).put("state", state);
        if (lat != null) value.put("geo_lat", lat);
        if (lon != null) value.put("geo_long", lon);
        return new Station(value);
    }
    public static void main(String[] args) throws Exception {
        Map<String,double[]> countries = new HashMap<>();
        countries.put("KR", new double[]{37,127.5}); countries.put("US", new double[]{38,-97});
        LocationResolver resolver = new LocationResolver(countries);
        Station known = station("known","KR","Seoul",37.5,127.0);
        Station missing = station("missing","KR","Seoul",null,null);
        resolver.setCatalogue(Arrays.asList(known,missing));
        LocationResolver.Location location = resolver.resolve(known);
        check(location.precision==LocationResolver.Precision.STATION && location.latitude==37.5,
            "A directory coordinate must take precedence over approximate anchors");
        location = resolver.resolve(missing);
        check(location.precision==LocationResolver.Precision.REGION && Math.abs(location.latitude-37.5)<.0001,
            "Stations without coordinates can use same-country, same-region anchors");
        check(location.label().contains("近似"),"Inferred region locations must be labelled approximate");
        check(!missing.located() && !missing.json().has("geo_lat"),"Approximate locations must not overwrite raw station coordinates");
        location=resolver.resolve(station("country","KR","",null,null));
        check(location.precision==LocationResolver.Precision.COUNTRY && location.longitude==127.5,
            "Country reference coordinates must cover stations without region data");
        location=resolver.resolve(station("other","US","Seoul",null,null));
        check(location.precision==LocationResolver.Precision.COUNTRY && location.longitude==-97,
            "Equal region names in different countries must never share an anchor");
        location=resolver.resolve(station("unknown","ZZ","",null,null));
        check(!location.available() && location.precision==LocationResolver.Precision.UNKNOWN,
            "Unknown country codes must not fabricate locations");
        resolver.setCatalogue(Collections.emptyList());
        check(resolver.resolve(missing).precision==LocationResolver.Precision.COUNTRY,
            "Catalogue refresh must invalidate cached region inferences");
        check(resolver.resolve(station("invalid","KR","",100.0,127.0)).precision==LocationResolver.Precision.COUNTRY,
            "Out-of-range raw coordinates must fall back rather than plot invalid points");
        System.out.println("PASS: "+checks+" location resolver checks");
    }
}
