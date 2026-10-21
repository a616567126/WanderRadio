package com.wanderadio.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.util.List;


import android.util.Log;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class RadioService extends Service {
    public final class LocalBinder extends Binder { RadioService service() { return RadioService.this; } }
    public interface Observer { void changed(); }
    private final LocalBinder binder=new LocalBinder();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private List<String> routes;
    private int routeIndex;
    private boolean resetting;
    private MediaSession session;
    private long sessionGeneration;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean prepared, wanted, foreground, focusHeld;
    private Runnable retry;
    private int attempt;
    private Runnable timeout;
    public Station station;
    public String status="选择一个电台，开始漫游";
    public boolean playing, buffering;
    public long sleepDeadline;
    public Observer observer;
    private final Runnable sleep=() -> { sleepDeadline=0; pause("睡眠定时已结束"); status="睡眠定时已结束"; publish(); };
    private final BroadcastReceiver noisy=new BroadcastReceiver() { public void onReceive(Context c,Intent i) { if(wanted && prepared)pause("耳机已断开，已暂停"); } };

    @Override public void onCreate() {
        super.onCreate();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change -> {
            Log.i("WanderPlayback","audio focus="+change+" wanted="+wanted+" prepared="+prepared);
            if(change==AudioManager.AUDIOFOCUS_GAIN) {
                focusHeld=true;
                if(player!=null)player.setVolume(1);
                if(wanted) { if(prepared)resumePrepared();else if(retry==null)connect(routes.get(routeIndex)); }
            } else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if(player!=null)player.setVolume(.2f);
            } else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                focusHeld=false;clearTimeout();
                if(player!=null && prepared && playing)player.pause();
                playing=false;buffering=false;status="被其他音频暂时打断，等待恢复";publish();
            } else if(change==AudioManager.AUDIOFOCUS_LOSS) {
                focusHeld=false;pause("其他应用占用音频，已暂停");
            }
        },handler).build();
        NotificationChannel channel=new NotificationChannel("radio","电台播放",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent("WanderRadio/0.1.4 (Android)")
            .setConnectTimeoutMs(10000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(true);
        player=new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
            .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(5000,30000,1000,2000).build())
            .build();
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).build());
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if(resetting || !wanted || state!=player.getPlaybackState())return;
                Log.i("WanderPlayback","state="+state+" route="+routeIndex);
                if(state==Player.STATE_READY) {
                    prepared=true;clearTimeout();
                    if(focusHeld)player.play();
                    updatePlayback();
                } else if(state==Player.STATE_BUFFERING) {
                    buffering=focusHeld;playing=false;status="正在缓冲直播…";publish();
                } else if(state==Player.STATE_ENDED)retryOrFail("直播连接已结束");
            }
            @Override public void onIsPlayingChanged(boolean value) {
                if(!resetting && wanted && value==player.isPlaying())updatePlayback();
            }
            @Override public void onPlayerError(PlaybackException error) {
                if(resetting || !wanted || error!=player.getPlayerError())return;
                Log.e("WanderPlayback","stream error "+error.getErrorCodeName(),error);
                String reason="播放失败（"+error.getErrorCodeName()+"）";
                for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
                    if(cause instanceof HttpDataSource.InvalidResponseCodeException) {
                        reason="电台服务器返回 HTTP "+((HttpDataSource.InvalidResponseCodeException)cause).responseCode;
                        break;
                    }
                    if(cause instanceof java.net.SocketTimeoutException)reason="直播服务器响应超时";
                    else if(cause instanceof java.net.UnknownHostException)reason="无法解析电台服务器地址";
                    else if(cause instanceof javax.net.ssl.SSLException)reason="电台安全连接失败";
                }
                retryOrFail(reason);
            }
        });
        createMediaSession();
        registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }
    private void createMediaSession() {
        final long generation=++sessionGeneration;
        if(session!=null){session.setActive(false);session.release();}
        session=new MediaSession(this,"WanderRadio");
        session.setCallback(new MediaSession.Callback() {
            private boolean current(String command) {
                if(generation!=sessionGeneration){Log.i("WanderPlayback","ignore stale session "+command);return false;}
                String caller="unknown";
                if(Build.VERSION.SDK_INT>=28)caller=session.getCurrentControllerInfo().getPackageName();
                Log.i("WanderPlayback","media command="+command+" from="+caller+" generation="+generation);
                return true;
            }
            @Override public void onPlay(){if(current("play"))play();}
            @Override public void onPause(){if(current("pause")&&wanted)pause("系统媒体控制已暂停");}
            @Override public void onStop(){if(current("stop")&&wanted)pause("系统媒体控制已停止");}
        },handler);
        session.setActive(true);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null) {
            String action=intent.getAction();
            if("pause".equals(action))pause("通知栏已暂停");
            else if("play".equals(action))play();
            else if("keep".equals(action)) { if(!wanted)stopSelf(id); }
            else if("tune".equals(action))try { tune(new Station(new JSONObject(intent.getStringExtra("station")))); } catch(Exception e) { fail("电台信息无效"); }
        }
        return START_NOT_STICKY;
    }
    public void tune(Station value) {
        boolean restarting=!wanted;
        cancelRetry();releasePlayer();station=value;attempt=0;routes=StreamRoutes.forStation(value);routeIndex=0;
        if(restarting)createMediaSession();
        wanted=true;
        Log.i("WanderPlayback","tune id="+value.id);
        // Android 15 requires an eligible foreground service before requesting audio focus.
        status="正在连接直播…";buffering=true;publish();ensureForeground();
        focusHeld=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!focusHeld) { fail("无法获取音频焦点，请稍后播放"); return; }
        connect(routes.get(routeIndex));
    }
    private void updatePlayback() {
        playing=wanted && focusHeld && player.isPlaying();
        buffering=wanted && focusHeld && player.getPlaybackState()==Player.STATE_BUFFERING;
        if(playing)status="正在直播";
        else if(buffering)status="正在缓冲直播…";
        else if(wanted && focusHeld)status="正在准备直播…";
        Log.i("WanderPlayback","playing="+playing+" buffering="+buffering);
        publish();
    }
    private void connect(String url) {
        releasePlayer();buffering=true;playing=false;status="正在连接直播…";publish();
        Log.i("WanderPlayback","connect route="+routeIndex+" host="+Uri.parse(url).getHost()+" scheme="+Uri.parse(url).getScheme());
        MediaItem.Builder item=new MediaItem.Builder().setUri(url);
        if(station.hls || url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8"))item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());
        player.setPlayWhenReady(wanted && focusHeld);
        timeout=() -> { if(wanted && !prepared)retryOrFail("直播服务器连接超时"); };
        handler.postDelayed(timeout,30000);
        player.prepare();
    }
    private void retryOrFail(String message) {
        Log.w("WanderPlayback",message+" attempt="+attempt+" wanted="+wanted);
        clearTimeout();
        if(!wanted || retry!=null)return;
        if(routeIndex+1<routes.size())routeIndex++;
        else if(attempt>=2) { fail(message+"，请换台或点击播放重试");return; }
        int number=++attempt;
        releasePlayer();buffering=true;playing=false;
        status=message+"，正在尝试线路（"+(routeIndex+1)+"/"+routes.size()+"）…";publish();
        retry=()->{retry=null;if(wanted && focusHeld)connect(routes.get(routeIndex));};
        handler.postDelayed(retry,Math.min(4000,1000L << Math.min(2,number-1)));
    }
    private void cancelRetry() { if(retry!=null)handler.removeCallbacks(retry);retry=null; }
    private void fail(String message) {
        Log.w("WanderPlayback","failure: "+message);
        cancelRetry();releasePlayer();wanted=false;focusHeld=false;status=message;audio.abandonAudioFocusRequest(focus);leaveForeground();publish();
    }
    private void resumePrepared() {
        if(player==null || !prepared || !wanted || !focusHeld)return;
        try { player.play();ensureForeground();updatePlayback(); }
        catch(Exception e) { fail("播放中断，请重试"); }
    }
    public void play() {
        if(station==null)return;
        if(wanted&&(playing||buffering))return;
        if(!wanted)createMediaSession();
        wanted=true;buffering=true;status="正在连接直播…";publish();ensureForeground();
        focusHeld=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!focusHeld) { fail("无法获取音频焦点，请稍后播放"); return; }
        if(prepared)resumePrepared(); else if(retry==null) { attempt=0;routeIndex=0;connect(routes.get(routeIndex)); }
    }
    public void pause() { pause("已暂停"); }
    private void pause(String reason) {
        Log.i("WanderPlayback","pause reason="+reason+" wanted="+wanted+" prepared="+prepared);
        wanted=false;focusHeld=false;cancelRetry();clearTimeout();
        if(player!=null&&prepared) {
            try{player.pause();}catch(IllegalStateException ignored){releasePlayer();}
        }else releasePlayer();
        playing=false;buffering=false;status=reason;
        audio.abandonAudioFocusRequest(focus);publish();
        // Keep a paused media notification instead of removing it and triggering SystemUI dismissal/stop.
        if(foreground){stopForeground(STOP_FOREGROUND_DETACH);foreground=false;}
        getSystemService(NotificationManager.class).notify(1,notification());
        stopSelf();
    }
    public void setSleep(int minutes) {
        handler.removeCallbacks(sleep);sleepDeadline=minutes>0?SystemClock.elapsedRealtime()+minutes*60000L:0;
        if(minutes>0)handler.postDelayed(sleep,minutes*60000L);publish();
    }
    private void clearTimeout() { if(timeout!=null)handler.removeCallbacks(timeout);timeout=null; }
    private void releasePlayer() {
        clearTimeout();prepared=false;playing=false;buffering=false;
        // ExoPlayer owns asynchronous network loading; stop and release it on its application looper.
        if(player!=null) {
            resetting=true;
            player.stop();player.clearMediaItems();
            resetting=false;
        }
    }
    private void ensureForeground() { startForeground(1,notification());foreground=true; }
    private void leaveForeground() { if(foreground) { stopForeground(STOP_FOREGROUND_REMOVE);foreground=false; } stopSelf(); }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Intent command=new Intent(this,RadioService.class).setAction(playing||buffering?"pause":"play");
        int flags=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent toggle=playing||buffering?PendingIntent.getService(this,1,command,flags):PendingIntent.getForegroundService(this,1,command,flags);
        return new Notification.Builder(this,"radio").setSmallIcon(com.wanderadio.app.R.drawable.ic_radio)
            .setContentTitle(station==null?"漫听":station.name).setContentText(status).setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(playing||buffering)
            .addAction(new Notification.Action.Builder(null,playing||buffering?"暂停":"播放",toggle).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0)).build();
    }
    private void publish() {
        if(session!=null) {
            int state=buffering?PlaybackState.STATE_BUFFERING:playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED;
            session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP).setState(state,PlaybackState.PLAYBACK_POSITION_UNKNOWN,playing?1:0).build());
            if(station!=null)session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,station.name).putString(MediaMetadata.METADATA_KEY_ARTIST,station.location()).build());
            if(foreground)getSystemService(NotificationManager.class).notify(1,notification());
        }
        if(observer!=null)observer.changed();
    }
    @Override public void onDestroy() {
        observer=null;sessionGeneration++;handler.removeCallbacksAndMessages(null);releasePlayer();player.release();audio.abandonAudioFocusRequest(focus);session.setActive(false);session.release();getSystemService(NotificationManager.class).cancel(1);unregisterReceiver(noisy);super.onDestroy();
    }
}
