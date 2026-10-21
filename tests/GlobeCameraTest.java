package com.wanderadio.app;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;

public final class GlobeCameraTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static Station station(double latitude, double longitude) throws Exception {
        return new Station(new JSONObject().put("stationuuid", latitude + "/" + longitude)
            .put("url", "https://example.org/live.mp3").put("geo_lat", latitude).put("geo_long", longitude));
    }
    public static void main(String[] args) throws Exception {
        float[] point = new float[3];
        GlobeCamera.project(37.5, 127, -Math.toRadians(127), Math.toRadians(37.5), point);
        check(Math.abs(point[0]) < .00001 && Math.abs(point[1]) < .00001 && point[2] > .9999,
            "A selected station must rotate to the camera center at its real latitude and longitude");
        GlobeCamera.project(-37.5, -53, -Math.toRadians(127), Math.toRadians(37.5), point);
        check(point[2] < -.9999, "The opposite side of the globe must remain hidden");
        double turn = GlobeCamera.shortestTurn(Math.toRadians(179), Math.toRadians(-179));
        check(Math.abs(Math.toDegrees(turn) - 181) < .0001, "Camera must take the short route across the date line");
        double[] region = GlobeCamera.regionTarget(Arrays.asList(station(10, 179), station(10, -179)));
        check(Math.abs(Math.abs(region[1]) - 180) < .01, "A date-line region must center near 180 degrees, not zero");
        check(GlobeCamera.regionTarget(Collections.emptyList()) == null, "Empty regions must not fabricate coordinates");
        Station missing = new Station(new JSONObject().put("stationuuid", "missing").put("url", "https://example.org/live.mp3"));
        check(GlobeCamera.regionTarget(Collections.singletonList(missing)) == null, "A region without coordinates must not be mapped");
        region = GlobeCamera.regionTarget(Arrays.asList(station(37, 127), station(38, 128)));
        check(Math.abs(region[0] - 37.5) < .1 && Math.abs(region[1] - 127.5) < .1 && region[2] <= 3.6 && region[2] > 1,
            "A compact region must focus on its station cluster and use bounded magnification");
        System.out.println("PASS: " + checks + " globe camera checks");
    }
}
