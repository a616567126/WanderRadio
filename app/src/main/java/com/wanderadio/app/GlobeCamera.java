package com.wanderadio.app;

import java.util.List;
import java.util.ArrayList;

/** Geographic camera calculations kept independent of Android rendering. */
public final class GlobeCamera {
    private GlobeCamera() { }

    public static double shortestTurn(double from, double to) {
        return from + Math.atan2(Math.sin(to - from), Math.cos(to - from));
    }

    public static void project(double latitude, double longitude, double yaw, double pitch, float[] result) {
        double lat = Math.toRadians(latitude);
        double lon = Math.toRadians(longitude) + yaw;
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.cos(lon);
        result[0] = (float) (Math.cos(lat) * Math.sin(lon));
        result[1] = (float) (y * Math.cos(pitch) - z * Math.sin(pitch));
        result[2] = (float) (y * Math.sin(pitch) + z * Math.cos(pitch));
    }

    /** Returns latitude, longitude and framing zoom, or null if the directory has no valid coordinates. */
    public static double[] regionTarget(List<Station> stations) {
        List<double[]> points = new ArrayList<>();
        for (Station station : stations) if (station.located()) points.add(new double[] {station.latitude, station.longitude});
        return regionTargetPoints(points);
    }

    public static double[] regionTargetPoints(List<double[]> points) {
        double x = 0, y = 0, z = 0;
        int count = 0;
        for (double[] point : points) {
            double lat = Math.toRadians(point[0]);
            double lon = Math.toRadians(point[1]);
            x += Math.cos(lat) * Math.cos(lon);
            y += Math.cos(lat) * Math.sin(lon);
            z += Math.sin(lat);
            count++;
        }
        if (count == 0) return null;
        // A spherical mean handles clusters crossing the international date line.
        double longitude = Math.atan2(y, x);
        double latitude = Math.atan2(z, Math.hypot(x, y));
        double widest = 0;
        for (double[] point : points) {
            double lat = Math.toRadians(point[0]);
            double lon = Math.toRadians(point[1]);
            double dot = Math.sin(latitude) * Math.sin(lat)
                + Math.cos(latitude) * Math.cos(lat) * Math.cos(lon - longitude);
            widest = Math.max(widest, Math.acos(Math.max(-1, Math.min(1, dot))));
        }
        double zoom = Math.max(1.15, Math.min(3.6, .72 / Math.max(.2, Math.sin(Math.min(Math.PI / 2, widest)))));
        return new double[] {Math.toDegrees(latitude), Math.toDegrees(longitude), zoom};
    }
}
