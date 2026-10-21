package com.wanderadio.app;

import org.json.JSONObject;
import java.util.Locale;

public final class Station {
    public final String id, name, url, originalUrl, country, countryCode, state, tags, codec;
    public final int bitrate;
    public final boolean hls;
    public final double latitude, longitude;
    public Station(JSONObject j) {
        id = j.optString("stationuuid"); name = j.optString("name", "未命名电台");
        originalUrl = j.optString("url");
        String resolved = j.optString("url_resolved"); url = resolved.isEmpty() ? originalUrl : resolved;
        countryCode = j.optString("countrycode").trim().toUpperCase(Locale.ROOT);
        String localized = new Locale("", countryCode).getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
        country = localized.isEmpty() ? j.optString("country", "未知地区") : localized;
        state = j.optString("state").trim(); tags = j.optString("tags"); codec = j.optString("codec");
        hls = j.optInt("hls") == 1;
        bitrate = j.optInt("bitrate"); latitude = j.optDouble("geo_lat", Double.NaN); longitude = j.optDouble("geo_long", Double.NaN);
    }
    public boolean usable() { return !id.isEmpty() && (url.startsWith("https://") || url.startsWith("http://")); }
    public boolean located() { return Double.isFinite(latitude) && Double.isFinite(longitude) && Math.abs(latitude)<=90 && Math.abs(longitude)<=180; }
    public static String flagFor(String code) {
        String value=code==null?"":code.trim().toUpperCase(Locale.ROOT);
        if(!value.matches("[A-Z]{2}"))return "🌐";
        return new String(Character.toChars(0x1F1E6+value.charAt(0)-'A'))+new String(Character.toChars(0x1F1E6+value.charAt(1)-'A'));
    }
    public String countryLabel() { return flagFor(countryCode)+" "+country; }
    public boolean inRegion(String code,String region) {
        return (code.isEmpty()||countryCode.equals(code))&&(region.isEmpty()||state.equals(region));
    }
    public String location() { return countryLabel() + (state.trim().isEmpty() ? "" : " · " + state); }
    public String quality() { return codec + (bitrate > 0 ? " · " + bitrate + " kbps" : " · 码率未知"); }
    public boolean matches(String query) { return (name + " " + country + " " + countryCode + " " + state + " " + tags).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)); }
    public JSONObject json() {
        JSONObject j = new JSONObject();
        try {
            j.put("stationuuid",id).put("name",name).put("url",originalUrl).put("url_resolved",url)
                .put("countrycode",countryCode).put("state",state).put("tags",tags).put("codec",codec).put("bitrate",bitrate).put("hls",hls?1:0);
            if (located()) j.put("geo_lat",latitude).put("geo_long",longitude);
        } catch (Exception ignored) { }
        return j;
    }
}
