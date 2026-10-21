package com.wanderadio.app;

import org.json.JSONArray;
import org.json.JSONObject;

public final class CataloguePagesTest {
    public static void main(String[] args) throws Exception {
        CataloguePages pages=new CataloguePages();
        JSONObject first=new JSONObject().put("stationuuid","one").put("url","https://example.com/one");
        JSONObject second=new JSONObject().put("stationuuid","two").put("url","https://example.com/two");
        int raw=pages.add(new JSONArray().put(first).put(first).put(second).toString());
        if(raw!=3 || pages.values().size()!=2)throw new AssertionError("Raw offset must include duplicates");
        if(pages.add("[]")!=0 || pages.values().size()!=2)throw new AssertionError("Empty final page");
        String path=CataloguePages.path(15000);
        if(!path.contains("offset=15000") || !path.contains("hidebroken=false"))throw new AssertionError(path);
        JSONArray repeated=new JSONArray();for(int i=0;i<CataloguePages.PAGE_SIZE;i++)repeated.put(first);
        try{pages.add(repeated.toString());throw new AssertionError("Repeated page accepted");}
        catch(java.io.IOException expected) { }
        System.out.println("PASS: raw offsets, UUID deduplication, empty final page, full catalogue query, repeated-page guard");
    }
}
