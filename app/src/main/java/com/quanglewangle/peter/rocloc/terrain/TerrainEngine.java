package com.quanglewangle.peter.rocloc.terrain;

import java.util.HashMap;
import java.util.Map;

/**
 * On-device terrain line-of-sight using OS Terrain 50 tiles downloaded from the server.
 * Java port of qrzlook/terrain50/terrain50.go.
 */
public class TerrainEngine {

    public static class LoSResult {
        public final boolean clear;
        public final double obsLat, obsLon;
        public LoSResult(boolean clear, double obsLat, double obsLon) {
            this.clear = clear; this.obsLat = obsLat; this.obsLon = obsLon;
        }
    }

    private static final String GRID_LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ";

    // 500 km square letter → [e-index, n-index]
    private static final Map<Character, int[]> FIVE500 = new HashMap<>();
    // [e-index][n-index] → letter
    private static final char[][] FIVE500_REV = new char[2][3];

    static {
        FIVE500.put('S', new int[]{0, 0}); FIVE500_REV[0][0] = 'S';
        FIVE500.put('T', new int[]{1, 0}); FIVE500_REV[1][0] = 'T';
        FIVE500.put('N', new int[]{0, 1}); FIVE500_REV[0][1] = 'N';
        FIVE500.put('O', new int[]{1, 1}); FIVE500_REV[1][1] = 'O';
        FIVE500.put('H', new int[]{0, 2}); FIVE500_REV[0][2] = 'H';
    }

    private final TerrainStore store;

    public TerrainEngine(TerrainStore store) {
        this.store = store;
    }

    public boolean hasTiles() {
        return store.hasTiles();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public double elevationAt(double lat, double lon) {
        double[] bng = wgs84ToBNG(lat, lon);
        return elevationAtBNG(bng[0], bng[1]);
    }

    public LoSResult losCheck(double lat1, double lon1, double h1,
                              double lat2, double lon2, double h2) {
        final double SAMPLE_M = 100.0;
        final double R_EFF = 4.0 / 3.0 * 6371000.0;
        double D = haversineM(lat1, lon1, lat2, lon2);
        if (D < 1) return new LoSResult(true, 0, 0);
        int n = Math.max(2, (int) (D / SAMPLE_M) + 1);
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            double lat = lat1 + t * (lat2 - lat1);
            double lon = lon1 + t * (lon2 - lon1);
            double lineH = h1 + t * (h2 - h1);
            double bulge = D * D * t * (1 - t) / (2 * R_EFF);
            double elev = elevationAt(lat, lon);
            if (Double.isNaN(elev)) continue; // no tile — benefit of doubt
            if (elev > lineH - bulge) return new LoSResult(false, lat, lon);
        }
        return new LoSResult(true, 0, 0);
    }

    // ── Tile geometry ────────────────────────────────────────────────────────

    public static String bngToTileCode(double e, double n) {
        int ei = (int) e, ni = (int) n;
        char l1 = FIVE500_REV[ei / 500000][ni / 500000];
        int e100 = (ei % 500000) / 100000;
        int n100 = (ni % 500000) / 100000;
        char l2 = GRID_LETTERS.charAt((4 - n100) * 5 + e100);
        int ed = (ei % 100000) / 10000;
        int nd = (ni % 100000) / 10000;
        return String.format("%c%c%d%d", l1, l2, ed, nd);
    }

