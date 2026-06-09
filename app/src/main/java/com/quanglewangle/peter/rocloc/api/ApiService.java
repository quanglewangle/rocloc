package com.quanglewangle.peter.rocloc.api;

import android.util.Log;

import com.quanglewangle.peter.rocloc.data.OperatorEntity;

import org.json.JSONObject;

import java.io.IOException;

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
