package com.wanderadio.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff0a1918,INK=0xfff2eee2,MUTED=0xff95aca2,GREEN=0xffb7e5c7;
    private final ArrayList<Station> stations=new ArrayList<>();
    private final ArrayList<Station> filtered=new ArrayList<>();
    private StationRepository repository;
    private Library library;
    private RadioService radio;
    private AtlasView atlas;
    private TextView title,location,quality,status,count,loadStatus;
    private Button play,save,timer,countryButton,regionButton;
    private ListView list;
    private Runnable channelRefresh;
    private boolean bound, binding;
    private final java.util.concurrent.ExecutorService geographyWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private GeographyIndex geography=new GeographyIndex(Collections.emptyList());
    private int catalogueGeneration;
    private int tab=0,genre=-1,minBitrate=0;
    private String query="",countryCode="",region="",focusedStation="";
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {radio=((RadioService.LocalBinder)binder).service();radio.observer=MainActivity.this::refreshPlayer;bound=true;refreshPlayer();}
        @Override public void onServiceDisconnected(ComponentName name) {radio=null;bound=false;}
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);library=new Library(this);repository=new StationRepository(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout page=column();page.setBackgroundColor(BG);page.setPadding(dp(20),dp(8),dp(20),0);
        // Respect Android 15 edge-to-edge and display cutouts without guessing status-bar heights.
        page.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(dp(20)+bars.left,dp(8)+bars.top,dp(20)+bars.right,bars.bottom);
            }
            return insets;
        });
        if(Build.VERSION.SDK_INT<30)page.setOnApplyWindowInsetsListener(null);
        setContentView(page);
        LinearLayout header=row();TextView brand=text("漫听 / WANDER RADIO",13,GREEN);brand.setLetterSpacing(.12f);header.addView(brand,new LinearLayout.LayoutParams(0,dp(32),1));
        header.addView(button("更多",v->more()),new LinearLayout.LayoutParams(dp(62),dp(38)));page.addView(header);
        TextView headline=text("把下一站，交给声音。",18,INK);headline.setTypeface(Typeface.create("serif",Typeface.NORMAL));headline.setPadding(0,dp(4),0,dp(4));headline.setVisibility(View.GONE);page.addView(headline);
        loadStatus=text("正在连接全球电台目录…",11,MUTED);page.addView(loadStatus);
        LinearLayout modes=row();modes.setPadding(0,dp(10),0,dp(2));
        modes.addView(button("地球",v->atlas.overview()),new LinearLayout.LayoutParams(0,dp(32),1));
        modes.addView(button("流派星云",v->atlas.setNebula(true)),new LinearLayout.LayoutParams(0,dp(32),1));
        modes.addView(button("筛选",v->filters()),new LinearLayout.LayoutParams(0,dp(32),1));page.addView(modes);
        LinearLayout tuning=column();
        tuning.setPadding(dp(12),dp(10),dp(12),dp(10));
        tuning.setBackground(panel(0xff10251f,18,0xff2b473c));
        TextView tuningLabel=text("频道调谐",10,MUTED);
        tuningLabel.setLetterSpacing(.15f);tuning.addView(tuningLabel);
        EditText search=new EditText(this);
        tuning.addView(searchField(search,"搜索电台、国家或地区…"),new LinearLayout.LayoutParams(-1,dp(46)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int c){query=s.toString();filter();}public void afterTextChanged(Editable e){}});
        LinearLayout geography=row();geography.setPadding(0,dp(8),0,0);
        countryButton=button("🌐 全部国家/地区  ⌄",v->countries());
        regionButton=button("全部省/州  ⌄",v->regions());
        LinearLayout countryField=selectorField("国家 / 地区",countryButton);
        LinearLayout regionField=selectorField("省 / 州",regionButton);
        LinearLayout.LayoutParams countryLayout=new LinearLayout.LayoutParams(0,dp(52),1.25f);
        countryLayout.setMarginEnd(dp(8));geography.addView(countryField,countryLayout);
        geography.addView(regionField,new LinearLayout.LayoutParams(0,dp(52),1));
        tuning.addView(geography);page.addView(tuning);
        atlas=new AtlasView(this);atlas.onSelect=this::tune;
        page.addView(atlas,new LinearLayout.LayoutParams(-1,0,6f));
        LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(10));card.setBackground(shape(0xff142c26,18));
        status=text("● 选择一个电台，开始漫游",11,GREEN);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(status);
        title=text("听见世界的另一面",21,INK);title.setMaxLines(1);title.setTypeface(Typeface.create("serif",Typeface.NORMAL));title.setPadding(0,dp(6),0,dp(4));card.addView(title);
        location=text("随机出发，也可以搜索目的地",12,MUTED);location.setMaxLines(1);card.addView(location);
        quality=text("直播电台 · 无需注册",10,MUTED);quality.setPadding(0,dp(3),0,dp(4));quality.setVisibility(View.GONE);card.addView(quality);
        LinearLayout controls=row();
        save=button("收藏",v->{if(radio!=null&&radio.station!=null){library.toggle(radio.station);refreshPlayer();filter();}});
        play=button("▶ 播放",v->{if(radio==null)return;if(radio.station==null)random();else if(radio.playing||radio.buffering)radio.pause();else radio.play();});
        play.setTextColor(BG);play.setBackground(shape(GREEN,24));
        controls.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(play,new LinearLayout.LayoutParams(0,dp(44),1.3f));
        controls.addView(button("下一站 ›",v->random()),new LinearLayout.LayoutParams(0,dp(44),1));card.addView(controls);
        LinearLayout extras=row();timer=button("睡眠定时",v->sleep());extras.addView(timer,new LinearLayout.LayoutParams(0,dp(34),1));extras.addView(button("分享电台",v->share()),new LinearLayout.LayoutParams(0,dp(34),1));extras.setVisibility(View.GONE);card.addView(extras);page.addView(card);
        LinearLayout tabs=row();tabs.addView(button("探索",v->{tab=0;filter();showChannels();}),new LinearLayout.LayoutParams(0,dp(34),1));tabs.addView(button("收藏",v->{tab=1;filter();showChannels();}),new LinearLayout.LayoutParams(0,dp(34),1));tabs.addView(button("最近",v->{tab=2;filter();showChannels();}),new LinearLayout.LayoutParams(0,dp(34),1));page.addView(tabs);
        count=text("选择频道 · 正在载入…",14,GREEN);count.setGravity(Gravity.CENTER);
        count.setPadding(dp(12),0,dp(12),0);count.setBackground(panel(0xff1b3528,14,0xff496b51));
        count.setContentDescription("展开大尺寸频道列表");count.setOnClickListener(v->showChannels());
        LinearLayout.LayoutParams channelLauncher=new LinearLayout.LayoutParams(-1,dp(50));channelLauncher.topMargin=dp(6);channelLauncher.bottomMargin=dp(8);page.addView(count,channelLauncher);
        list=new ListView(this);list.setDividerHeight(0);list.setAdapter(adapter);list.setOnItemClickListener((parent,view,index,id)->tune(filtered.get(index)));
        TextView empty=text("这里还没有电台。可调整筛选或重试目录。",12,MUTED);empty.setPadding(0,dp(10),0,0);list.setEmptyView(empty);
        // Channel selection lives in a roomy sheet instead of a one-row strip competing with the map.
        list.setOnItemLongClickListener((p,v,index,id)->{library.toggle(filtered.get(index));filter();refreshPlayer();return true;});
        binding=bindService(new Intent(this,RadioService.class),connection,BIND_AUTO_CREATE);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);
        load();
        page.setFocusableInTouchMode(true);page.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void load() {
        loadStatus.setText("正在更新目录…");repository.load((values,message)->{
            if(isFinishing()||isDestroyed())return;
            loadStatus.setText(message);
            loadStatus.setOnClickListener(v->load());
            // Page-progress callbacks do not change data and must not rebuild the map or filters.
            if(values.isEmpty())return;
            final int generation=++catalogueGeneration;
            ArrayList<Station> catalogue=new ArrayList<>(values);
            ArrayList<Station> extras=new ArrayList<>(library.favorites.values());extras.addAll(library.recent);
            geographyWorker.execute(()->{
                LinkedHashMap<String,Station> combined=new LinkedHashMap<>();
                for(Station station:catalogue)combined.put(station.id,station);
                for(Station station:extras)combined.putIfAbsent(station.id,station);
                GeographyIndex ready=new GeographyIndex(new ArrayList<>(combined.values()));
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||generation!=catalogueGeneration)return;
                    stations.clear();stations.addAll(catalogue);geography=ready;
                    atlas.setCatalogue(geography.all);filter();
                });
            });
        });
    }
    private final BaseAdapter adapter=new BaseAdapter() {
        public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent) {
            Station s=filtered.get(p);LinearLayout box=column();box.setPadding(dp(6),dp(10),dp(6),dp(10));
            TextView name=text((library.favorites.containsKey(s.id)?"♥  ":"")+s.name,14,INK);name.setSingleLine();box.addView(name);
            TextView subtitle=text(s.location()+"  ·  "+s.quality(),10,MUTED);subtitle.setSingleLine();box.addView(subtitle);return box;
        }
    };
    private void filter() {
        if(list==null)return;long started=SystemClock.uptimeMillis();filtered.clear();Collection<Station> source=tab==1?library.favorites.values():tab==2?library.recent:countryCode.isEmpty()?stations:geography.stations(countryCode,region);
        for(Station s:source)if(s.inRegion(countryCode,region)&&(query.trim().isEmpty()||s.matches(query))&&s.bitrate>=minBitrate&&(genre<0||AtlasView.genre(s.tags)==genre))filtered.add(s);
        adapter.notifyDataSetChanged();atlas.setStations(filtered);count.setText((tab==1?"我的收藏":tab==2?"最近收听":"选择频道")+" · "+filtered.size()+" 台    ⌃ 展开列表");
        if(channelRefresh!=null)channelRefresh.run();
        android.util.Log.d("WanderGeography","filter count="+filtered.size()+" elapsedMs="+(SystemClock.uptimeMillis()-started));
    }
    private void tune(Station s) {
        if(radio==null)return;
        // Start lifetime management, then select synchronously so stale UI cannot pause a queued tune.
        startForegroundService(new Intent(this,RadioService.class).setAction("keep"));
        focusedStation=s.id;
        library.visit(s);radio.tune(s);repository.click(s);filter();
        if(!atlas.focusStation(s))Toast.makeText(this,"位置未标注，仍可正常收听",Toast.LENGTH_SHORT).show();
    }
    private void random() {
        if(filtered.isEmpty()){Toast.makeText(this,"当前列表没有电台，请调整筛选或等待目录",Toast.LENGTH_SHORT).show();return;}
        ArrayList<Station> choices=new ArrayList<>(filtered);if(radio!=null&&radio.station!=null&&choices.size()>1)choices.removeIf(s->s.id.equals(radio.station.id));
        tune(choices.get(new Random().nextInt(choices.size())));
    }
    private void refreshPlayer() {
        if(radio==null)return;Station s=radio.station;
        status.setText((radio.playing?"● ":radio.buffering?"◌ ":"○ ")+radio.status);
        if(s!=null){title.setText(s.name);location.setText(s.location()+" · "+s.quality());quality.setText(s.quality());save.setText(library.favorites.containsKey(s.id)?"♥ 已收藏":"♡ 收藏");atlas.setSelected(s);
            atlas.setSignalState(radio.playing,radio.buffering);
            if(!s.id.equals(focusedStation)) {
                focusedStation=s.id;
                if(!atlas.focusStation(s))Toast.makeText(this,"位置未标注，仍可正常收听",Toast.LENGTH_SHORT).show();
            }
        }
        play.setText(radio.buffering?"连接中…":radio.playing?"Ⅱ 暂停":"▶ 播放");
        play.setEnabled(!radio.buffering);play.setAlpha(radio.buffering?.6f:1f);
        timer.setText(radio.sleepDeadline>0?"定时已开启":"睡眠定时");
    }
    private List<Station> geographicSource() { return geography.all; }
    private void countries() {
        long started=SystemClock.uptimeMillis();
        // Capture one immutable snapshot so a catalogue refresh cannot change option indices.
        GeographyIndex snapshot=geography;
        List<String> codes=snapshot.countryCodes,labels=snapshot.countryLabels;
        searchableChoices("选择国家/地区",labels,countryCode.isEmpty()?0:codes.indexOf(countryCode)+1,which->{
            countryCode=which==0?"":codes.get(which-1);region="";
            countryButton.setText(labels.get(which)+" ⌄");regionButton.setText("全部省/州 ⌄");filter();focusGeography();
        });
        android.util.Log.d("WanderGeography","country picker elapsedMs="+(SystemClock.uptimeMillis()-started));
    }
    private void regions() {
        if(countryCode.isEmpty()){Toast.makeText(this,"请先选择国家/地区，再筛选省或州",Toast.LENGTH_SHORT).show();countries();return;}
        long started=SystemClock.uptimeMillis();
        List<String> labels=geography.regions(countryCode);
        if(labels.size()==1){Toast.makeText(this,"目录尚未提供该国家/地区的省州信息",Toast.LENGTH_SHORT).show();return;}
        searchableChoices("选择省 / 州 / 地区",labels,region.isEmpty()?0:labels.indexOf(region),which->{region=which==0?"":labels.get(which);regionButton.setText(labels.get(which)+" ⌄");filter();focusGeography();});
        android.util.Log.d("WanderGeography","region picker elapsedMs="+(SystemClock.uptimeMillis()-started));
    }
    private void focusGeography() {
        if(countryCode.isEmpty()){atlas.overview();return;}
        List<Station> locations=geography.stations(countryCode,region);
        if(!atlas.focusRegion(locations))Toast.makeText(this,"该地区暂未提供有效坐标，仍可从列表收听",Toast.LENGTH_SHORT).show();
    }
    private void searchableChoices(String heading,List<String> labels,int current,java.util.function.IntConsumer selected) {
        Sheet sheet=new Sheet(heading,"选择一个目的地，让声音带你出发");
        Dialog dialog=sheet.dialog;LinearLayout content=sheet.body;
        EditText search=new EditText(this);content.addView(searchField(search,"搜索名称…"),new LinearLayout.LayoutParams(-1,dp(48)));
        ListView options=new ListView(this);options.setDividerHeight(0);
        LinearLayout.LayoutParams optionLayout=new LinearLayout.LayoutParams(-1,Math.min(dp(330),(int)(getResources().getDisplayMetrics().heightPixels*.38f)));
        optionLayout.topMargin=dp(10);content.addView(options,optionLayout);
        TextView empty=text("没有匹配的目的地",13,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(20),0,dp(20));content.addView(empty);options.setEmptyView(empty);
        ArrayList<Integer> indices=new ArrayList<>();for(int i=0;i<labels.size();i++)indices.add(i);
        BaseAdapter choices=new BaseAdapter(){
            public int getCount(){return indices.size();}public Object getItem(int position){return labels.get(indices.get(position));}public long getItemId(int position){return indices.get(position);}
            public View getView(int position,View old,ViewGroup parent){
                int index=indices.get(position);LinearLayout row=row();row.setPadding(dp(12),0,dp(12),0);
                row.setBackground(panel(index==current?0xff2b4635:0xff12281f,12,index==current?0xff779b77:0xff12281f));
                TextView label=text(labels.get(index),14,index==current?GREEN:INK);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
                row.addView(text(index==current?"✓":"",16,GREEN),new LinearLayout.LayoutParams(dp(24),dp(48)));return row;
            }
        };
        options.setAdapter(choices);
        options.setOnItemClickListener((parent,view,index,id)->{int chosen=indices.get(index);dialog.dismiss();selected.accept(chosen);});
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int start,int before,int n){
                indices.clear();for(int i=0;i<labels.size();i++)if(labels.get(i).toLowerCase(Locale.ROOT).contains(s.toString().trim().toLowerCase(Locale.ROOT)))indices.add(i);choices.notifyDataSetChanged();
            }
        });
        showSheet(dialog);
    }
    private void showSheet(Dialog dialog) {
        Window window=dialog.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams attributes=window.getAttributes();attributes.y=dp(16);attributes.dimAmount=.6f;window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ViewGroup host=window.findViewById(android.R.id.content);
        if(host!=null&&host.getChildCount()>0){View root=host.getChildAt(0);root.setFocusableInTouchMode(true);root.requestFocus();}
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(window.getDecorView().getWindowToken(),0);
    }
    private LinearLayout selectorField(String caption,Button value) {
        LinearLayout field=column();field.setPadding(dp(10),dp(6),dp(6),0);field.setBackground(panel(0xff192f25,12,0xff35503f));
        TextView label=text(caption,9,MUTED);label.setLetterSpacing(.08f);field.addView(label);
        value.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);value.setTextSize(12);value.setSingleLine();value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setPadding(0,0,dp(4),0);field.addView(value,new LinearLayout.LayoutParams(-1,0,1));field.setOnClickListener(v->value.performClick());
        return field;
    }
    private LinearLayout searchField(EditText input,String hint) {
        LinearLayout field=row();field.setPadding(dp(10),0,dp(4),0);field.setBackground(panel(0xff0b1c15,12,0xff36503f));
        ImageView icon=new ImageView(this);icon.setImageResource(android.R.drawable.ic_menu_search);icon.setImageTintList(android.content.res.ColorStateList.valueOf(MUTED));
        field.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
        input.setSingleLine();input.setTextColor(INK);input.setHintTextColor(MUTED);input.setTextSize(13);input.setHint(hint);input.setContentDescription(hint);
        input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(8),0,dp(4),0);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();return true;}return false;});
        field.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        Button clear=button("×",v->input.setText(""));clear.setTextSize(21);clear.setContentDescription("清空搜索");clear.setVisibility(View.INVISIBLE);
        field.addView(clear,new LinearLayout.LayoutParams(dp(32),-1));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int count){clear.setVisibility(s.length()>0?View.VISIBLE:View.INVISIBLE);}});
        return field;
    }
    private GradientDrawable panel(int color,int radius,int border) {
        GradientDrawable drawable=shape(color,radius);drawable.setStroke(dp(1),border);return drawable;
    }
    private final class Sheet {
        final Dialog dialog=new Dialog(MainActivity.this);
        final LinearLayout body=column();
        Sheet(String title,String subtitle) {
            LinearLayout shell=column();shell.setPadding(dp(18),dp(12),dp(18),dp(18));
            shell.setBackground(panel(0xff12281f,24,0xff395344));
            View handle=new View(MainActivity.this);handle.setBackground(shape(0xff496653,4));
            LinearLayout.LayoutParams handleLayout=new LinearLayout.LayoutParams(dp(36),dp(4));
            handleLayout.gravity=Gravity.CENTER_HORIZONTAL;handleLayout.bottomMargin=dp(10);shell.addView(handle,handleLayout);
            LinearLayout heading=row();TextView label=text(title,19,INK);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            heading.addView(label,new LinearLayout.LayoutParams(0,dp(42),1));
            Button close=button("×",v->dialog.dismiss());close.setTextSize(23);close.setContentDescription("关闭面板");
            close.setBackground(panel(0xff1c3528,18,0xff3a5744));heading.addView(close,new LinearLayout.LayoutParams(dp(36),dp(36)));shell.addView(heading);
            if(!subtitle.isEmpty()){TextView description=text(subtitle,11,MUTED);description.setPadding(0,dp(2),0,dp(14));shell.addView(description);}
            shell.addView(body);shell.setFocusableInTouchMode(true);shell.requestFocus();dialog.setContentView(shell);
        }
        void show(){showSheet(dialog);}
    }
    private Button pill(String label,boolean selected,View.OnClickListener listener) {
        Button value=button(label,listener);value.setTextColor(selected?BG:GREEN);
        value.setBackground(panel(selected?GREEN:0xff1b3528,13,selected?GREEN:0xff3c5945));
        value.setSingleLine();value.setEllipsize(android.text.TextUtils.TruncateAt.END);return value;
    }
    private void filters() {
        Sheet sheet=new Sheet("调谐偏好","流派和音质一起选择，应用后调整当前星图");
        int[] draft={genre,minBitrate};
        String[] labels={"全部流派","爵士蓝调","古典经典","摇滚","电子舞曲","新闻谈话","流行","民谣世界","综合其他"};
        int[] bitrates={0,128,192,320};String[] qualityLabels={"不限","128+","192+","320+"};
        ArrayList<Button> genreButtons=new ArrayList<>(),qualityButtons=new ArrayList<>();
        TextView genreLabel=text("音乐与内容",11,MUTED);genreLabel.setPadding(0,0,0,dp(8));sheet.body.addView(genreLabel);
        Runnable repaint=()->{
            for(int i=0;i<genreButtons.size();i++){
                boolean selected=draft[0]==i-1;Button button=genreButtons.get(i);button.setTextColor(selected?BG:GREEN);
                button.setBackground(panel(selected?GREEN:0xff1b3528,13,selected?GREEN:0xff3c5945));
            }
            for(int i=0;i<qualityButtons.size();i++){
                boolean selected=draft[1]==bitrates[i];Button button=qualityButtons.get(i);button.setTextColor(selected?BG:GREEN);
                button.setBackground(panel(selected?GREEN:0xff1b3528,13,selected?GREEN:0xff3c5945));
            }
        };
        for(int rowIndex=0;rowIndex<3;rowIndex++) {
            LinearLayout options=row();options.setGravity(Gravity.TOP);
            for(int column=0;column<3;column++) {
                final int index=rowIndex*3+column;
                Button option=pill(labels[index],draft[0]==index-1,v->{draft[0]=index-1;repaint.run();});
                genreButtons.add(option);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(44),1);
                layout.setMarginEnd(column==2?0:dp(8));layout.bottomMargin=dp(8);options.addView(option,layout);
            }
            sheet.body.addView(options);
        }
        TextView qualityLabel=text("最低码率 · KBPS",11,MUTED);qualityLabel.setPadding(0,dp(6),0,dp(8));sheet.body.addView(qualityLabel);
        LinearLayout qualities=row();
        for(int i=0;i<bitrates.length;i++) {
            final int index=i;Button option=pill(qualityLabels[i],draft[1]==bitrates[i],v->{draft[1]=bitrates[index];repaint.run();});
            option.setContentDescription(i==0?"不限码率，包含未知码率":"最低 "+bitrates[i]+" kbps");qualityButtons.add(option);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(42),1);layout.setMarginEnd(i==3?0:dp(8));qualities.addView(option,layout);
        }
        sheet.body.addView(qualities);
        TextView note=text("码率以电台自报为准；选择“不限”包含未知码率频道。",10,MUTED);note.setPadding(0,dp(10),0,dp(14));sheet.body.addView(note);
        LinearLayout actions=row();
        Button reset=pill("重置",false,v->{draft[0]=-1;draft[1]=0;repaint.run();});
        LinearLayout.LayoutParams resetLayout=new LinearLayout.LayoutParams(0,dp(46),1);resetLayout.setMarginEnd(dp(10));actions.addView(reset,resetLayout);
        Button apply=pill("应用筛选",true,v->{
            genre=draft[0];minBitrate=draft[1];filter();
            if(filtered.isEmpty())Toast.makeText(this,"没有符合条件的频道，请调整筛选",Toast.LENGTH_SHORT).show();
            else atlas.focusRegion(filtered);
            sheet.dialog.dismiss();
        });actions.addView(apply,new LinearLayout.LayoutParams(0,dp(46),2));sheet.body.addView(actions);sheet.show();
    }
    private void optionSheet(String title,String subtitle,String[] choices,int checked,java.util.function.IntConsumer action) {
        Sheet sheet=new Sheet(title,subtitle);
        for(int i=0;i<choices.length;i++) {
            final int index=i;Button option=pill((i==checked?"✓  ":"")+choices[i],i==checked,v->{action.accept(index);sheet.dialog.dismiss();});
            option.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);option.setPadding(dp(16),0,dp(16),0);option.setTextSize(14);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(48));layout.bottomMargin=dp(8);sheet.body.addView(option,layout);
        }
        sheet.show();
    }
    private void showChannels() {
        Sheet sheet=new Sheet(tab==1?"我的收藏":tab==2?"最近收听":"选择一个频道","当前地区与流派筛选已生效 · 长按收藏频道");
        EditText search=new EditText(this);sheet.body.addView(searchField(search,"搜索频道、国家、地区…"),new LinearLayout.LayoutParams(-1,dp(48)));
        TextView total=text("",11,MUTED);total.setPadding(0,dp(10),0,dp(8));sheet.body.addView(total);
        ArrayList<Station> visible=new ArrayList<>(filtered);
        ListView channels=new ListView(this);
        channels.setDivider(new android.graphics.drawable.ColorDrawable(0xff12281f));channels.setDividerHeight(dp(8));
        BaseAdapter rows=new BaseAdapter(){
            public int getCount(){return visible.size();}public Object getItem(int position){return visible.get(position);}public long getItemId(int position){return position;}
            public View getView(int position,View old,ViewGroup parent){
                Station station=visible.get(position);boolean current=radio!=null&&radio.station!=null&&radio.station.id.equals(station.id);
                LinearLayout item=row();item.setPadding(dp(14),dp(10),dp(12),dp(10));item.setMinimumHeight(dp(72));
                item.setBackground(panel(current?0xff294836:0xff1a3326,14,current?0xff9bcba2:0xff34503d));
                LinearLayout information=column();
                TextView name=text((library.favorites.containsKey(station.id)?"♥  ":"")+station.name,16,INK);
                name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);information.addView(name);
                TextView details=text(station.location()+" · "+station.quality(),11,MUTED);details.setSingleLine();details.setEllipsize(android.text.TextUtils.TruncateAt.END);details.setPadding(0,dp(4),0,0);information.addView(details);
                item.addView(information,new LinearLayout.LayoutParams(0,-2,1));
                TextView icon=text(current&&radio.playing?"♪":"▶",18,current?BG:GREEN);icon.setGravity(Gravity.CENTER);icon.setBackground(shape(current?GREEN:0xff2d4b36,22));
                LinearLayout.LayoutParams iconLayout=new LinearLayout.LayoutParams(dp(42),dp(42));iconLayout.setMarginStart(dp(10));item.addView(icon,iconLayout);
                return item;
            }
        };
        Runnable refresh=()->{
            visible.clear();String text=search.getText().toString();for(Station station:filtered)if(station.matches(text))visible.add(station);
            total.setText("找到 "+visible.size()+" 个频道");rows.notifyDataSetChanged();
        };
        channels.setAdapter(rows);
        TextView empty=text("没有匹配的频道，试试清空搜索或调整筛选。",13,MUTED);empty.setPadding(dp(8),dp(24),dp(8),dp(24));empty.setGravity(Gravity.CENTER);sheet.body.addView(empty);channels.setEmptyView(empty);
        int height=Math.min(dp(440),(int)(getResources().getDisplayMetrics().heightPixels*.48f));
        sheet.body.addView(channels,new LinearLayout.LayoutParams(-1,height));
        channels.setOnItemClickListener((parent,view,position,id)->{Station station=visible.get(position);tune(station);sheet.dialog.dismiss();});
        channels.setOnItemLongClickListener((parent,view,position,id)->{library.toggle(visible.get(position));filter();refreshPlayer();refresh.run();return true;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable value){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}});
        channelRefresh=refresh;
        sheet.dialog.setOnDismissListener(dialog->{if(channelRefresh==refresh)channelRefresh=null;});
        refresh.run();sheet.show();
    }
    private void more() {
        optionSheet("收听工具","让每一次漫游更从容",new String[]{"◷  睡眠定时","↗  分享当前电台","ⓘ  关于漫听"},-1,index->{if(index==0)sleep();else if(index==1)share();else about();});
    }
    private void sleep() {
        if(radio==null)return;
        String subtitle=radio.sleepDeadline>0?"剩余约 "+Math.max(1,(radio.sleepDeadline-SystemClock.elapsedRealtime()+59999)/60000)+" 分钟":"选择多久后停止播放";
        optionSheet("睡眠定时",subtitle,new String[]{"15 分钟","30 分钟","60 分钟","关闭定时"},radio.sleepDeadline==0?3:-1,index->radio.setSleep(new int[]{15,30,60,0}[index]));
    }
    private void share() {
        if(radio==null||radio.station==null){Toast.makeText(this,"先选择一个电台再分享",Toast.LENGTH_SHORT).show();return;}
        Station station=radio.station;
        String message=station.name+" · "+station.location()+"\nhttps://www.radio-browser.info/history/"+station.id;
        optionSheet("分享这一站",station.name,new String[]{"复制电台链接","分享至其他应用"},-1,index->{
            if(index==0){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("电台",message));Toast.makeText(this,"已复制电台链接",Toast.LENGTH_SHORT).show();}
            else startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,message),"分享电台"));
        });
    }
    private void about() {
        Sheet sheet=new Sheet("漫听 · 0.1.6","把下一站，交给声音。");
        ScrollView scroll=new ScrollView(this);
        TextView description=text("独立原生电台客户端，灵感来自 RoamDial，与原站无隶属关系。\n\n电台目录来自 Radio Browser，分页加载全球全量目录，包含失效标记的电台；音频直连电台服务器，部分线路可能失效或限制地区访问。\n\n位置分为目录坐标、地区范围、国家范围。近似位置仅用于地图探索，不代表发射台地址；国家参考数据来自 mledoze/countries，按 ODbL 提供。\n\n收藏和最近收听保存在本机，无账号、无分析埋点，不申请定位或麦克风权限。电台直播本身可能包含广告，部分线路使用 HTTP。",13,INK);
        description.setLineSpacing(dp(4),1);scroll.addView(description);
        sheet.body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(260)));
        Button refresh=pill("更新电台目录",true,v->{load();sheet.dialog.dismiss();});
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(46));layout.topMargin=dp(14);sheet.body.addView(refresh,layout);sheet.show();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String value,View.OnClickListener click){Button b=new Button(this);b.setText(value);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(GREEN);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);b.setBackground(shape(Color.TRANSPARENT,12));b.setOnClickListener(click);return b;}
    @Override protected void onDestroy(){channelRefresh=null;catalogueGeneration++;geographyWorker.shutdownNow();if(radio!=null)radio.observer=null;if(binding)unbindService(connection);repository.close();super.onDestroy();}
}
