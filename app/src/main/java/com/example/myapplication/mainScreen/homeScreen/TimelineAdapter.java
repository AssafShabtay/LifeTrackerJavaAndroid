package com.example.myapplication.mainScreen.homeScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getMovementColorAndIcon;
import static com.example.myapplication.helpers.ColorAndIcons.getStillColor;
import static com.example.myapplication.helpers.ColorAndIcons.getStillIconRes;
import static com.example.myapplication.helpers.UiFormatters.category;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.MovementWithHabit;
import com.example.myapplication.database.MovementWithUsage;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.StayWithHabit;
import com.example.myapplication.database.StayWithUsage;
import com.example.myapplication.database.TimelineItem;
import com.example.myapplication.helpers.UiFormatters;

public class TimelineAdapter extends ListAdapter<TimelineItem, RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TimelineItem item);
    }

    public interface OnEditButtonClickListener {
        void onStillEditButtonClick(StillLocation still);
        void onMovementEditButtonClick(MovementActivity movement);
    }

    public TimelineAdapter(OnItemClickListener clickListener, OnEditButtonClickListener editButtonClickListener) {
        super(new TimelineDiffCallback());
        this.clickListener = clickListener;
        this.editButtonClickListener = editButtonClickListener;
    }

    private static final int TYPE_STILL = 0;
    private static final int TYPE_MOVEMENT = 1;

    private final OnItemClickListener clickListener;
    private final OnEditButtonClickListener editButtonClickListener;

    static class TimelineDiffCallback extends DiffUtil.ItemCallback<TimelineItem> {
        @Override
        public boolean areItemsTheSame(@NonNull TimelineItem oldItem, @NonNull TimelineItem newItem) {
            return oldItem.getId() == newItem.getId() && oldItem.getClass().equals(newItem.getClass());
        }

        @Override
        public boolean areContentsTheSame(@NonNull TimelineItem oldItem, @NonNull TimelineItem newItem) {
            return oldItem.equals(newItem);
        }
    }

    @Override
    public int getItemViewType(int position) {
        TimelineItem item = getItem(position);
        if (item instanceof StillLocation || item instanceof StayWithHabit || item instanceof StayWithUsage) {
            return TYPE_STILL;
        } else {
            return TYPE_MOVEMENT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_STILL) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_still_location, parent, false);
            return new StillViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_movement_activity, parent, false);
            return new MovementViewHolder(view);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem item = getItem(position);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(item);
        });

        if (holder instanceof StillViewHolder) {
            StillViewHolder stillHolder = (StillViewHolder) holder;

            StillLocation still = null;
            StayWithHabit stayHabit = null;

            if (item instanceof StayWithHabit) {
                stayHabit = (StayWithHabit) item;
                still = stayHabit.getStillLocation();
            } else if (item instanceof StayWithUsage) {
                still = ((StayWithUsage) item).getStillLocation();
            } else if (item instanceof StillLocation) {
                still = (StillLocation) item;
            }

            if (still == null) return;

            String title = still.getPlaceName() != null ? still.getPlaceName() : "Stationary";
            stillHolder.itemTitle.setText(title);

            if (still.getPlaceAddress() != null && !still.getPlaceAddress().isEmpty()) {
                stillHolder.itemAddress.setText(still.getPlaceAddress());
                stillHolder.itemAddress.setVisibility(View.VISIBLE);
            } else {
                stillHolder.itemAddress.setVisibility(View.GONE);
            }

            if (still.getCategory() != null && !still.getCategory().isEmpty()) {
                stillHolder.itemCategory.setText(category(still.getCategory()));
                stillHolder.itemCategory.setVisibility(View.VISIBLE);
            } else {
                stillHolder.itemCategory.setVisibility(View.GONE);
            }

            stillHolder.itemTimeRange.setText(UiFormatters.timeOnly(still.getStartTimeDate()) + " — " +
                    UiFormatters.timeOnly(still.getEndTimeDate()));
            stillHolder.itemDuration.setText(UiFormatters.duration(still.getStartTimeDate(), still.getEndTimeDate()));

            int iconXml = still.getIcon() != null ? getStillIconRes(still) : R.drawable.ic_still;
            stillHolder.itemIcon.setImageResource(iconXml);

            int stillColor = getStillColor(still, stillHolder.itemView.getContext());
            stillHolder.itemTitle.setTextColor(stillColor);
            stillHolder.itemIcon.setColorFilter(stillColor);
            stillHolder.iconContainer.setCardBackgroundColor(stillColor & 0x20FFFFFF);

            if (stillHolder.lineDot.getBackground() != null) {
                DrawableCompat.setTint(stillHolder.lineDot.getBackground().mutate(), stillColor);
            }

            // Bind Habit Goal Progress
            if (stayHabit != null) {
                stillHolder.layoutRatioBarContainer.setVisibility(View.VISIBLE);
                stillHolder.progressHabitGoal.setVisibility(View.VISIBLE);
                stillHolder.progressHabitGoal.setProgress(stayHabit.getGoalProgressPct());

                long dwellMs = still.getEndTimeDate() != null && still.getStartTimeDate() != null
                        ? still.getEndTimeDate().getTime() - still.getStartTimeDate().getTime() : 0;

                stillHolder.tvMicroScreenTime.setText("Goal Progress: " + UiFormatters.formatDurationMs(dwellMs)
                        + " / " + UiFormatters.formatDurationMs(stayHabit.getTargetDwellMs()));
                stillHolder.tvMicroUnlocks.setText("🔥 Streak: " + stayHabit.getStreakCount() + " Days");

                stillHolder.badgeZoneStatus.setVisibility(View.VISIBLE);
                stillHolder.badgeZoneStatus.setText(stayHabit.getHabitBadgeText());
                if (stayHabit.isGoalMet()) {
                    stillHolder.badgeZoneStatus.setTextColor(Color.parseColor("#1B5E20"));
                    stillHolder.badgeZoneStatus.setBackgroundColor(Color.parseColor("#E8F5E9"));
                } else {
                    stillHolder.badgeZoneStatus.setTextColor(Color.parseColor("#E65100"));
                    stillHolder.badgeZoneStatus.setBackgroundColor(Color.parseColor("#FFF3E0"));
                }
            } else {
                stillHolder.layoutRatioBarContainer.setVisibility(View.GONE);
                stillHolder.badgeZoneStatus.setVisibility(View.GONE);
            }

            final StillLocation finalStill = still;
            stillHolder.editBtn.setOnClickListener(v -> {
                if (editButtonClickListener != null) editButtonClickListener.onStillEditButtonClick(finalStill);
            });

        } else {
            MovementViewHolder movementHolder = (MovementViewHolder) holder;

            MovementActivity movement = null;
            MovementWithHabit movementHabit = null;

            if (item instanceof MovementWithHabit) {
                movementHabit = (MovementWithHabit) item;
                movement = movementHabit.getMovementActivity();
            } else if (item instanceof MovementWithUsage) {
                movement = ((MovementWithUsage) item).getMovementActivity();
            } else if (item instanceof MovementActivity) {
                movement = (MovementActivity) item;
            }

            if (movement == null) return;

            String type = movement.getActivityTypeName() != null ? movement.getActivityTypeName() : "Movement";
            movementHolder.itemTitle.setText(type);

            movementHolder.itemTimeRange.setText(UiFormatters.timeOnly(movement.getStartTimeDate()) + " — " +
                    UiFormatters.timeOnly(movement.getEndTimeDate()));
            movementHolder.itemDuration.setText(UiFormatters.duration(movement.getStartTimeDate(), movement.getEndTimeDate()));

            int[] colorAndIcon = getMovementColorAndIcon(type.toLowerCase());
            int colorRes = colorAndIcon[0];
            int iconRes = colorAndIcon[1];

            int movementColor = ContextCompat.getColor(movementHolder.itemView.getContext(), colorRes);
            if (movementHolder.lineDot.getBackground() != null) {
                DrawableCompat.setTint(movementHolder.lineDot.getBackground().mutate(), movementColor);
            }
            movementHolder.itemIcon.setImageResource(iconRes);
            movementHolder.itemIcon.setColorFilter(movementColor);
            movementHolder.iconContainer.setCardBackgroundColor(movementColor & 0x20FFFFFF);

            if (movementHabit != null) {
                movementHolder.layoutMovementScreenInfo.setVisibility(View.VISIBLE);
                long duration = movement.getEndTimeDate() != null && movement.getStartTimeDate() != null
                        ? movement.getEndTimeDate().getTime() - movement.getStartTimeDate().getTime() : 0;

                movementHolder.tvMovementScreenSummary.setText("Movement Target: "
                        + UiFormatters.formatDurationMs(duration) + " / " + UiFormatters.formatDurationMs(movementHabit.getTargetMovementMs()));
                movementHolder.badgeMovementSafety.setText(movementHabit.getHabitBadgeText());
                if (movementHabit.isGoalMet()) {
                    movementHolder.badgeMovementSafety.setTextColor(Color.parseColor("#1B5E20"));
                    movementHolder.badgeMovementSafety.setBackgroundColor(Color.parseColor("#E8F5E9"));
                } else {
                    movementHolder.badgeMovementSafety.setTextColor(Color.parseColor("#E65100"));
                    movementHolder.badgeMovementSafety.setBackgroundColor(Color.parseColor("#FFF3E0"));
                }
            } else {
                movementHolder.layoutMovementScreenInfo.setVisibility(View.GONE);
            }

            final MovementActivity finalMovement = movement;
            movementHolder.editBtn.setOnClickListener(v -> {
                if (editButtonClickListener != null) editButtonClickListener.onMovementEditButtonClick(finalMovement);
            });

            movementHolder.stopsContainer.removeAllViews();
            if (movement.getStops() != null && !movement.getStops().isEmpty()) {
                movementHolder.stopsContainer.setVisibility(View.VISIBLE);
                LayoutInflater inflater = LayoutInflater.from(movementHolder.itemView.getContext());
                for (StillLocation stop : movement.getStops()) {
                    View stopView = inflater.inflate(R.layout.item_nested_stop, movementHolder.stopsContainer, false);

                    TextView stopTitle = stopView.findViewById(R.id.stopTitle);
                    TextView stopDuration = stopView.findViewById(R.id.stopDuration);
                    TextView stopTimeRange = stopView.findViewById(R.id.stopTimeRange);
                    ImageView stopIcon = stopView.findViewById(R.id.stopIcon);
                    View btnLabelStop = stopView.findViewById(R.id.btnLabelStop);
                    TextView stopAddress = stopView.findViewById(R.id.stopAddress);
                    TextView stopCategory = stopView.findViewById(R.id.itemCategory);

                    String sTitle = stop.getPlaceName() != null ? stop.getPlaceName() : "Stationary";
                    stopTitle.setText(sTitle);

                    if (stop.getPlaceAddress() != null && !stop.getPlaceAddress().isEmpty()) {
                        stopAddress.setText(stop.getPlaceAddress());
                        stopAddress.setVisibility(View.VISIBLE);
                    } else {
                        stopAddress.setVisibility(View.GONE);
                    }

                    if (stop.getCategory() != null && !stop.getCategory().isEmpty()) {
                        stopCategory.setText(category(stop.getCategory()));
                        stopCategory.setVisibility(View.VISIBLE);
                    } else {
                        stopCategory.setVisibility(View.GONE);
                    }

                    stopDuration.setText(UiFormatters.duration(stop.getStartTimeDate(), stop.getEndTimeDate()));
                    stopTimeRange.setText(UiFormatters.timeOnly(stop.getStartTimeDate()) + " — " + UiFormatters.timeOnly(stop.getEndTimeDate()));

                    int stopIconXml = stop.getIcon() != null ? getStillIconRes(stop) : R.drawable.ic_still;
                    stopIcon.setImageResource(stopIconXml);

                    int stopColor = getStillColor(stop, stopView.getContext());
                    stopTitle.setTextColor(stopColor);
                    stopIcon.setColorFilter(stopColor);

                    stopView.setOnClickListener(v -> {
                        if (clickListener != null) clickListener.onItemClick(stop);
                    });

                    btnLabelStop.setOnClickListener(v -> {
                        if (editButtonClickListener != null) editButtonClickListener.onStillEditButtonClick(stop);
                    });

                    movementHolder.stopsContainer.addView(stopView);
                }
            } else {
                movementHolder.stopsContainer.setVisibility(View.GONE);
            }
        }
    }

    static class StillViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle, itemTimeRange, itemDuration, itemAddress, itemCategory;
        View lineDot;
        ImageView itemIcon;
        Button editBtn;
        CardView iconContainer;

        LinearLayout layoutRatioBarContainer;
        ProgressBar progressHabitGoal;
        TextView tvMicroScreenTime, tvMicroUnlocks, badgeZoneStatus;

        StillViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            lineDot = itemView.findViewById(R.id.lineDot);
            itemIcon = itemView.findViewById(R.id.itemIcon);
            editBtn = itemView.findViewById(R.id.editBtn);
            itemAddress = itemView.findViewById(R.id.itemAddress);
            itemCategory = itemView.findViewById(R.id.itemCategory);
            iconContainer = itemView.findViewById(R.id.iconContainer);

            layoutRatioBarContainer = itemView.findViewById(R.id.layout_ratio_bar_container);
            progressHabitGoal = itemView.findViewById(R.id.progress_habit_goal);
            tvMicroScreenTime = itemView.findViewById(R.id.tv_micro_screen_time);
            tvMicroUnlocks = itemView.findViewById(R.id.tv_micro_unlocks);
            badgeZoneStatus = itemView.findViewById(R.id.badge_zone_status);
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle, itemTimeRange, itemDuration;
        View lineDot;
        Button editBtn;
        ImageView itemIcon;
        LinearLayout stopsContainer;
        CardView iconContainer;

        LinearLayout layoutMovementScreenInfo;
        TextView tvMovementScreenSummary, badgeMovementSafety;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            lineDot = itemView.findViewById(R.id.lineDot);
            editBtn = itemView.findViewById(R.id.editBtn);
            itemIcon = itemView.findViewById(R.id.itemIcon);
            stopsContainer = itemView.findViewById(R.id.stopsContainer);
            iconContainer = itemView.findViewById(R.id.iconContainer);

            layoutMovementScreenInfo = itemView.findViewById(R.id.layout_movement_screen_info);
            tvMovementScreenSummary = itemView.findViewById(R.id.tv_movement_screen_summary);
            badgeMovementSafety = itemView.findViewById(R.id.badge_movement_safety);
        }
    }
}
