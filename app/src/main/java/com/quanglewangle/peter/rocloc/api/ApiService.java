package com.quanglewangle.peter.rocloc.api;

import android.util.Log;

import com.quanglewangle.peter.rocloc.data.OperatorEntity;
import com.quanglewangle.peter.rocloc.data.Site;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * REST lookup via the qrzlook server on fimblefowl.co.uk.
 *
 * Endpoint:  GET /qrz/lookup/{CALLSIGN}
 * (nginx proxies /qrz/ → :8091/, which runs the qrzlook Go server)
 *
 * Success response fields (all strings in the JSON):
 *   callsign, name, fname, lname, city, state, country, grid, lat, lon, altm
 *
 * Error response:
 *   { "error": "callsign not found" }   HTTP 404
 */
public class ApiService {

    private static final String BASE_URL = "https://fimblefowl.co.uk/qrz/lookup/";
    private static final String TAG = "ApiService";

    public interface Callback {
        void onSuccess(OperatorEntity entity);
        void onError(String error);
    }

    public static class LoSResult {
        public final boolean clear;
        public final double myElev;
        public final double targetElev;
        public final double distanceKm;
        public final double obsLat;
        public final double obsLon;
        public LoSResult(boolean clear, double myElev, double targetElev,
                         double distanceKm, double obsLat, double obsLon) {
            this.clear = clear; this.myElev = myElev; this.targetElev = targetElev;
            this.distanceKm = distanceKm; this.obsLat = obsLat; this.obsLon = obsLon;
        }
    }

    public interface LoSCallback {
        void onResult(LoSResult result);
        void onError(String error);
    }

    public static class SitesLoSEntry {
        public final boolean clear;
        public final double obsLat;
        public final double obsLon;
        public SitesLoSEntry(boolean clear, double obsLat, double obsLon) {
            this.clear = clear; this.obsLat = obsLat; this.obsLon = obsLon;
        }
    }

    public interface SitesCallback {
        void onResult(List<Site> sites);
        void onError(String error);
    }

    public interface SitesLoSCallback {
        void onResult(Map<String, SitesLoSEntry> result);
        void onError(String error);
    }

    private final OkHttpClient client = new OkHttpClient();

    public void lookup(String callsign, Callback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + callsign.toUpperCase())
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body().string();
                    if (!response.isSuccessful()) {
                        // Try to pull the error message from the JSON body
                        try {
                            String msg = new JSONObject(body).optString("error", "HTTP " + response.code());
                            callback.onError(msg);
                        } catch (Exception ignored) {
                            callback.onError("HTTP " + response.code());
                        }
                        return;
                    }
                    OperatorEntity entity = parse(body);
                    if (entity != null) {
                        callback.onSuccess(entity);
                    } else {
                        callback.onError("Unexpected response");
                    }
                }
            }
        });
    }

    public void losCheck(double lat1, double lon1, double lat2, double lon2,
                         double h1, double h2, LoSCallback callback) {
        String url = String.format(java.util.Locale.US,
                "https://fimblefowl.co.uk/qrz/los?lat1=%f&lon1=%f&lat2=%f&lon2=%f&h1=%.1f&h2=%.1f",
                lat1, lon1, lat2, lon2, h1, h2);
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body().string();
                    JSONObject obj = new JSONObject(body);
                    if (obj.has("error")) {
                        callback.onError(obj.optString("error"));
                        return;
                    }
                    callback.onResult(new LoSResult(
                            obj.optBoolean("clear"),
                            obj.optDouble("my_elev"),
                            obj.optDouble("target_elev"),
                            obj.optDouble("distance_km"),
                            obj.optDouble("obs_lat"),
                            obj.optDouble("obs_lon")));
                } catch (Exception ex) {
                    callback.onError("Parse error: " + ex.getMessage());
                }
            }
        });
    }

    public void getSites(SitesCallback callback) {
        Request request = new Request.Builder()
                .url("https://fimblefowl.co.uk/qrz/sites")
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body().string();
                    JSONArray arr = new JSONArray(body);
                    List<Site> sites = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Site s = new Site();
                        s.call_sign = o.optString("call_sign");
                        s.name      = o.optString("name");
                        s.lat       = o.optDouble("lat");
                        s.lon       = o.optDouble("lon");
                        s.qnf       = o.optDouble("qnf", 3);
                        s.qnh       = o.optDouble("qnh");
                        s.site_type = o.optString("site_type");
                        sites.add(s);
                    }
                    callback.onResult(sites);
                } catch (Exception ex) {
                    callback.onError("Parse error: " + ex.getMessage());
                }
            }
        });
    }

    public void getSitesLoS(String callsign, SitesLoSCallback callback) {
        Request request = new Request.Builder()
                .url("https://fimblefowl.co.uk/qrz/sites/los/" + callsign.toUpperCase())
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body().string();
                    JSONObject obj = new JSONObject(body);
                    if (obj.has("error")) { callback.onError(obj.optString("error")); return; }
                    Map<String, SitesLoSEntry> result = new HashMap<>();
                    java.util.Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String cs = keys.next();
                        JSONObject e = obj.getJSONObject(cs);
                        result.put(cs, new SitesLoSEntry(
                                e.optBoolean("clear"),
                                e.optDouble("obs_lat"),
                                e.optDouble("obs_lon")));
                    }
                    callback.onResult(result);
                } catch (Exception ex) {
                    callback.onError("Parse error: " + ex.getMessage());
                }
            }
        });
    }

    private OperatorEntity parse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("error")) {
                return null;
            }

            OperatorEntity e = new OperatorEntity();
            e.callsign = obj.optString("callsign", "").toUpperCase();
            e.name     = obj.optString("name");
            e.fname    = obj.optString("fname");
            e.lname    = obj.optString("lname");
            e.city     = obj.optString("city");
            e.state    = obj.optString("state");
            e.country  = obj.optString("country");
            e.grid     = obj.optString("grid");

            // lat/lon arrive as strings from the QRZ XML → Go server
            String latStr = obj.optString("lat");
            String lonStr = obj.optString("lon");
            e.lat = latStr.isEmpty() ? 0 : Double.parseDouble(latStr);
            e.lon = lonStr.isEmpty() ? 0 : Double.parseDouble(lonStr);

            String altmStr = obj.optString("altm");
            e.altm = altmStr.isEmpty() ? 0 : Double.parseDouble(altmStr);

            return e.callsign.isEmpty() ? null : e;
        } catch (Exception ex) {
            Log.e(TAG, "JSON parse failed", ex);
            return null;
        }
    }
}
