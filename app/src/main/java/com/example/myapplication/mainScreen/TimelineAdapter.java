package com.example.myapplication.mainScreen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
    private OnItemClickListener listener; // the action to perform when an item is clicked
    private OnItemLongClickListener longClickListener; // the action to perform when an item is clicked
    private OnLabelClickListener labelClickListener; // the action to perform when an item is clicked


    // Interface for handling clicks on timeline items

    public interface OnItemClickListener {
        void onItemClick(TimelineItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TimelineItem item);
    }

    public interface OnLabelClickListener {
        void onLabelClick(StillLocation still);
    }

    // functions to set what happens when an item is clicked

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longClickListener) {//TODO REMOVE
        this.longClickListener = longClickListener;
    }

    public void setOnLabelClickListener(OnLabelClickListener labelClickListener) {
        this.labelClickListener = labelClickListener;
    }

    public void submitList(List<TimelineItem> newItems) {
        // updates the timeline items to the new ones
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged(); // function to refresh ui, which apperantly isnt efficient so TODO
    }

    @Override
    public int getItemViewType(int position) {
        // Return view type based on the class of the item
        if (items.get(position) instanceof StillLocation) {
            return TYPE_STILL;
        } else {
            return TYPE_MOVEMENT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the corresponding layout for the view type
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
        // adds the data to view holder

        // Get the item at the current position
        TimelineItem item = items.get(position);

        // Setup click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(item);
                return true;
            }
            return false;
        });


        if (holder instanceof StillViewHolder) {
            //if item is still, show still template
            StillLocation still = (StillLocation) item;
            StillViewHolder stillHolder = (StillViewHolder) holder;

            String title;
            if (still.placeName != null) {
                title = still.placeName;
            } else {
                title = "Stationary";
            }

            // Title
            stillHolder.itemTitle.setText(title);

            // Coords
            if (still.placeCoords != null) {//TODO Remove coords
                stillHolder.itemCoords.setText(still.placeCoords);
            } else {
                stillHolder.itemCoords.setText(UiFormatters.decimal(still.lat) + ", " + UiFormatters.decimal(still.lng));
            }

            // Time Range
            stillHolder.itemTimeRange.setText(UiFormatters.timeOnly(still.startTimeDate) + " — " +
                    UiFormatters.timeOnly(still.endTimeDate));

            // Item Duration
            stillHolder.itemDuration.setText(UiFormatters.duration(still.startTimeDate, still.endTimeDate));

            // Color TODO CORRECT COLORS
            stillHolder.color.setBackgroundColor(ContextCompat.getColor(stillHolder.itemView.getContext(), R.color.activity_still));

            // Set icon based on activity
            int iconRes = R.drawable.ic_still;
            if (still.icon != null) {
                String icon = still.icon.toLowerCase();
                if (icon.contains("home")) iconRes = R.drawable.ic_home;
                else if (icon.contains("work")) iconRes = R.drawable.ic_work;
                else if (icon.contains("gym")) iconRes = R.drawable.ic_gym;
                else if (icon.contains("school")) iconRes = R.drawable.ic_school;
                else if (icon.contains("restaurant")) iconRes = R.drawable.ic_restaurant;
                else if (icon.contains("cafe") || icon.contains("coffee")) iconRes = R.drawable.ic_coffee;
            }
            stillHolder.itemIcon.setImageResource(iconRes);

            // Label place button
            stillHolder.btnLabel.setOnClickListener(v -> {
                if (labelClickListener != null) {
                    labelClickListener.onLabelClick(still);
                }
            });

            // change the button if it's already labeled
            if (still.placeName != null && !still.placeName.equals("Stationary")) {
                stillHolder.btnLabel.setText("Edit Label");
            } else {
                stillHolder.btnLabel.setText("Label Place");
            }

        } else {
            // If item is movement, show movement template
            MovementActivity movement = (MovementActivity) item;
            MovementViewHolder movementHolder = (MovementViewHolder) holder;

            String type;
            if (movement.activityType != null) {
                type = movement.activityType;
            } else {
                type = "Movement";
            }

            // Title
            movementHolder.itemTitle.setText(type);

            // Time Range
            movementHolder.itemTimeRange.setText(UiFormatters.timeOnly(movement.startTimeDate) + " — " +
                    UiFormatters.timeOnly(movement.endTimeDate));

            // Item Duration
            movementHolder.itemDuration.setText(UiFormatters.duration(movement.startTimeDate, movement.endTimeDate));

            movementHolder.itemSpeed.setVisibility(View.GONE);//TODO WHY IS THAT HERE

            // Set icon based on activity TODO CORRECT COLORS
            int colorRes = R.color.activity_walking;
            int iconRes = R.drawable.ic_walk;
            if (type.equalsIgnoreCase("Driving") || type.equalsIgnoreCase("IN_VEHICLE")) {
                colorRes = R.color.activity_vehicle;
                iconRes = R.drawable.ic_car;
            } else if (type.equalsIgnoreCase("WALKING") || type.equalsIgnoreCase("ON_FOOT") || type.equalsIgnoreCase("RUNNING") || type.equalsIgnoreCase("Walking")) {
                colorRes = R.color.activity_walking;
                iconRes = R.drawable.ic_walk;
            } else if (type.equalsIgnoreCase("Cycling") || type.equalsIgnoreCase("ON_BICYCLE")) {
                colorRes = R.color.activity_walking;
                iconRes = R.drawable.ic_bike;
            }
            movementHolder.color.setBackgroundColor(ContextCompat.getColor(movementHolder.itemView.getContext(), colorRes));
            movementHolder.itemIcon.setImageResource(iconRes);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StillViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle, itemCoords, itemTimeRange, itemDuration;
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
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle, itemSpeed, itemTimeRange, itemDuration;
        View color;
        android.widget.ImageView itemIcon;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemSpeed = itemView.findViewById(R.id.itemSpeed);
            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            color = itemView.findViewById(R.id.color);
            itemIcon = itemView.findViewById(R.id.itemIcon);
        }
    }
}
