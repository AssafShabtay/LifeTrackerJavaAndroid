package com.example.myapplication.mainScreen.homeScreen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.AppUsageSession;
import com.example.myapplication.database.StayWithUsage;
import com.example.myapplication.database.MovementWithUsage;
import com.example.myapplication.helpers.UiFormatters;
import com.example.myapplication.helpers.UsageStatsHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StayUsageBottomSheet extends BottomSheetDialogFragment {

    private String title;
    private String subtitle;
    private long dwellMs;
    private long screenMs;
    private int pickups;
    private String zoneCategory;
    private List<AppUsageSession> appSessions = new ArrayList<>();

    public static StayUsageBottomSheet newInstanceForStay(StayWithUsage stay) {
        StayUsageBottomSheet sheet = new StayUsageBottomSheet();
        if (stay != null) {
            sheet.title = stay.getStillLocation() != null && stay.getStillLocation().getPlaceName() != null
                    ? stay.getStillLocation().getPlaceName() : "Stationary Stay";
            sheet.zoneCategory = stay.getStillLocation() != null && stay.getStillLocation().getCategory() != null
                    ? stay.getStillLocation().getCategory() : "General";

            Date start = stay.getStartTimeDate();
            Date end = stay.getEndTimeDate();
            sheet.dwellMs = (start != null && end != null) ? Math.max(0, end.getTime() - start.getTime()) : 0;
            sheet.screenMs = stay.getTotalScreenOnTimeMs();
            sheet.pickups = stay.getUnlockCount();
            sheet.appSessions = stay.getAppUsageList();

            String timeRange = UiFormatters.formatTimeRange(start, end);
            sheet.subtitle = timeRange + " • Total Dwell: " + UiFormatters.formatDurationMs(sheet.dwellMs);
        }
        return sheet;
    }

    public static StayUsageBottomSheet newInstanceForMovement(MovementWithUsage movement) {
        StayUsageBottomSheet sheet = new StayUsageBottomSheet();
        if (movement != null) {
            sheet.title = movement.getMovementActivity() != null && movement.getMovementActivity().getActivityTypeName() != null
                    ? movement.getMovementActivity().getActivityTypeName() : "Movement Activity";
            sheet.zoneCategory = "Transit";

            Date start = movement.getStartTimeDate();
            Date end = movement.getEndTimeDate();
            sheet.dwellMs = (start != null && end != null) ? Math.max(0, end.getTime() - start.getTime()) : 0;
            sheet.screenMs = movement.getTotalScreenOnTimeMs();
            sheet.pickups = movement.getUnlockCount();

            String timeRange = UiFormatters.formatTimeRange(start, end);
            sheet.subtitle = timeRange + " • Duration: " + UiFormatters.formatDurationMs(sheet.dwellMs);
        }
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_stay_usage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_sheet_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_sheet_subtitle);
        TextView tvPresenceTime = view.findViewById(R.id.tv_sheet_presence_time);
        TextView tvScreenTime = view.findViewById(R.id.tv_sheet_screen_time);
        TextView tvPickups = view.findViewById(R.id.tv_sheet_pickups);
        TextView tvAvgPickup = view.findViewById(R.id.tv_sheet_avg_pickup);
        TextView tvCalloutText = view.findViewById(R.id.tv_reclaim_callout_text);
        RecyclerView rvAppUsage = view.findViewById(R.id.rv_app_usage_list);
        View btnClose = view.findViewById(R.id.btn_close_sheet);

        if (tvTitle != null) tvTitle.setText(title != null ? title : "Dwell Analysis");
        if (tvSubtitle != null) tvSubtitle.setText(subtitle != null ? subtitle : "");

        long physicalMs = Math.max(0, dwellMs - screenMs);
        int presencePct = dwellMs > 0 ? (int) ((physicalMs * 100) / dwellMs) : 100;
        int screenPct = dwellMs > 0 ? (int) ((screenMs * 100) / dwellMs) : 0;

        if (tvPresenceTime != null) {
            tvPresenceTime.setText(UiFormatters.formatDurationMs(physicalMs) + " (" + presencePct + "%)");
        }
        if (tvScreenTime != null) {
            tvScreenTime.setText(UiFormatters.formatDurationMs(screenMs) + " (" + screenPct + "%)");
        }
        if (tvPickups != null) {
            tvPickups.setText(pickups + " times");
        }
        if (tvAvgPickup != null) {
            long avgMs = pickups > 0 ? screenMs / pickups : 0;
            long avgMins = avgMs / (60 * 1000);
            tvAvgPickup.setText(avgMins > 0 ? avgMins + " mins" : "< 1 min");
        }

        if (tvCalloutText != null) {
            String callout = UsageStatsHelper.buildTimeReclaimCallout(appSessions, zoneCategory);
            tvCalloutText.setText(callout);
        }

        if (rvAppUsage != null) {
            rvAppUsage.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvAppUsage.setAdapter(new AppUsageAdapter(appSessions));
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    private static class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {
        private final List<AppUsageSession> items;

        AppUsageAdapter(List<AppUsageSession> items) {
            this.items = items != null ? items : new ArrayList<>();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppUsageSession item = items.get(position);
            holder.tvName.setText(item.getAppLabel() != null ? item.getAppLabel() : item.getPackageName());
            holder.tvCategory.setText(item.getCategory() != null ? item.getCategory().name() : "UTILITY");
            holder.tvDuration.setText(UiFormatters.formatDurationMs(item.getTotalForegroundTimeMs()));

            if (item.getAppIcon() != null) {
                holder.ivIcon.setImageDrawable(item.getAppIcon());
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_statistics);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvCategory;
            TextView tvDuration;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_app_icon);
                tvName = itemView.findViewById(R.id.tv_app_name);
                tvCategory = itemView.findViewById(R.id.tv_app_category);
                tvDuration = itemView.findViewById(R.id.tv_app_duration);
            }
        }
    }
}
