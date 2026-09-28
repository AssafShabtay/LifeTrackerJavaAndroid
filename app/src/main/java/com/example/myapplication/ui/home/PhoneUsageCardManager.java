package com.example.myapplication.ui.home;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.tracking.usage.UsageStatsHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PhoneUsageCardManager {

    private final Context context;
    private final View cardView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private View layoutUsageContent;
    private View layoutPermissionPrompt;
    private Button btnGrantPermission;

    private PhoneUsageDonutView donutView;
    private TextView tvTotalScreenTime;

    private View dotSocial, dotMedia, dotWork, dotOther;
    private TextView tvCategorySocial, tvCategoryMedia, tvCategoryWork, tvCategoryOther;

    private final int colorSocial;
    private final int colorMedia;
    private final int colorWork;
    private final int colorOther;

    public PhoneUsageCardManager(Context context, View cardView) {
        this.context = context;
        this.cardView = cardView;

        colorSocial = ContextCompat.getColor(context, R.color.purple);
        colorMedia = ContextCompat.getColor(context, R.color.blue);
        colorWork = ContextCompat.getColor(context, R.color.green);
        colorOther = ContextCompat.getColor(context, R.color.orange);

        initViews();
    }

    private void initViews() {
        if (cardView == null) return;

        layoutUsageContent = cardView.findViewById(R.id.layout_usage_content);
        layoutPermissionPrompt = cardView.findViewById(R.id.layout_permission_prompt);
        btnGrantPermission = cardView.findViewById(R.id.btn_grant_usage_permission);

        donutView = cardView.findViewById(R.id.phone_usage_donut_view);
        tvTotalScreenTime = cardView.findViewById(R.id.tv_total_screen_time);

        dotSocial = cardView.findViewById(R.id.dot_social);
        dotMedia = cardView.findViewById(R.id.dot_media);
        dotWork = cardView.findViewById(R.id.dot_work);
        dotOther = cardView.findViewById(R.id.dot_other);

        tvCategorySocial = cardView.findViewById(R.id.tv_category_social);
        tvCategoryMedia = cardView.findViewById(R.id.tv_category_media);
        tvCategoryWork = cardView.findViewById(R.id.tv_category_work);
        tvCategoryOther = cardView.findViewById(R.id.tv_category_other);

        if (btnGrantPermission != null) {
            btnGrantPermission.setOnClickListener(v -> UsageStatsHelper.openUsageAccessSettings(context));
        }

        setDotColor(dotSocial, colorSocial);
        setDotColor(dotMedia, colorMedia);
        setDotColor(dotWork, colorWork);
        setDotColor(dotOther, colorOther);
    }

    private void setDotColor(View dotView, int color) {
        if (dotView != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            dotView.setBackground(drawable);
        }
    }

    public void loadUsageDataForDate(Date date) {
        if (cardView == null) return;

        boolean hasPermission = UsageStatsHelper.hasUsageAccessPermission(context);
        if (!hasPermission) {
            if (layoutUsageContent != null) layoutUsageContent.setVisibility(View.GONE);
            if (layoutPermissionPrompt != null) layoutPermissionPrompt.setVisibility(View.VISIBLE);
            return;
        }

        if (layoutUsageContent != null) layoutUsageContent.setVisibility(View.VISIBLE);
        if (layoutPermissionPrompt != null) layoutPermissionPrompt.setVisibility(View.GONE);

        Date selectedDate = date != null ? date : new Date();

        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long endTime = Math.min(cal.getTimeInMillis(), System.currentTimeMillis());

        executorService.execute(() -> {
            Map<String, Long> packageUsage = UsageStatsHelper.getUsageTimePerPackage(context, startTime, endTime);

            long socialMs = 0;
            long mediaMs = 0;
            long workMs = 0;
            long otherMs = 0;

            PackageManager pm = context.getPackageManager();

            for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
                String pkg = entry.getKey();
                long duration = entry.getValue();
                if (duration <= 0) continue;

                int category = categorizePackage(pm, pkg);
                switch (category) {
                    case 1: socialMs += duration; break;
                    case 2: mediaMs += duration; break;
                    case 3: workMs += duration; break;
                    default: otherMs += duration; break;
                }
            }

            long totalMs = socialMs + mediaMs + workMs + otherMs;

            final long finalSocialMs = socialMs;
            final long finalMediaMs = mediaMs;
            final long finalWorkMs = workMs;
            final long finalOtherMs = otherMs;
            final long finalTotalMs = totalMs;

            mainHandler.post(() -> updateUi(finalTotalMs, finalSocialMs, finalMediaMs, finalWorkMs, finalOtherMs));
        });
    }

    private void updateUi(long totalMs, long socialMs, long mediaMs, long workMs, long otherMs) {
        if (tvTotalScreenTime != null) {
            tvTotalScreenTime.setText(formatDuration(totalMs));
        }

        if (tvCategorySocial != null) tvCategorySocial.setText("Social: " + formatDuration(socialMs));
        if (tvCategoryMedia != null) tvCategoryMedia.setText("Media: " + formatDuration(mediaMs));
        if (tvCategoryWork != null) tvCategoryWork.setText("Work: " + formatDuration(workMs));
        if (tvCategoryOther != null) tvCategoryOther.setText("Other: " + formatDuration(otherMs));

        List<PhoneUsageDonutView.DonutSegment> segments = new ArrayList<>();
        if (totalMs > 0) {
            if (socialMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) socialMs / totalMs, colorSocial, "Social"));
            if (mediaMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) mediaMs / totalMs, colorMedia, "Media"));
            if (workMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) workMs / totalMs, colorWork, "Work"));
            if (otherMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) otherMs / totalMs, colorOther, "Other"));
        }

        if (donutView != null) {
            donutView.setSegments(segments, true);
        }
    }

    private int categorizePackage(PackageManager pm, String pkg) {
        String lowerPkg = pkg.toLowerCase(Locale.ROOT);

        // Social apps
        if (lowerPkg.contains("instagram") || lowerPkg.contains("facebook") || lowerPkg.contains("whatsapp") ||
                lowerPkg.contains("twitter") || lowerPkg.contains("tiktok") || lowerPkg.contains("telegram") ||
                lowerPkg.contains("snapchat") || lowerPkg.contains("reddit") || lowerPkg.contains("linkedin") ||
                lowerPkg.contains("pinterest") || lowerPkg.contains("messenger") || lowerPkg.contains("discord")) {
            return 1; // Social
        }

        // Media & Entertainment
        if (lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("spotify") ||
                lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("twitch") ||
                lowerPkg.contains("hulu") || lowerPkg.contains("disney") || lowerPkg.contains("gallery") ||
                lowerPkg.contains("photos") || lowerPkg.contains("vlc") || lowerPkg.contains("music")) {
            return 2; // Media
        }

        // Work & Productivity
        if (lowerPkg.contains("gmail") || lowerPkg.contains("outlook") || lowerPkg.contains("slack") ||
                lowerPkg.contains("teams") || lowerPkg.contains("docs") || lowerPkg.contains("sheets") ||
                lowerPkg.contains("calendar") || lowerPkg.contains("notes") || lowerPkg.contains("keep") ||
                lowerPkg.contains("calculator") || lowerPkg.contains("clock") || lowerPkg.contains("android.studio") ||
                lowerPkg.contains("drive") || lowerPkg.contains("zoom")) {
            return 3; // Work
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pm != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                int appCategory = appInfo.category;
                if (appCategory == ApplicationInfo.CATEGORY_SOCIAL) return 1;
                if (appCategory == ApplicationInfo.CATEGORY_AUDIO || appCategory == ApplicationInfo.CATEGORY_VIDEO || appCategory == ApplicationInfo.CATEGORY_GAME) return 2;
                if (appCategory == ApplicationInfo.CATEGORY_PRODUCTIVITY) return 3;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        return 4; // Other
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "0m";

        long totalMinutes = durationMs / (1000 * 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else {
            return String.format(Locale.getDefault(), "%dm", minutes);
        }
    }
}
