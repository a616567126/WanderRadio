package com.wanderadio.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

final class Library {
    final LinkedHashMap<String,Station> favorites=new LinkedHashMap<>();
    final ArrayList<Station> recent=new ArrayList<>();
    private final SharedPreferences prefs;
    Library(Context c) {
        prefs=c.getSharedPreferences("library",0);
        for (Station s:read("favorites")) favorites.put(s.id,s);
        recent.addAll(read("recent"));
    }
    private List<Station> read(String key) {
        ArrayList<Station> result=new ArrayList<>();
        try { JSONArray a=new JSONArray(prefs.getString(key,"[]")); for(int i=0;i<a.length();i++) { Station s=new Station(a.getJSONObject(i)); if(s.usable()) result.add(s); } } catch(Exception ignored) { }
        return result;
    }
    private void save(String key,Collection<Station> stations) { JSONArray a=new JSONArray(); for(Station s:stations)a.put(s.json()); prefs.edit().putString(key,a.toString()).apply(); }
    void toggle(Station s) { if(favorites.remove(s.id)==null)favorites.put(s.id,s); save("favorites",favorites.values()); }
    void visit(Station s) { recent.removeIf(v -> v.id.equals(s.id)); recent.add(0,s); if(recent.size()>50)recent.remove(recent.size()-1); save("recent",recent); }
}
