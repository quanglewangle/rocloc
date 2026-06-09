package com.quanglewangle.peter.rocloc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.rocloc.data.OperatorEntity;

import java.util.List;

public class OperatorListAdapter extends RecyclerView.Adapter<OperatorListAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(OperatorEntity entity);
    }

    private final List<OperatorEntity> items;
    private final OnItemClickListener listener;

    public OperatorListAdapter(List<OperatorEntity> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_operator, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        OperatorEntity e = items.get(position);
        h.callsign.setText(e.callsign);
        h.name.setText(nullSafe(e.name));
        String loc = nullSafe(e.grid);
        if (!nullSafe(e.city).isEmpty()) loc += "  " + e.city;
        h.grid.setText(loc.trim());
        h.itemView.setOnClickListener(v -> listener.onItemClick(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView callsign, name, grid;

        ViewHolder(View v) {
            super(v);
            callsign = v.findViewById(R.id.callsignText);
            name     = v.findViewById(R.id.nameText);
            grid     = v.findViewById(R.id.gridText);
        }
    }
}
