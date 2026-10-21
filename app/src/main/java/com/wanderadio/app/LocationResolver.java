package com.wanderadio.app;

import java.util.*;

/** Resolves display locations without presenting approximate anchors as transmitter coordinates. */
public final class LocationResolver {
    public enum Precision { STATION, REGION, COUNTRY, UNKNOWN }
    public static final class Location {
        public final double latitude, longitude;
        public final Precision precision;
        Location(double latitude, double longitude, Precision precision) {
            this.latitude = latitude; this.longitude = longitude; this.precision = precision;
        }
        public boolean available() { return precision != Precision.UNKNOWN; }
        public String label() {
            if (precision == Precision.STATION) return "目录坐标";
            if (precision == Precision.REGION) return "地区范围 · 近似位置";
            if (precision == Precision.COUNTRY) return "国家范围 · 近似位置";
            return "位置未标注";
        }
    }
    private final Map<String, double[]> countries;
    private final Map<String, double[]> regionSums = new HashMap<>();
    private final Map<String, Location> cache = new HashMap<>();
    public LocationResolver(Map<String, double[]> countries) { this.countries = new HashMap<>(countries); }

    public void setCatalogue(List<Station> stations) {
        regionSums.clear(); cache.clear();
        for (Station station : stations) {
            if (!station.located() || station.state.trim().isEmpty() || station.countryCode.isEmpty()) continue;
            String key = regionKey(station);
            double[] sum = regionSums.computeIfAbsent(key, ignored -> new double[3]);
            double latitude = Math.toRadians(station.latitude), longitude = Math.toRadians(station.longitude);
            sum[0] += Math.cos(latitude) * Math.cos(longitude);
            sum[1] += Math.cos(latitude) * Math.sin(longitude);
            sum[2] += Math.sin(latitude);
        }
    }
    private String regionKey(Station station) {
        return station.countryCode + "/" + station.state.trim().toLowerCase(Locale.ROOT);
    }
    public Location resolve(Station station) {
        if (station.located()) return new Location(station.latitude, station.longitude, Precision.STATION);
        Location saved = cache.get(station.id);
        if (saved != null) return saved;
        Location result;
        double[] region = station.state.trim().isEmpty() ? null : regionSums.get(regionKey(station));
        if (region != null && Math.sqrt(region[0]*region[0]+region[1]*region[1]+region[2]*region[2]) > .00001) {
            result = new Location(Math.toDegrees(Math.atan2(region[2], Math.hypot(region[0], region[1]))),
                Math.toDegrees(Math.atan2(region[1], region[0])), Precision.REGION);
        } else {
            double[] country = countries.get(station.countryCode);
            result = country == null ? new Location(Double.NaN, Double.NaN, Precision.UNKNOWN)
                : new Location(country[0], country[1], Precision.COUNTRY);
        }
        cache.put(station.id, result);
        return result;
    }
}