    public static double[] tileOrigin(String code) {
        code = code.toUpperCase();
        int[] en500 = FIVE500.get(code.charAt(0));
        int idx = GRID_LETTERS.indexOf(code.charAt(1));
        int col = idx % 5, row = idx / 5;
        int ed = code.charAt(2) - '0';
        int nd = code.charAt(3) - '0';
        double e = en500[0] * 500000.0 + col * 100000.0 + ed * 10000.0;
        double n = en500[1] * 500000.0 + (4 - row) * 100000.0 + nd * 10000.0;
        return new double[]{e, n};
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private double elevationAtBNG(double e, double n) {
        if (e < 0 || e >= 700000 || n < 0 || n >= 1300000) return Double.NaN;
        String code = bngToTileCode(e, n);
        float[] grid = store.getTile(code);
        if (grid == null) return Double.NaN;
        double[] origin = tileOrigin(code);
        int col = (int) ((e - origin[0]) / 50.0);
        int row = (int) ((n - origin[1]) / 50.0);
        if (col < 0 || col >= 200 || row < 0 || row >= 200) return Double.NaN;
        int rowIdx = 199 - row; // stored north-first
        float val = grid[rowIdx * 200 + col];
        return val == -9999 ? Double.NaN : val;
    }

    public static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double r = Math.PI / 180;
        double dLat = (lat2 - lat1) * r;
        double dLon = (lon2 - lon1) * r;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1 * r) * Math.cos(lat2 * r)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.asin(Math.sqrt(a));
    }

    // Port of terrain50.go wgs84ToBNG — OS Helmert approximation (±3-5 m, fine for 50 m cells)
    public static double[] wgs84ToBNG(double latDeg, double lonDeg) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);

        // WGS84 ellipsoid
        final double aW = 6378137.000;
        final double fW = 1.0 / 298.257223563;
        double bW = aW * (1 - fW);
        double e2W = 1 - (bW / aW) * (bW / aW);

        double sinLat = Math.sin(lat), cosLat = Math.cos(lat);
        double sinLon = Math.sin(lon), cosLon = Math.cos(lon);
        double nuW = aW / Math.sqrt(1 - e2W * sinLat * sinLat);

        double x = nuW * cosLat * cosLon;
        double y = nuW * cosLat * sinLon;
        double z = nuW * (1 - e2W) * sinLat;

        // Helmert: WGS84 → OSGB36
        final double tx = -446.448, ty = +125.157, tz = -542.060;
        final double s = 20.4894e-6;
        final double toRad = Math.PI / 648000.0;
        double rx = -0.1502 * toRad, ry = -0.2470 * toRad, rz = -0.8421 * toRad;

        double x2 = tx + (1 + s) * x - rz * y + ry * z;
        double y2 = ty + rz * x + (1 + s) * y - rx * z;
        double z2 = tz - ry * x + rx * y + (1 + s) * z;

        // Cartesian → OSGB36 lat/lon (Airy 1830)
        final double aA = 6377563.396, bA = 6356256.909;
        double e2A = 1 - (bA / aA) * (bA / aA);
        double p = Math.sqrt(x2 * x2 + y2 * y2);
        double lat2 = Math.atan2(z2, p * (1 - e2A));
        for (int i = 0; i < 10; i++) {
            double nuA = aA / Math.sqrt(1 - e2A * Math.sin(lat2) * Math.sin(lat2));
            lat2 = Math.atan2(z2 + e2A * nuA * Math.sin(lat2), p);
        }
        double lon2 = Math.atan2(y2, x2);

        // OSGB36 lat/lon → BNG Transverse Mercator
        final double n0 = -100000.0, e0 = 400000.0, f0 = 0.9996012717;
        final double phi0 = Math.toRadians(49.0), lam0 = Math.toRadians(-2.0);
        double nA = (aA - bA) / (aA + bA);

        double sinPhi = Math.sin(lat2), cosPhi = Math.cos(lat2), tanPhi = Math.tan(lat2);
        double tan2 = tanPhi * tanPhi, tan4 = tan2 * tan2;
        double nu = aA * f0 / Math.sqrt(1 - e2A * sinPhi * sinPhi);
        double rho = aA * f0 * (1 - e2A) / Math.pow(1 - e2A * sinPhi * sinPhi, 1.5);
        double eta2 = nu / rho - 1;

        double m = meridionalArc(bA, f0, phi0, lat2, nA);
        double I_ = m + n0;
        double II_ = nu / 2 * sinPhi * cosPhi;
        double III_ = nu / 24 * sinPhi * Math.pow(cosPhi, 3) * (5 - tan2 + 9 * eta2);
        double IIIA = nu / 720 * sinPhi * Math.pow(cosPhi, 5) * (61 - 58 * tan2 + tan4);
        double IV = nu * cosPhi;
        double V = nu / 6 * Math.pow(cosPhi, 3) * (nu / rho - tan2);
        double VI = nu / 120 * Math.pow(cosPhi, 5) * (5 - 18 * tan2 + tan4 + 14 * eta2 - 58 * tan2 * eta2);
        double dL = lon2 - lam0;

        double northing = I_ + II_ * dL * dL + III_ * Math.pow(dL, 4) + IIIA * Math.pow(dL, 6);
        double easting = e0 + IV * dL + V * Math.pow(dL, 3) + VI * Math.pow(dL, 5);
        return new double[]{easting, northing};
    }

    private static double meridionalArc(double b, double f0, double phi0, double phi, double n) {
        double n2 = n * n, n3 = n2 * n;
        double dp = phi - phi0, sp = phi + phi0;
        return b * f0 * ((1 + n + 5.0 / 4 * n2 + 5.0 / 4 * n3) * dp
                - (3 * n + 3 * n2 + 21.0 / 8 * n3) * Math.sin(dp) * Math.cos(sp)
                + (15.0 / 8 * n2 + 15.0 / 8 * n3) * Math.sin(2 * dp) * Math.cos(2 * sp)
                - (35.0 / 24 * n3) * Math.sin(3 * dp) * Math.cos(3 * sp));
    }
}
