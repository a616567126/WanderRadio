package com.wanderadio.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class StationRepository {
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File cache;
    private final File fullCache;
    private volatile boolean loading, closed;
    private volatile List<String> servers;
    public interface Result { void accept(List<Station> stations, String message); }
    public StationRepository(Context context) { cache = new File(context.getFilesDir(), "stations.json"); fullCache=new File(context.getFilesDir(),"stations-full.jsonl"); }
    public static List<Station> parse(String body) throws Exception {
        JSONArray array = new JSONArray(body); Map<String, Station> unique = new LinkedHashMap<>();
        for (int i=0; i<array.length(); i++) { Station s = new Station(array.getJSONObject(i)); if (s.usable()) unique.put(s.id,s); }
        if (unique.isEmpty()) throw new IOException("目录返回空数据");
        return new ArrayList<>(unique.values());
    }
    private List<String> servers() {
        if (servers != null) return servers;
        List<String> names=Collections.emptyList();
        CompletableFuture<List<String>> discovery=CompletableFuture.supplyAsync(() -> {
            LinkedHashSet<String> hosts=new LinkedHashSet<>();
            try {
                for(InetAddress ip:InetAddress.getAllByName("all.api.radio-browser.info")) {
                    String host=ip.getCanonicalHostName();
                    if(host.endsWith(".api.radio-browser.info"))hosts.add("https://"+host);
                }
            } catch(Exception ignored) { }
            return new ArrayList<>(hosts);
        });
        // Reverse DNS can stall on mobile networks. Fall back without blocking catalogue loading.
        try {names=discovery.get(3,TimeUnit.SECONDS);}catch(Exception ignored){discovery.cancel(true);}
        ArrayList<String> found = new ArrayList<>(names); Collections.shuffle(found);
        found.add("https://de1.api.radio-browser.info"); found.add("https://de2.api.radio-browser.info");
        servers = new ArrayList<>(new LinkedHashSet<>(found)); return servers;
    }
    private String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent", "WanderRadio/0.1 (Android)");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP "+c.getResponseCode());
            try (InputStream in=c.getInputStream()) { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1)out.write(buffer,0,n); return out.toString(StandardCharsets.UTF_8.name()); }
        } finally { c.disconnect(); }
    }
    public synchronized void load(Result result) {
        if(loading || closed)return;
        loading=true;
        io.execute(() -> {
            boolean completeCache=false;
            try {
                List<Station> local=new ArrayList<>();
                if(fullCache.isFile()) {
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(fullCache),StandardCharsets.UTF_8))) {
                        String line;
                        while((line=reader.readLine())!=null) {
                            Station station=new Station(new org.json.JSONObject(line));
                            if(station.usable())local.add(station);
                        }
                    }
                    completeCache=!local.isEmpty();
                } else if(cache.isFile())local=parse(new String(java.nio.file.Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));
                if(!local.isEmpty()) {
                    List<Station> snapshot=local;
                    String label=completeCache?"已载入全量缓存 · "+local.size()+" 台，正在更新…":"旧版部分缓存 · "+local.size()+" 台，正在补全…";
                    main.post(() -> {if(!closed)result.accept(snapshot,label);});
                }
            } catch(Exception ignored) {completeCache=false;}
            Exception failure=null;
            try {
                for(String server:servers()) {
                    try {
                        CataloguePages pages=new CataloguePages();
                        int offset=0,page=0;
                        while(!closed && !Thread.currentThread().isInterrupted()) {
                            android.util.Log.i("WanderCatalogue","fetch host="+server+" offset="+offset);
                            String body=get(server+CataloguePages.path(offset));
                            int rawCount=pages.add(body);
                            offset+=rawCount;page++;
                            android.util.Log.i("WanderCatalogue","page="+page+" raw="+rawCount+" unique="+pages.size());
                            if(rawCount<CataloguePages.PAGE_SIZE) {
                                List<Station> all=pages.values();
                                if(all.isEmpty())throw new IOException("目录返回空数据");
                                saveFull(all);
                                main.post(() -> {if(!closed)result.accept(all,"全球全量目录 · "+all.size()+" 台（含失效标记）");});
                                return;
                            }
                            String progress="全球目录加载中 · 已读取 "+offset+" 条（尚未完成）";
                            List<Station> snapshot=!completeCache && page==1?pages.values():Collections.emptyList();
                            main.post(() -> {if(!closed)result.accept(snapshot,progress);});
                        }
                        return;
                    } catch(Exception error) {android.util.Log.w("WanderCatalogue","server failed: "+server,error);failure=error;}
                }
                String reason="全量目录更新失败，保留已有数据 · 点击重试"+(failure==null?"":" · "+failure.getMessage());
                main.post(() -> {if(!closed)result.accept(Collections.emptyList(),reason);});
            } finally {loading=false;}
        });
    }
    private void saveFull(List<Station> stations) throws IOException {
        File temporary=new File(fullCache.getParentFile(),"stations-full.tmp");
        try(BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temporary),StandardCharsets.UTF_8))) {
            for(Station station:stations){writer.write(station.json().toString());writer.newLine();}
        }
        java.nio.file.Files.move(temporary.toPath(),fullCache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    public void click(Station station) { io.execute(() -> {
        for (String server : servers()) try { get(server+"/json/url/"+URLEncoder.encode(station.id,"UTF-8")); return; } catch (Exception ignored) { }
    }); }
    public void close() { closed=true;io.shutdownNow();main.removeCallbacksAndMessages(null); }
}
