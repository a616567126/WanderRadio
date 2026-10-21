package com.wanderadio.app;

import java.text.Collator;
import java.util.*;

/** Immutable geography choices built once per catalogue snapshot, off the UI thread. */
public final class GeographyIndex {
    public final List<Station> all;
    public final List<String> countryCodes, countryLabels;
    private final Map<String,List<Station>> countries=new HashMap<>();
    private final Map<String,List<Station>> regions=new HashMap<>();
    private final Map<String,List<String>> regionNames=new HashMap<>();
    public GeographyIndex(List<Station> source) {
        all=Collections.unmodifiableList(new ArrayList<>(source));
        Map<String,String> labels=new HashMap<>();
        Map<String,Set<String>> names=new HashMap<>();
        for(Station station:source) {
            countries.computeIfAbsent(station.countryCode,ignored->new ArrayList<>()).add(station);
            regions.computeIfAbsent(key(station.countryCode,station.state),ignored->new ArrayList<>()).add(station);
            if(!station.countryCode.isEmpty() && !labels.containsKey(station.countryCode))labels.put(station.countryCode,station.countryLabel());
            if(!station.state.isEmpty())names.computeIfAbsent(station.countryCode,ignored->new HashSet<>()).add(station.state);
        }
        Collator order=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        ArrayList<String> codes=new ArrayList<>(labels.keySet());
        codes.sort((a,b)->order.compare(labels.get(a),labels.get(b)));
        countryCodes=Collections.unmodifiableList(codes);
        ArrayList<String> titles=new ArrayList<>();titles.add("🌐 全部国家/地区");
        for(String code:codes)titles.add(labels.get(code));
        countryLabels=Collections.unmodifiableList(titles);
        for(Map.Entry<String,Set<String>> entry:names.entrySet()) {
            ArrayList<String> values=new ArrayList<>(entry.getValue());values.sort(order);
            values.add(0,"全部省/州");regionNames.put(entry.getKey(),Collections.unmodifiableList(values));
        }
        countries.replaceAll((code,values)->Collections.unmodifiableList(values));
        regions.replaceAll((code,values)->Collections.unmodifiableList(values));
    }
    private static String key(String country,String region){return country+"\u0000"+region;}
    public List<Station> stations(String country,String region) {
        if(country.isEmpty())return all;
        return region.isEmpty()?countries.getOrDefault(country,Collections.emptyList()):regions.getOrDefault(key(country,region),Collections.emptyList());
    }
    public List<String> regions(String country){return regionNames.getOrDefault(country,Collections.singletonList("全部省/州"));}
}
