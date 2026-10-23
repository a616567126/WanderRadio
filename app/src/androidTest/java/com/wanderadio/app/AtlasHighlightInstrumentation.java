package com.wanderadio.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import java.util.ArrayList;
import org.json.JSONObject;

/** Device-side rendering regression for selections beyond the background dot limit. */
public final class AtlasHighlightInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            runOnMainSync(()->{
                try {verify();}catch(Exception error){throw new RuntimeException(error);}
            });
            result.putString("stream","PASS: selected station outside 3,000 dots, list refresh, empty list, nebula, missing coordinates\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);
        }
    }
    private Station station(String id,boolean located) throws Exception {
        JSONObject json=new JSONObject().put("stationuuid",id).put("name",id).put("url","https://example.com/live");
        if(located)json.put("geo_lat",0).put("geo_long",Math.toDegrees(.5));
        return new Station(json);
    }
    private int warmPixels(AtlasView view) {
        Bitmap bitmap=Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));int warm=0;
        for(int y=200;y<750;y++)for(int x=100;x<620;x++) {
            int color=bitmap.getPixel(x,y),red=(color>>16)&255,green=(color>>8)&255;
            if(red>120 && red>green*1.15)warm++;
        }
        bitmap.recycle();return warm;
    }
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void verify() throws Exception {
        AtlasView view=new AtlasView(getTargetContext());view.layout(0,0,720,1000);
        ArrayList<Station> values=new ArrayList<>();
        for(int i=0;i<3100;i++)values.add(station("station-"+i,true));
        view.setCatalogue(values);view.setStations(values);
        require(warmPixels(view)==0,"Unexpected initial highlight");
        view.setSelected(values.get(3099));
        require(warmPixels(view)>100,"Selection beyond index 3000 was not rendered");
        java.lang.reflect.Field count=AtlasView.class.getDeclaredField("hitCount");count.setAccessible(true);
        require(count.getInt(view)==3001,"Selected dot lacks reserved hit target");
        view.setStations(values);require(warmPixels(view)>100,"List refresh removed highlight");
        view.setStations(new ArrayList<>());require(warmPixels(view)>100,"Empty filter removed active overlay");
        view.setNebula(true);require(warmPixels(view)>100,"Nebula omitted active selection");
        view.setNebula(false);view.setSelected(station("unknown",false));
        require(warmPixels(view)==0,"Missing coordinates retained previous highlight");
    }
}
