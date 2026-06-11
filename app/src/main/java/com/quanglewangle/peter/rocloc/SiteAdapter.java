package com.quanglewangle.peter.rocloc;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.rocloc.data.Site;

import java.util.List;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    public interface OnItemClickListener { void onItemClick(Site site); }

    private final List<Site> items;
    private final OnItemClickListener listener;

    public SiteAdapter(List<Site> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_site, parent, false);
        ViewHolder h = new ViewHolder(v);
        v.setOnClickListener(view -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_ID) listener.onItemClick(items.get(pos));
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Site s = items.get(position);
        if (s.isPin()) {
            h.callsign.setText(safe(s.name));
            h.name.setText("");
        } else {
            h.callsign.setText(s.displayCallsign());
            h.name.setText(safe(s.name));
        }
        h.latlon.setText(String.format(java.util.Locale.US, "%.4f°N  %.4f°E", s.lat, s.lon));
        if (s.qnh > 0) {
            h.elev.setText(String.format(java.util.Locale.US, "%.0f m ASL", s.qnh));
        } else {
            h.elev.setText("");
        }
    }

    public static void showDetail(View anchor, Site s) {
        StringBuilder msg = new StringBuilder();
        if (s.name != null && !s.name.isEmpty()) msg.append(s.name).append("\n");
        if (s.call_sign != null && !s.call_sign.isEmpty()) msg.append("Callsign: ").append(s.call_sign).append("\n");
        if (s.qnf > 0) msg.append("Antenna height: ").append((int) s.qnf).append(" m\n");
        if (s.qnh > 0) msg.append("Elevation: ").append((int) s.qnh).append(" m ASL\n");
        msg.append(String.format(java.util.Locale.US, "%.4f°N  %.4f°E", s.lat, s.lon));
        new AlertDialog.Builder(anchor.getContext())
                .setTitle(s.displayCallsign())
                .setMessage(msg.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override public int getItemCount() { return items.size(); }

    private static String safe(String s) { return s == null ? "" : s; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView callsign, name, latlon, elev;
        ViewHolder(View v) {
            super(v);
            callsign = v.findViewById(R.id.siteCallsign);
            name     = v.findViewById(R.id.siteName);
            latlon   = v.findViewById(R.id.siteLatLon);
            elev     = v.findViewById(R.id.siteElev);
        }
    }
}
