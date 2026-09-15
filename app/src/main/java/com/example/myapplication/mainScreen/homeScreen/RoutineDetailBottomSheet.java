package com.example.myapplication.mainScreen.homeScreen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.myapplication.database.MovementWithHabit;
import com.example.myapplication.database.StayWithHabit;
import com.example.myapplication.helpers.HabitManager;
import com.example.myapplication.helpers.UiFormatters;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Date;

public class RoutineDetailBottomSheet extends BottomSheetDialogFragment {

    private String title;
    private String subtitle;
    private long targetMs;
    private long actualMs;
    private boolean isGoalMet;
    private String badgeText;
    private int streakDays;
    private String punctualityText;

    public static RoutineDetailBottomSheet newInstanceForStay(StayWithHabit stay) {
        RoutineDetailBottomSheet sheet = new RoutineDetailBottomSheet();
        if (stay != null) {
            sheet.title = stay.getStillLocation() != null && stay.getStillLocation().getPlaceName() != null
                    ? stay.getStillLocation().getPlaceName() + " Routine" : "Focus Routine Goal";
            sheet.targetMs = stay.getTargetDwellMs();

            Date start = stay.getStartTimeDate();
            Date end = stay.getEndTimeDate();
            sheet.actualMs = (start != null && end != null) ? Math.max(0, end.getTime() - start.getTime()) : 0;
            sheet.isGoalMet = stay.isGoalMet();
            sheet.badgeText = stay.getHabitBadgeText();
            sheet.streakDays = stay.getStreakCount();

            String arrival = start != null ? UiFormatters.timeOnly(start) : "—";
            sheet.punctualityText = arrival + " (" + (stay.isGoalMet() ? "On Time" : "Standard") + ")";
            sheet.subtitle = "Target: " + UiFormatters.formatDurationMs(sheet.targetMs) + " • Achieved: "
                    + UiFormatters.formatDurationMs(sheet.actualMs);
        }
        return sheet;
    }

    public static RoutineDetailBottomSheet newInstanceForMovement(MovementWithHabit movement) {
        RoutineDetailBottomSheet sheet = new RoutineDetailBottomSheet();
        if (movement != null) {
            sheet.title = movement.getMovementActivity() != null && movement.getMovementActivity().getActivityTypeName() != null
                    ? movement.getMovementActivity().getActivityTypeName() + " Movement Goal" : "Movement Goal";
            sheet.targetMs = movement.getTargetMovementMs();

            Date start = movement.getStartTimeDate();
            Date end = movement.getEndTimeDate();
            sheet.actualMs = (start != null && end != null) ? Math.max(0, end.getTime() - start.getTime()) : 0;
            sheet.isGoalMet = movement.isGoalMet();
            sheet.badgeText = movement.getHabitBadgeText();
            sheet.streakDays = 7;
            sheet.punctualityText = "Active Session";
            sheet.subtitle = "Target: " + UiFormatters.formatDurationMs(sheet.targetMs) + " • Today: "
                    + UiFormatters.formatDurationMs(sheet.actualMs);
        }
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_routine_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_routine_sheet_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_routine_sheet_subtitle);
        TextView tvBadge = view.findViewById(R.id.tv_goal_status_badge);
        ProgressBar progressBar = view.findViewById(R.id.progress_routine_detail);

        TextView tvTarget = view.findViewById(R.id.tv_detail_target);
        TextView tvActual = view.findViewById(R.id.tv_detail_actual);
        TextView tvStreak = view.findViewById(R.id.tv_detail_streak);
        TextView tvPunctual = view.findViewById(R.id.tv_detail_punctual);
        TextView tvCallout = view.findViewById(R.id.tv_routine_callout_text);
        View btnClose = view.findViewById(R.id.btn_close_routine_sheet);

        if (tvTitle != null) tvTitle.setText(title != null ? title : "Routine Detail");
        if (tvSubtitle != null) tvSubtitle.setText(subtitle != null ? subtitle : "");
        if (tvBadge != null) tvBadge.setText(badgeText != null ? badgeText : (isGoalMet ? "[✓ Goal Met]" : "[In Progress]"));

        if (progressBar != null) {
            int pct = targetMs > 0 ? (int) Math.min(100, (actualMs * 100) / targetMs) : 100;
            progressBar.setProgress(pct);
        }

        if (tvTarget != null) tvTarget.setText(UiFormatters.formatDurationMs(targetMs));
        if (tvActual != null) tvActual.setText(UiFormatters.formatDurationMs(actualMs));
        if (tvStreak != null) tvStreak.setText("🔥 " + streakDays + " Days");
        if (tvPunctual != null) tvPunctual.setText(punctualityText != null ? punctualityText : "On Time");

        if (tvCallout != null) {
            tvCallout.setText(HabitManager.getRoutineMotivationalCallout(isGoalMet ? 3 : 2, 3));
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }
}
