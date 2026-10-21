package com.wanderadio.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered public stream candidates. Keep the original address as a fallback. */
public final class StreamRoutes {
    private StreamRoutes() { }
    public static List<String> forStation(Station station) {
        LinkedHashSet<String> values=new LinkedHashSet<>();
        addSecure(values,station.url);
        addSecure(values,station.originalUrl);
        add(values,station.url);
        add(values,station.originalUrl);
        return new ArrayList<>(values);
    }
    private static void addSecure(LinkedHashSet<String> values,String url) {
        if(url.startsWith("http://"))values.add("https://"+url.substring(7));
        else add(values,url);
    }
    private static void add(LinkedHashSet<String> values,String url) {
        if(url.startsWith("https://") || url.startsWith("http://"))values.add(url);
    }
}
