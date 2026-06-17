package com.example.myapplication.mainScreen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.TimelineItem;
import com.example.myapplication.helpers.UiFormatters;

import java.util.ArrayList;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_STILL = 0;
    private static final int TYPE_MOVEMENT = 1;

    private final List<TimelineItem> items = new ArrayList<>();
    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;
    private OnLabelClickListener labelClickListener;

    public interface OnItemClickListener {
        void onItemClick(TimelineItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TimelineItem item);
    }

    public interface OnLabelClickListener {
        void onLabelClick(StillLocation still);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void setOnLabelClickListener(OnLabelClickListener labelClickListener) {
        this.labelClickListener = labelClickListener;
    }

    public void submitList(List<TimelineItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<TimelineItem> getItems() {
        return items;
    }

    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof StillLocation) ? TYPE_STILL : TYPE_MOVEMENT;
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

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem item = items.get(position);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(item);
                return true;
            }
            return false;
        });

        if (holder instanceof StillViewHolder) {
            StillLocation still = (StillLocation) item;
            StillViewHolder stillHolder = (StillViewHolder) holder;

            String title = (still.placeName != null) ? still.placeName : "Stationary";
            // Check for stops
            //boolean isStop = still.wasSupposedToBeActivity != null; TODO remove

            if (still.isStop && !title.startsWith("Stop • ")) {
                title = "Stop • " + title;
            }
            stillHolder.itemTitle.setText(title);

            // Address (priority)
            if (still.address != null && !still.address.isEmpty()) {
                stillHolder.itemAddress.setText(still.address);
                stillHolder.itemAddress.setVisibility(View.VISIBLE);
                stillHolder.itemCoords.setVisibility(View.GONE); // Hide coords if address exists
            } else {
                stillHolder.itemAddress.setVisibility(View.GONE); // Hide address if no address
                // Coords
                if (still.placeCoords != null) {
                    stillHolder.itemCoords.setText(still.placeCoords);
                    stillHolder.itemCoords.setVisibility(View.VISIBLE);
                } else if (still.lat != null && still.lng != null) {
                    stillHolder.itemCoords.setText(UiFormatters.decimal(still.lat) + ", " + UiFormatters.decimal(still.lng));
                    stillHolder.itemCoords.setVisibility(View.VISIBLE);
                } else {
                    stillHolder.itemCoords.setVisibility(View.GONE);
                }
            }

            stillHolder.itemTimeRange.setText(UiFormatters.timeOnly(still.startTimeDate) + " — " +
                    UiFormatters.timeOnly(still.endTimeDate));

            stillHolder.itemDuration.setText(UiFormatters.duration(still.startTimeDate, still.endTimeDate));

            // Tint the dot
            int stillColor;
            if (still.color != null) {
                stillColor = still.color;
            } else {
                stillColor = ContextCompat.getColor(stillHolder.itemView.getContext(),
                        still.isStop ? R.color.activity_stop : R.color.activity_still);
            }
            
            if (stillHolder.color.getBackground() != null) {
                DrawableCompat.setTint(stillHolder.color.getBackground().mutate(), stillColor);
            }

            // Set icon
            int iconRes = R.drawable.ic_still;
            if (still.icon != null) {
                String icon = still.icon.toLowerCase();
                if (icon.contains("home")) iconRes = R.drawable.ic_home;
                else if (icon.contains("work")) iconRes = R.drawable.ic_work;
                else if (icon.contains("gym")) iconRes = R.drawable.ic_gym;
                else if (icon.contains("school")) iconRes = R.drawable.ic_school;
                else if (icon.contains("restaurant") || icon.contains("eat")) iconRes = R.drawable.ic_restaurant;
                else if (icon.contains("cafe") || icon.contains("coffee")) iconRes = R.drawable.ic_coffee;
            }
            stillHolder.itemIcon.setImageResource(iconRes);

            // Highlight stops or custom colors
            if (still.isStop || still.color != null) {
                int textColor = (still.color != null) ? still.color : ContextCompat.getColor(stillHolder.itemView.getContext(), R.color.activity_stop);
                stillHolder.itemTitle.setTextColor(textColor);
                stillHolder.itemIcon.setColorFilter(textColor);
            } else {
                stillHolder.itemTitle.setTextColor(ContextCompat.getColor(stillHolder.itemView.getContext(), R.color.on_surface));
                stillHolder.itemIcon.clearColorFilter();
            }

            stillHolder.btnLabel.setOnClickListener(v -> {
                if (labelClickListener != null) labelClickListener.onLabelClick(still);
            });

        } else {
            MovementActivity movement = (MovementActivity) item;
            MovementViewHolder movementHolder = (MovementViewHolder) holder;

            String type = (movement.activityTypeName != null) ? movement.activityTypeName : "Movement";
            movementHolder.itemTitle.setText(type);

            movementHolder.itemTimeRange.setText(UiFormatters.timeOnly(movement.startTimeDate) + " — " +
                    UiFormatters.timeOnly(movement.endTimeDate));

            movementHolder.itemDuration.setText(UiFormatters.duration(movement.startTimeDate, movement.endTimeDate));

            // Activity type logic
            int colorRes = R.color.activity_walking;
            int iconRes = R.drawable.ic_walk;

            String t = type.toLowerCase();
            if (t.contains("driving") || t.contains("vehicle")) {
                colorRes = R.color.activity_vehicle;
                iconRes = R.drawable.ic_car;
            } else if (t.contains("running")) {
                colorRes = R.color.activity_running;
                iconRes = R.drawable.ic_walk; // Use walk icon for running as well
            } else if (t.contains("cycling") || t.contains("bicycle")) {
                colorRes = R.color.activity_cycling;
                iconRes = R.drawable.ic_bike;
            } else if (t.contains("walking") || t.contains("foot")) {
                colorRes = R.color.activity_walking;
                iconRes = R.drawable.ic_walk;
            }

            int moveColor = ContextCompat.getColor(movementHolder.itemView.getContext(), colorRes);
            if (movementHolder.color.getBackground() != null) {
                DrawableCompat.setTint(movementHolder.color.getBackground().mutate(), moveColor);
            }
            movementHolder.itemIcon.setImageResource(iconRes);

            // Speed logic
            if (movement.activityTypeName != null && (t.contains("driving") || t.contains("cycling") || t.contains("running"))) {
                movementHolder.itemSpeed.setVisibility(View.VISIBLE);
                movementHolder.itemSpeed.setText("Movement tracking active");
            } else {
                movementHolder.itemSpeed.setVisibility(View.GONE);
            }

            // Handle nested stops
            movementHolder.stopsContainer.removeAllViews();
            if (movement.stops != null && !movement.stops.isEmpty()) {
                movementHolder.stopsContainer.setVisibility(View.VISIBLE);
                LayoutInflater inflater = LayoutInflater.from(movementHolder.itemView.getContext());
                for (StillLocation stop : movement.stops) {
                    View stopView = inflater.inflate(R.layout.item_nested_stop, movementHolder.stopsContainer, false);

                    TextView stopTitle = stopView.findViewById(R.id.stopTitle);
                    TextView stopDuration = stopView.findViewById(R.id.stopDuration);
                    TextView stopTimeRange = stopView.findViewById(R.id.stopTimeRange);
                    android.widget.ImageView stopIcon = stopView.findViewById(R.id.stopIcon);
                    View btnLabelStop = stopView.findViewById(R.id.btnLabelStop);
                    TextView stopAddress = stopView.findViewById(R.id.stopAddress);

                    String sTitle = (stop.placeName != null) ? stop.placeName : "Stationary";
                    if (!sTitle.startsWith("Stop • ")) {
                        sTitle = "Stop • " + sTitle;
                    }
                    stopTitle.setText(sTitle);
                    stopDuration.setText(UiFormatters.duration(stop.startTimeDate, stop.endTimeDate));
                    stopTimeRange.setText(UiFormatters.timeOnly(stop.startTimeDate) + " — " + UiFormatters.timeOnly(stop.endTimeDate));

                    // Set stop address
                    if (stop.address != null && !stop.address.isEmpty()) {
                        stopAddress.setText(stop.address);
                        stopAddress.setVisibility(View.VISIBLE);
                    } else {
                        stopAddress.setVisibility(View.GONE);
                    }

                    // Set stop icon based on category
                    int sIconRes = R.drawable.ic_still;
                    if (stop.icon != null) {
                        String icon = stop.icon.toLowerCase();
                        if (icon.contains("home")) sIconRes = R.drawable.ic_home;
                        else if (icon.contains("work")) sIconRes = R.drawable.ic_work;
                        else if (icon.contains("gym")) sIconRes = R.drawable.ic_gym;
                        else if (icon.contains("school")) sIconRes = R.drawable.ic_school;
                        else if (icon.contains("restaurant") || icon.contains("eat")) sIconRes = R.drawable.ic_restaurant;
                        else if (icon.contains("cafe") || icon.contains("coffee")) sIconRes = R.drawable.ic_coffee;
                    }
                    stopIcon.setImageResource(sIconRes);

                    // Apply custom color to stop if exists
                    if (stop.color != null) {
                        stopTitle.setTextColor(stop.color);
                        stopIcon.setColorFilter(stop.color);
                    }

                    stopView.setOnClickListener(v -> {
                        if (listener != null) listener.onItemClick(stop);
                    });

                    btnLabelStop.setOnClickListener(v -> {
                        if (labelClickListener != null) labelClickListener.onLabelClick(stop);
                    });

                    movementHolder.stopsContainer.addView(stopView);
                }
            } else {
                movementHolder.stopsContainer.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StillViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle, itemCoords, itemTimeRange, itemDuration, itemAddress;
        View color;
        android.widget.ImageView itemIcon;
        Button btnLabel;

        StillViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemCoords = itemView.findViewById(R.id.itemCoords);
            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            color = itemView.findViewById(R.id.color);
            itemIcon = itemView.findViewById(R.id.itemIcon);
            btnLabel = itemView.findViewById(R.id.btnLabel);
            itemAddress = itemView.findViewById(R.id.itemAddress);
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle, itemSpeed, itemTimeRange, itemDuration;
        View color;
        android.widget.ImageView itemIcon;
        LinearLayout stopsContainer;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemSpeed = itemView.findViewById(R.id.itemSpeed);
            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            color = itemView.findViewById(R.id.color);
            itemIcon = itemView.findViewById(R.id.itemIcon);
            stopsContainer = itemView.findViewById(R.id.stopsContainer);
        }
    }
}