package com.quanglewangle.peter.rocloc.location;

/**
 * Great-circle distance, bearing, compass point, and Maidenhead locator.
 * No terrain data — LoS results are geometric (flat-earth horizon only).
 */
public class HereCalculator {

    private static final double R_KM = 6371.0;

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** True bearing from point 1 to point 2, degrees 0-360. */
    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1r = Math.toRadians(lat1);
        double lat2r = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(lat2r);
        double x = Math.cos(lat1r) * Math.sin(lat2r)
                - Math.sin(lat1r) * Math.cos(lat2r) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** 16-point compass name for a bearing. */
    public static String compassPoint(double bearing) {
        String[] pts = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                        "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return pts[(int) ((bearing + 11.25) / 22.5) % 16];
    }

    /**
     * 6-character Maidenhead locator (e.g. IO91WM) from lat/lon.
     * Uses upper-case subsquare letters.
     */
    public static String maidenhead(double lat, double lon) {
        lon += 180.0;
        lat += 90.0;

        int fieldLon  = (int) (lon / 20);
        int fieldLat  = (int) (lat / 10);
        int squareLon = (int) ((lon % 20) / 2);
        int squareLat = (int) (lat % 10);
        int subLon    = (int) ((lon % 2)  / (5.0 / 60));
        int subLat    = (int) ((lat % 1)  / (2.5 / 60));

        subLon = Math.min(subLon, 23);
        subLat = Math.min(subLat, 23);

        return "" + (char)('A' + fieldLon)
                  + (char)('A' + fieldLat)
                  + (char)('0' + squareLon)
                  + (char)('0' + squareLat)
                  + (char)('A' + subLon)
                  + (char)('A' + subLat);
    }

    /** Approximate radio-horizon distance (km) for a given antenna height (m). */
    public static double radioHorizonKm(double antHeightM) {
        return 3.57 * Math.sqrt(antHeightM);
    }
}
