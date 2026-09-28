package com.example.myapplication.ui.statistics.efficiency;
import com.example.myapplication.tracking.usage.UsageStatsHelper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.ui.general.ColorAndIcons;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

public class LocationUsageAdapter extends RecyclerView.Adapter<LocationUsageAdapter.ViewHolder> {

    private final Context context;
    private final List<LocationUsageStats> items;

    public LocationUsageAdapter(Context context, List<LocationUsageStats> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location_usage_rank, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationUsageStats item = items.get(position);

        holder.tvLocationName.setText(item.getLocationName());
        holder.tvStayTime.setText(String.format("Visit: %s", EfficiencyStatsManager.formatDuration(item.getTotalStayDurationMs())));
        holder.tvScreenTime.setText(String.format("%s phone", EfficiencyStatsManager.formatDuration(item.getTotalScreenTimeMs())));
        holder.tvPercentage.setText(String.format(Locale.getDefault(), "%d%% of visit", item.getUsagePercentage()));
        holder.progressUsage.setProgress(item.getUsagePercentage());

        // Category icon & color styling
        StillLocation dummyStill = new StillLocation();
        dummyStill.setIcon(item.getIconName());
        dummyStill.setCategory(item.getCategory());
        dummyStill.setColor(item.getColor());

        int iconRes = ColorAndIcons.getStillIconRes(dummyStill);
        holder.ivLocationIcon.setImageResource(iconRes);

        int color = ColorAndIcons.getStillColor(dummyStill, holder.itemView.getContext());
        holder.cardIcon.setCardBackgroundColor(ColorStateList.valueOf(color));

        // Top apps display
        List<UsageStatsHelper.AppUsageInfo> topApps = item.getTopApps();
        if (topApps != null && !topApps.isEmpty()) {
            StringBuilder sb = new StringBuilder("Top apps: ");
            int limit = Math.min(2, topApps.size());
            for (int i = 0; i < limit; i++) {
                UsageStatsHelper.AppUsageInfo appInfo = topApps.get(i);
                sb.append(appInfo.getAppName())
                        .append(" (")
                        .append(EfficiencyStatsManager.formatDuration(appInfo.getTotalTimeInForegroundMs()))
                        .append(")");
                if (i < limit - 1) {
                    sb.append(", ");
                }
            }
            holder.tvTopApps.setText(sb.toString());
            holder.tvTopApps.setVisibility(View.VISIBLE);
        } else {
            holder.tvTopApps.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardIcon;
        final ImageView ivLocationIcon;
        final TextView tvLocationName;
        final TextView tvStayTime;
        final TextView tvScreenTime;
        final TextView tvPercentage;
        final LinearProgressIndicator progressUsage;
        final TextView tvTopApps;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardIcon = itemView.findViewById(R.id.card_location_icon);
            ivLocationIcon = itemView.findViewById(R.id.iv_location_icon);
            tvLocationName = itemView.findViewById(R.id.tv_location_name);
            tvStayTime = itemView.findViewById(R.id.tv_location_stay_time);
            tvScreenTime = itemView.findViewById(R.id.tv_location_screen_time);
            tvPercentage = itemView.findViewById(R.id.tv_location_percentage);
            progressUsage = itemView.findViewById(R.id.progress_usage_percent);
            tvTopApps = itemView.findViewById(R.id.tv_top_apps_label);
        }
    }
}
