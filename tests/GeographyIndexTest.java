package com.wanderadio.app;

import java.util.*;
import java.nio.file.*;
import org.json.JSONObject;

public final class GeographyIndexTest {
    public static void main(String[] args) throws Exception {
        Station a=new Station(new JSONObject().put("stationuuid","a").put("url","https://example.com/a").put("countrycode","CN").put("state","Liaoning"));
        Station b=new Station(new JSONObject().put("stationuuid","b").put("url","https://example.com/b").put("countrycode","CN").put("state","Shanghai"));
        Station c=new Station(new JSONObject().put("stationuuid","c").put("url","https://example.com/c").put("countrycode","US").put("state","Liaoning"));
        ArrayList<Station> source=new ArrayList<>(List.of(a,b,c));
        GeographyIndex index=new GeographyIndex(source);source.clear();
        if(index.stations("","").size()!=3)throw new AssertionError("Snapshot changed");
        if(index.stations("CN","").size()!=2 || index.stations("CN","Liaoning").get(0)!=a)throw new AssertionError("Wrong region");
        if(index.stations("ZZ","").size()!=0 || index.regions("ZZ").size()!=1)throw new AssertionError("Missing country");
        if(index.countryLabels.size()!=index.countryCodes.size()+1 || index.regions("CN").size()!=3)throw new AssertionError("Picker choices");
        try{index.stations("CN","").clear();throw new AssertionError("Mutable lookup");}catch(UnsupportedOperationException expected) { }
        if(args.length>0) {
            ArrayList<Station> all=new ArrayList<>();
            for(String line:Files.readAllLines(Path.of(args[0])))all.add(new Station(new JSONObject(line)));
            long start=System.nanoTime();GeographyIndex full=new GeographyIndex(all);
            long built=System.nanoTime();
            for(int i=0;i<10000;i++){full.regions("CN");full.stations("CN","Liaoning");}
            long end=System.nanoTime();
            long expected=all.stream().filter(s->s.countryCode.equals("CN")&&s.state.equals("Liaoning")).count();
            if(full.stations("CN","Liaoning").size()!=expected)throw new AssertionError("Full catalogue mismatch");
            System.out.println("Full snapshot: "+all.size()+"; buildMs="+(built-start)/1_000_000+"; 10000 cached lookupsMs="+(end-built)/1_000_000);
        }
        System.out.println("PASS: immutable snapshot, country/region lookup, empty choices, stable option mapping");
    }
}
