package com.example.myapplication.mainScreen.dashboardScreen.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.ColorAndIcons;
import com.example.myapplication.helpers.UsageStatsHelper;
import com.example.myapplication.mainScreen.dashboardScreen.model.LocationUsageItem;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class LocationUsageAdapter extends RecyclerView.Adapter<LocationUsageAdapter.ViewHolder> {

    private final List<LocationUsageItem> items = new ArrayList<>();

    public void setItems(List<LocationUsageItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location_usage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationUsageItem item = items.get(position);
        Context context = holder.itemView.getContext();

        holder.tvPlaceName.setText(item.getPlaceName());

        String physicalFormatted = formatDuration(item.getPhysicalDurationMs());
        holder.tvPhysicalDuration.setText("Present for " + physicalFormatted);

        String screenFormatted = formatDuration(item.getScreenTimeMs());
        holder.tvScreenTime.setText(screenFormatted + " phone");

        int pct = item.getScreenPercentageOfPhysicalTime();
        holder.tvScreenPercentage.setText(pct + "% screen");
        holder.progressScreenTime.setProgress(pct);

        // Place icon and tinting
        StillLocation tempStill = new StillLocation();
        tempStill.setIcon(item.getIcon());
        tempStill.setCategory(item.getCategory());
        int iconRes = ColorAndIcons.getStillIconRes(tempStill);
        holder.ivPlaceIcon.setImageResource(iconRes);
        if (item.getColor() != null) {
            holder.cardPlaceIcon.setCardBackgroundColor(ColorStateList.valueOf(item.getColor()));
        }

        // Top apps summary
        List<UsageStatsHelper.AppUsageInfo> topApps = item.getTopApps();
        if (topApps == null || topApps.isEmpty() || item.getScreenTimeMs() == 0) {
            holder.layoutTopApps.setVisibility(View.GONE);
        } else {
            holder.layoutTopApps.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            int count = Math.min(topApps.size(), 3);
            for (int i = 0; i < count; i++) {
                UsageStatsHelper.AppUsageInfo app = topApps.get(i);
                if (i > 0) sb.append(", ");
                sb.append(app.getAppName())
                  .append(" (")
                  .append(formatDuration(app.getTotalTimeInForegroundMs()))
                  .append(")");
            }
            holder.tvTopAppsSummary.setText(sb.toString());
        }
    }

    private String formatDuration(long millis) {
        long minutes = millis / (60 * 1000);
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours > 0) {
            return hours + "h " + remainingMinutes + "m";
        }
        return remainingMinutes + "m";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardPlaceIcon;
        ImageView ivPlaceIcon;
        TextView tvPlaceName;
        TextView tvPhysicalDuration;
        TextView tvScreenTime;
        TextView tvScreenPercentage;
        LinearProgressIndicator progressScreenTime;
        View layoutTopApps;
        TextView tvTopAppsSummary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPlaceIcon = itemView.findViewById(R.id.cardPlaceIcon);
            ivPlaceIcon = itemView.findViewById(R.id.ivPlaceIcon);
            tvPlaceName = itemView.findViewById(R.id.tvPlaceName);
            tvPhysicalDuration = itemView.findViewById(R.id.tvPhysicalDuration);
            tvScreenTime = itemView.findViewById(R.id.tvScreenTime);
            tvScreenPercentage = itemView.findViewById(R.id.tvScreenPercentage);
            progressScreenTime = itemView.findViewById(R.id.progressScreenTime);
            layoutTopApps = itemView.findViewById(R.id.layoutTopApps);
            tvTopAppsSummary = itemView.findViewById(R.id.tvTopAppsSummary);
        }
    }
}
