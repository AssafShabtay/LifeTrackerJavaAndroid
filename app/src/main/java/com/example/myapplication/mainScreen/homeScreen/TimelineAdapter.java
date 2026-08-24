package com.example.myapplication.mainScreen.homeScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getMovementColorAndIcon;
import static com.example.myapplication.helpers.ColorAndIcons.getStillColor;
import static com.example.myapplication.helpers.ColorAndIcons.getStillIconRes;
import static com.example.myapplication.helpers.UiFormatters.category;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
import com.example.myapplication.database.StillLocation;
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
            if (oldItem instanceof StillLocation && newItem instanceof StillLocation) {
                return oldItem.getId() == newItem.getId();
            } else if (oldItem instanceof MovementActivity && newItem instanceof MovementActivity) {
                return oldItem.getId() == newItem.getId();
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(@NonNull TimelineItem oldItem, @NonNull TimelineItem newItem) {
            return oldItem.equals(newItem);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position) instanceof StillLocation) return TYPE_STILL;
        else return TYPE_MOVEMENT;
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
            // --------------- Handle still items ---------------
            StillLocation still = (StillLocation) item;
            StillViewHolder stillHolder = (StillViewHolder) holder;

            // Name
            String title = "Stationary";
            if (still.getPlaceName() != null) title = still.getPlaceName();
            stillHolder.itemTitle.setText(title);

            // Address
            if (still.getPlaceAddress() != null && !still.getPlaceAddress().isEmpty()) {
                stillHolder.itemAddress.setText(still.getPlaceAddress());
                stillHolder.itemAddress.setVisibility(View.VISIBLE);
            } else {
                stillHolder.itemAddress.setVisibility(View.GONE); // Hide address if no address
            }

            // Category
            if (still.getCategory() != null && !still.getCategory().isEmpty()) {
                stillHolder.itemCategory.setText(category(still.getCategory()));
                stillHolder.itemCategory.setVisibility(View.VISIBLE);
            } else {
                stillHolder.itemCategory.setVisibility(View.GONE);
            }

            // Time range and duration
            stillHolder.itemTimeRange.setText(UiFormatters.timeOnly(still.getStartTimeDate()) + " — " +
                    UiFormatters.timeOnly(still.getEndTimeDate()));

            stillHolder.itemDuration.setText(UiFormatters.duration(still.getStartTimeDate(), still.getEndTimeDate()));

            // Icon
            int iconXml = R.drawable.ic_still;
            if (still.getIcon() != null) {
                iconXml = getStillIconRes(still);
            }
            stillHolder.itemIcon.setImageResource(iconXml);

            // Apply colors
            int stillColor = getStillColor(still, stillHolder.itemView.getContext());
            stillHolder.itemTitle.setTextColor(stillColor);
            stillHolder.itemIcon.setColorFilter(stillColor);
            stillHolder.iconContainer.setCardBackgroundColor(stillColor & 0x20FFFFFF);

            // The dot on the left line
            if (stillHolder.lineDot.getBackground() != null) {
                DrawableCompat.setTint(stillHolder.lineDot.getBackground().mutate(), stillColor);
            }

            // Click listener
            stillHolder.editBtn.setOnClickListener(v -> {
                if (editButtonClickListener != null) editButtonClickListener.onStillEditButtonClick(still);
            });

        } else {
            // --------------- Handle movement items ---------------
            MovementActivity movement = (MovementActivity) item;
            MovementViewHolder movementHolder = (MovementViewHolder) holder;

            // Name
            String type = "Movement";
            if (movement.getActivityTypeName() != null) type = movement.getActivityTypeName();
            movementHolder.itemTitle.setText(type);


            // Time range and duration
            movementHolder.itemTimeRange.setText(UiFormatters.timeOnly(movement.getStartTimeDate()) + " — " +
                    UiFormatters.timeOnly(movement.getEndTimeDate()));

            movementHolder.itemDuration.setText(UiFormatters.duration(movement.getStartTimeDate(), movement.getEndTimeDate()));

            // Geet movement color and icon
            int[] colorAndIcon = getMovementColorAndIcon(type.toLowerCase());
            int colorRes = colorAndIcon[0];
            int iconRes = colorAndIcon[1];

            // Color and icon
            int movementColor = ContextCompat.getColor(movementHolder.itemView.getContext(), colorRes);
            if (movementHolder.lineDot.getBackground() != null) {
                DrawableCompat.setTint(movementHolder.lineDot.getBackground().mutate(), movementColor);
            }
            movementHolder.itemIcon.setImageResource(iconRes);
            movementHolder.itemIcon.setColorFilter(movementColor);
            movementHolder.iconContainer.setCardBackgroundColor(movementColor & 0x20FFFFFF);

            movementHolder.editBtn.setOnClickListener(v -> {
                if (editButtonClickListener != null) editButtonClickListener.onMovementEditButtonClick(movement);
            });
            // Handle nested stops
            movementHolder.stopsContainer.removeAllViews();
            if (movement.getStops() != null && !movement.getStops().isEmpty()) {
                movementHolder.stopsContainer.setVisibility(View.VISIBLE);
                LayoutInflater inflater = LayoutInflater.from(movementHolder.itemView.getContext());
                for (StillLocation stop : movement.getStops()) {
                    View stopView = inflater.inflate(R.layout.item_nested_stop, movementHolder.stopsContainer, false);

                    TextView stopTitle = stopView.findViewById(R.id.stopTitle);
                    TextView stopDuration = stopView.findViewById(R.id.stopDuration);
                    TextView stopTimeRange = stopView.findViewById(R.id.stopTimeRange);
                    android.widget.ImageView stopIcon = stopView.findViewById(R.id.stopIcon);
                    View btnLabelStop = stopView.findViewById(R.id.btnLabelStop);
                    TextView stopAddress = stopView.findViewById(R.id.stopAddress);
                    TextView stopCategory = stopView.findViewById(R.id.itemCategory);

                    // Name
                    String sTitle = "Stationary";
                    if (stop.getPlaceName() != null) sTitle = stop.getPlaceName();
                    stopTitle.setText(sTitle);

                    // Address
                    if (stop.getPlaceAddress() != null && !stop.getPlaceAddress().isEmpty()) {
                        stopAddress.setText(stop.getPlaceAddress());
                        stopAddress.setVisibility(View.VISIBLE);
                    } else {
                        stopAddress.setVisibility(View.GONE);
                    }

                    // Category
                    if (stop.getCategory() != null && !stop.getCategory().isEmpty()) {
                        stopCategory.setText(category(stop.getCategory()));
                        stopCategory.setVisibility(View.VISIBLE);
                    } else {
                        stopCategory.setVisibility(View.GONE);
                    }

                    // Time range and duration
                    stopDuration.setText(UiFormatters.duration(stop.getStartTimeDate(), stop.getEndTimeDate()));
                    stopTimeRange.setText(UiFormatters.timeOnly(stop.getStartTimeDate()) + " — " + UiFormatters.timeOnly(stop.getEndTimeDate()));

                    // Icon
                    int stopIconXml = R.drawable.ic_still;
                    if (stop.getIcon() != null) {
                        stopIconXml = getStillIconRes(stop);
                    }
                    stopIcon.setImageResource(stopIconXml);

                    //Color
                    int stopColor = getStillColor(stop, stopView.getContext());
                    stopTitle.setTextColor(stopColor);
                    stopIcon.setColorFilter(stopColor);

                    // Listeners
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
        android.widget.ImageView itemIcon;
        Button editBtn;
        CardView iconContainer;

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
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle;
        TextView itemTimeRange;
        TextView itemDuration;
        View lineDot;
        Button editBtn;
        android.widget.ImageView itemIcon;
        LinearLayout stopsContainer;
        CardView iconContainer;

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
        }
    }
}