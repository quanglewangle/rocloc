package com.quanglewangle.peter.rocloc;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.quanglewangle.peter.rocloc.api.ApiService;
import com.quanglewangle.peter.rocloc.data.Site;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment {

    private MapView mapView;
    private ProgressBar progressBar;
    private final ApiService api = new ApiService();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<Site> allSites = new ArrayList<>();
    private final List<Polyline> losOverlays = new ArrayList<>();
    private RadiusMarkerClusterer clusterOverlay;
    private boolean sitesLoaded = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.mapProgress);
        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.getController().setZoom(6.5);
        mapView.getController().setCenter(new GeoPoint(54.5, -3.0));

        mapView.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(
                new org.osmdroid.events.MapEventsReceiver() {
                    @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                        Site nearest = nearestSite(p, 40);
                        if (nearest != null) {
                            fetchLoS(nearest);
                        } else {
                            clearLoS();
                        }
                        return true;
                    }
                    @Override public boolean longPressHelper(GeoPoint p) { return false; }
                }));

        if (!sitesLoaded) loadSites(false);
    }

    @Override public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && mapView != null) {
            if (!sitesLoaded) {
                loadSites(false);
            } else {
                mapView.invalidate();
            }
        }
    }

    public void goToSite(Site site) {
        if (mapView == null) return;
        clearLoS();
        mapView.getController().animateTo(new GeoPoint(site.lat, site.lon));
        mapView.getController().setZoom(11.0);
        fetchLoS(site);
    }

    private void loadSites(boolean fitBounds) {
        progressBar.setVisibility(View.VISIBLE);
        api.getSites(new ApiService.SitesCallback() {
            @Override public void onResult(List<Site> sites) {
                mainHandler.post(() -> {
                    allSites = sites;
                    sitesLoaded = true;
                    rebuildMarkers(fitBounds);
                    progressBar.setVisibility(View.GONE);
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> progressBar.setVisibility(View.GONE));
            }
        });
    }

    private void rebuildMarkers(boolean fitBounds) {
        // Remove old cluster overlay (keep map events overlay at index 0)
        if (clusterOverlay != null) mapView.getOverlays().remove(clusterOverlay);
        losOverlays.clear();

        clusterOverlay = new RadiusMarkerClusterer(requireContext());
        clusterOverlay.setRadius(100);

        List<GeoPoint> points = new ArrayList<>();
        for (Site s : allSites) {
            if (s.lat == 0 && s.lon == 0) continue;
            GeoPoint gp = new GeoPoint(s.lat, s.lon);
            Marker m = dotMarker(mapView, s.isPin() ? Color.rgb(34, 197, 94) : Color.rgb(239, 107, 34));
            m.setPosition(gp);
            m.setTitle(s.displayCallsign());
            m.setSnippet(s.isPin() ? "Pin" : safe(s.name));
            clusterOverlay.add(m);
            points.add(gp);
        }

        mapView.getOverlays().add(clusterOverlay);

        if (fitBounds && points.size() > 1) {
            BoundingBox box = BoundingBox.fromGeoPoints(points);
            mapView.post(() -> mapView.zoomToBoundingBox(box, true, 80));
        }
        mapView.invalidate();
    }

    private void fetchLoS(Site src) {
        if (src.call_sign == null || src.call_sign.isEmpty()) return;
        clearLoS();
        progressBar.setVisibility(View.VISIBLE);
        api.getSitesLoS(src.call_sign, new ApiService.SitesLoSCallback() {
            @Override public void onResult(Map<String, ApiService.SitesLoSEntry> result) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    drawLoS(src, result);
                });
            }
            @Override public void onError(String error) {
                mainHandler.post(() -> progressBar.setVisibility(View.GONE));
            }
        });
    }

    private void drawLoS(Site src, Map<String, ApiService.SitesLoSEntry> result) {
        for (Map.Entry<String, ApiService.SitesLoSEntry> entry : result.entrySet()) {
            Site target = findSite(entry.getKey());
            if (target == null) continue;
            ApiService.SitesLoSEntry e = entry.getValue();
            GeoPoint srcPt = new GeoPoint(src.lat, src.lon);
            GeoPoint dstPt = new GeoPoint(target.lat, target.lon);

            if (e.clear) {
                addLine(srcPt, dstPt, Color.rgb(34, 197, 94), 0.85f);
            } else if (e.obsLat != 0 || e.obsLon != 0) {
                GeoPoint obsPt = new GeoPoint(e.obsLat, e.obsLon);
                addLine(srcPt, obsPt, Color.rgb(239, 68, 68), 0.85f);
                addLine(obsPt, dstPt, Color.DKGRAY, 0.35f);
            }
        }
        mapView.invalidate();
    }

    private void addLine(GeoPoint a, GeoPoint b, int color, float alpha) {
        Polyline line = new Polyline();
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(a); pts.add(b);
        line.setPoints(pts);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setAlpha((int)(alpha * 255));
        line.getOutlinePaint().setStrokeWidth(3f);
        mapView.getOverlays().add(line);
        losOverlays.add(line);
    }

    private void clearLoS() {
        for (Polyline p : losOverlays) mapView.getOverlays().remove(p);
        losOverlays.clear();
        if (mapView != null) mapView.invalidate();
    }

    @Nullable
    private Site nearestSite(GeoPoint tap, float thresholdDp) {
        if (mapView == null || allSites.isEmpty()) return null;
        float threshPx = thresholdDp * getResources().getDisplayMetrics().density;
        Point tapPx = mapView.getProjection().toPixels(tap, null);
        Site best = null;
        double bestDist = Double.MAX_VALUE;
        for (Site s : allSites) {
            if (s.lat == 0 && s.lon == 0) continue;
            Point sPx = mapView.getProjection().toPixels(new GeoPoint(s.lat, s.lon), null);
            double dx = tapPx.x - sPx.x;
            double dy = tapPx.y - sPx.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < bestDist) { bestDist = dist; best = s; }
        }
        return (bestDist <= threshPx) ? best : null;
    }

    @Nullable
    private Site findSite(String callsign) {
        for (Site s : allSites) {
            if (callsign.equalsIgnoreCase(s.call_sign)) return s;
        }
        return null;
    }

    private Marker dotMarker(MapView map, int color) {
        float dp = getResources().getDisplayMetrics().density;
        int px = (int) (12 * dp);
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(bmp).drawCircle(px / 2f, px / 2f, px / 2f, paint);
        Marker m = new Marker(map);
        m.setIcon(new BitmapDrawable(getResources(), bmp));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        return m;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
