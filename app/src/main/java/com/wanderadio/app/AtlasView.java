package com.wanderadio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.util.*;
import java.io.*;
import org.json.*;
import java.util.function.Consumer;

/** Native Canvas projection. Dots represent directory entries, never fabricated locations. */
public final class AtlasView extends View {
    private static final String[] GENRES = {"爵士蓝调", "古典经典", "摇滚", "电子舞曲", "新闻谈话", "流行", "民谣世界", "综合"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path grid = new Path();
    private final float[] projected = new float[3];
    private final float[][] hits = new float[3000][2];
    private final float[][] nebulaPoints = new float[3000][2];
    private final Station[] hitStations = new Station[3000];
    private final float[][] stars = new float[100][3];
    private int hitCount;
    private Shader background;
    private final Shader glow = new RadialGradient(0, 0, 1,
        new int[] {0xfffff7db, 0xffffc88d, 0x998f603c, 0x003b352c},
        new float[] {0, .18f, .48f, 1}, Shader.TileMode.CLAMP);
    private List<Station> stations = Collections.emptyList();
    private final LocationResolver locations;
    private final List<LocationResolver.Location> displayLocations = new ArrayList<>();
    public Consumer<Station> onSelect;
    private String selected = "", signalStatus = "未调谐";
    private boolean nebula;
    private float yaw = -.5f, pitch, zoom = 1.25f, anchor = .5f;
    private float downX, downY, lastX, lastY;
    private boolean moved;
    private ValueAnimator flight;
    private final ScaleGestureDetector scale;

    public AtlasView(Context context) {
        super(context);
        locations = new LocationResolver(readCountries(context));
        setContentDescription("电台星图。选择地区自动定位，选择频道自动放大。拖动旋转、双指缩放，轻点电台播放。");
        Random random = new Random(73);
        for (float[] star : stars) {
            star[0] = random.nextFloat(); star[1] = random.nextFloat(); star[2] = random.nextFloat() + .3f;
        }
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                stopFlight();
                zoom = Math.max(.7f, Math.min(10f, zoom * detector.getScaleFactor()));
                moved = true; invalidate(); return true;
            }
        });
    }

    public void setStations(List<Station> values) {
        stations = new ArrayList<>(values);
        displayLocations.clear();
        // Resolve only points that can be drawn; the complete list remains available to channel search.
        for (int i=0;i<Math.min(stations.size(),3000);i++)displayLocations.add(locations.resolve(stations.get(i)));
        Random random = new Random();
        for (int i = 0; i < Math.min(stations.size(), 3000); i++) {
            Station station = stations.get(i);
            random.setSeed(station.id.hashCode());
            double angle = genre(station.tags) * Math.PI * 2 / 8;
            nebulaPoints[i][0] = (float) Math.cos(angle) * .27f + (float) random.nextGaussian() * .063f;
            nebulaPoints[i][1] = (float) Math.sin(angle) * .29f + (float) random.nextGaussian() * .063f;
        }
        invalidate();
    }

    private static Map<String,double[]> readCountries(Context context) {
        Map<String,double[]> result = new HashMap<>();
        try (InputStream input = context.getAssets().open("country-centers.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer,0,count);
            JSONObject data = new JSONObject(output.toString("UTF-8"));
            Iterator<String> codes = data.keys();
            while (codes.hasNext()) {String code=codes.next();JSONArray point=data.getJSONArray(code);result.put(code,new double[]{point.getDouble(0),point.getDouble(1)});}
        } catch (IOException | JSONException error) {android.util.Log.w("WanderMap","country anchors unavailable",error);}
        return result;
    }

    public void setCatalogue(List<Station> values) {
        locations.setCatalogue(values);
        setStations(stations);
    }

    public String locationLabel(Station station) { return locations.resolve(station).label(); }

    public void setSelected(String id) { selected = id; invalidate(); }

    public void setSignalState(boolean playing, boolean buffering) {
        signalStatus = buffering ? "调谐中" : playing ? "已锁定" : "已暂停";
        invalidate();
    }

    public void setNebula(boolean value) { stopFlight(); nebula = value; invalidate(); }

    public boolean focusStation(Station station) {
        LocationResolver.Location location=locations.resolve(station);
        if (!location.available()) return false;
        selected=station.id;nebula=false;
        float magnification=location.precision==LocationResolver.Precision.STATION?7.5f:
            location.precision==LocationResolver.Precision.REGION?5f:3.2f;
        fly(location.latitude,location.longitude,magnification,.32f);
        return true;
    }

    public boolean focusRegion(List<Station> values) {
        List<double[]> points=new ArrayList<>();
        for(Station station:values) {
            LocationResolver.Location location=locations.resolve(station);
            if(location.available())points.add(new double[]{location.latitude,location.longitude});
        }
        double[] target=GlobeCamera.regionTargetPoints(points);
        if(target==null)return false;
        nebula=false;
        fly(target[0],target[1],Math.max(2.1f,(float)target[2]),.5f);
        return true;
    }

    public void overview() { nebula=false;fly(0,25,1.25f,.5f); }

    private void fly(double latitude, double longitude, float targetZoom, float targetAnchor) {
        stopFlight();
        float startYaw = yaw, startPitch = pitch, startZoom = zoom, startAnchor = anchor;
        float targetYaw = (float) GlobeCamera.shortestTurn(yaw, -Math.toRadians(longitude));
        float targetPitch = (float) Math.toRadians(latitude);
        flight = ValueAnimator.ofFloat(0, 1);
        flight.setDuration(800);
        flight.setInterpolator(new DecelerateInterpolator(1.5f));
        flight.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            yaw = startYaw + (targetYaw - startYaw) * fraction;
            pitch = startPitch + (targetPitch - startPitch) * fraction;
            zoom = startZoom + (targetZoom - startZoom) * fraction;
            anchor = startAnchor + (targetAnchor - startAnchor) * fraction;
            invalidate();
        });
        flight.start();
    }

    private void stopFlight() { if (flight != null) { flight.cancel(); flight = null; } }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width > 0 && height > 0) background = new RadialGradient(width * .4f, height * .5f,
            Math.max(width, height) * .7f, new int[] {0xff18352c, 0xff0a1918}, null, Shader.TileMode.CLAMP);
    }

    private void project(double latitude, double longitude, float radius) {
        GlobeCamera.project(latitude, longitude, yaw, pitch, projected);
        projected[0] = getWidth() * anchor + projected[0] * radius;
        projected[1] = getHeight() * .47f - projected[1] * radius;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        float radius = Math.min(width * .48f, height * .46f) * zoom;
        paint.setShader(background); canvas.drawRect(0, 0, width, height, paint); paint.setShader(null);
        // Fixed background stars are decoration, visually distinct from station dots.
        paint.setColor(0xff365048);
        for (float[] star : stars) canvas.drawCircle(star[0] * width, star[1] * height, dp(star[2]), paint);
        if (!nebula) {
            paint.setColor(0xff244039); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(.6f));
            canvas.drawCircle(width * anchor, height * .47f, radius, paint);
            for (int latitude = -60; latitude <= 60; latitude += 30) gridLine(canvas, latitude, true, radius);
            for (int longitude = -180; longitude < 180; longitude += 30) gridLine(canvas, longitude, false, radius);
            paint.setStyle(Paint.Style.FILL);
        }
        hitCount = 0;
        Station locked = null;
        float selectedX = 0, selectedY = 0;
        for (int i = 0; i < Math.min(stations.size(), 3000); i++) {
            Station station = stations.get(i);
            float x, y, depth;
            if (nebula) {
                x = width / 2 + nebulaPoints[i][0] * width;
                y = height / 2 + nebulaPoints[i][1] * height; depth = 1;
            } else {
                LocationResolver.Location location=displayLocations.get(i);
                if (!location.available()) continue;
                project(location.latitude,location.longitude,radius);
                x = projected[0]; y = projected[1]; depth = projected[2];
                if (depth < 0) continue;
            }
            if (x < -dp(35) || x > width + dp(35) || y < -dp(35) || y > height + dp(35)) continue;
            boolean active = station.id.equals(selected);
            paint.setColor(active ? 0xffefd6ae : 0xff83a496);
            paint.setAlpha(active ? 255 : (int) (45 + 95 * depth));
            canvas.drawCircle(x, y, dp(active ? 5f : 2.2f), paint);
            if (active) { locked = station; selectedX = x; selectedY = y; }
            hits[hitCount][0] = x; hits[hitCount][1] = y; hitStations[hitCount] = station; hitCount++;
        }
        paint.setAlpha(255);
        if (locked != null) drawSignal(canvas, locked, selectedX, selectedY);
        paint.setColor(0xff88a094); paint.setTypeface(Typeface.DEFAULT); paint.setTextSize(sp(10));
        canvas.drawText(nebula ? "流派星云 · 轻点频道调谐" : "拖动探索  /  双指缩放", dp(12), height - dp(10), paint);
        if (stations.isEmpty()) {
            paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("暂无可显示的频道", width / 2, height / 2, paint); paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawSignal(Canvas canvas, Station station, float x, float y) {
        canvas.save(); canvas.translate(x, y); canvas.scale(dp(65), dp(65));
        paint.setShader(glow); canvas.drawCircle(0, 0, 1, paint); paint.setShader(null); canvas.restore();
        paint.setStrokeWidth(dp(.7f));
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            float inner = dp(25), outer = dp(i % 4 == 0 ? 34 : 30);
            paint.setColor(i % 4 == 0 ? 0xffbfb8a2 : 0xff666e60);
            canvas.drawLine(x + (float) Math.cos(angle) * inner, y + (float) Math.sin(angle) * inner,
                x + (float) Math.cos(angle) * outer, y + (float) Math.sin(angle) * outer, paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(.9f)); paint.setColor(0xffe2d9bd);
        canvas.drawCircle(x, y, dp(22), paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xfffff8e7); canvas.drawCircle(x, y, dp(10), paint);
        paint.setColor(0xffef7950); canvas.drawCircle(x + dp(18), y - dp(21), dp(4), paint);
        float textX = x + dp(44), textY = y - dp(5);
        float available = getWidth() - textX - dp(10);
        if (available < dp(90)) { textX = Math.max(dp(12), x - dp(145)); textY = y + dp(44); available = getWidth() - textX - dp(10); }
        paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(sp(9)); paint.setColor(0xffed875b);
        canvas.drawText(signalStatus, textX, textY, paint);
        float gap = paint.measureText(signalStatus) + dp(10);
        paint.setColor(0xffa4b5a9);
        String meta = GENRES[genre(station.tags)] + " · " + (station.bitrate > 0 ? station.bitrate + " KBPS" : "LIVE");
        drawClipped(canvas, meta, textX + gap, textY, available - gap);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); paint.setTextSize(sp(15)); paint.setColor(0xfff4f0e3);
        drawClipped(canvas,station.name,textX,textY+dp(20),available);
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(sp(9));paint.setColor(0xff9eac9c);
        drawClipped(canvas,locations.resolve(station).label(),textX,textY+dp(36),available);
    }

    private void drawClipped(Canvas canvas, String text, float x, float y, float available) {
        if (available <= 0) return;
        canvas.save(); canvas.clipRect(x, y - sp(18), x + available, y + dp(5)); canvas.drawText(text, x, y, paint); canvas.restore();
    }

    private void gridLine(Canvas canvas, int fixed, boolean latitude, float radius) {
        grid.reset(); boolean started = false;
        for (int n = -180; n <= 180; n += 3) {
            project(latitude ? fixed : n / 2.0, latitude ? n : fixed, radius);
            if (projected[2] >= 0) {
                if (!started) grid.moveTo(projected[0], projected[1]); else grid.lineTo(projected[0], projected[1]); started = true;
            } else started = false;
        }
        canvas.drawPath(grid, paint);
    }

    public static int genre(String tags) {
        String value = tags.toLowerCase(Locale.ROOT);
        if (value.contains("jazz") || value.contains("blues")) return 0;
        if (value.contains("classic")) return 1;
        if (value.contains("rock")) return 2;
        if (value.contains("electro") || value.contains("dance") || value.contains("house")) return 3;
        if (value.contains("news") || value.contains("talk")) return 4;
        if (value.contains("pop")) return 5;
        if (value.contains("folk") || value.contains("world")) return 6;
        return 7;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scale.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopFlight(); downX = lastX = event.getX(); downY = lastY = event.getY(); moved = false;
                getParent().requestDisallowInterceptTouchEvent(true); return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > dp(8)) moved = true;
                if (!scale.isInProgress() && !nebula && moved) {
                    yaw += (event.getX() - lastX) / (150 * Math.max(1, zoom));
                    pitch = Math.max(-(float) Math.PI / 2, Math.min((float) Math.PI / 2,
                        pitch + (event.getY() - lastY) / (150 * Math.max(1, zoom))));
                    invalidate();
                }
                lastX = event.getX(); lastY = event.getY(); return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    double distance = dp(28); Station closest = null;
                    for (int i = 0; i < hitCount; i++) {
                        double candidate = Math.hypot(hits[i][0] - event.getX(), hits[i][1] - event.getY());
                        if (candidate < distance) { distance = candidate; closest = hitStations[i]; }
                    }
                    if (closest != null && onSelect != null) onSelect.accept(closest);
                    performClick();
                }
                getParent().requestDisallowInterceptTouchEvent(false); return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { stopFlight(); super.onDetachedFromWindow(); }
}
