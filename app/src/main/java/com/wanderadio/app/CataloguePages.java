package com.wanderadio.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;

/** Accumulates every API page; raw row counts, not deduplicated counts, advance the offset. */
public final class CataloguePages {
    public static final int PAGE_SIZE=5000;
    private final LinkedHashMap<String,Station> stations=new LinkedHashMap<>();
    public static String path(int offset) {
        return "/json/stations?hidebroken=false&order=name&reverse=false&limit="+PAGE_SIZE+"&offset="+offset;
    }
    public int add(String body) throws Exception {
        JSONArray rows=new JSONArray(body);
        int before=stations.size();
        for(int i=0;i<rows.length();i++) {
            Station station=new Station(rows.getJSONObject(i));
            if(station.usable())stations.put(station.id,station);
        }
        if(rows.length()>=PAGE_SIZE && stations.size()==before)throw new IOException("目录分页未推进，请重试");
        return rows.length();
    }
    public int size(){return stations.size();}
    public List<Station> values(){return new ArrayList<>(stations.values());}
}
