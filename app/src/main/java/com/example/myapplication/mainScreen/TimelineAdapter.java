package com.example.myapplication.mainScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getStillColor;
import static com.example.myapplication.helpers.ColorAndIcons.getStillIconRes;

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
    private OnEditButtonClickListener labelClickListener;

    public interface OnItemClickListener {
        void onItemClick(TimelineItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TimelineItem item);
    }

    public interface OnEditButtonClickListener {
        void onLabelClick(StillLocation still);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnEditButtonClickListener(OnEditButtonClickListener labelClickListener) {
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

        if (holder instanceof StillViewHolder) {
            // --------------- Handle still items ---------------
            StillLocation still = (StillLocation) item;
            StillViewHolder stillHolder = (StillViewHolder) holder;

            String title = (still.placeName != null) ? still.placeName : "Stationary";

            if (still.isStop && !title.startsWith("Stop • ")) {
                title = "Stop • " + title;
            }
            stillHolder.itemTitle.setText(title);

            // Address
            if (still.placeAddress != null && !still.placeAddress.isEmpty()) {
                stillHolder.itemAddress.setText(still.placeAddress);
                stillHolder.itemAddress.setVisibility(View.VISIBLE);
            } else {
                stillHolder.itemAddress.setVisibility(View.GONE); // Hide address if no address
            }

            // Category
            if (still.category != null && !still.category.isEmpty()) {
                stillHolder.itemCategory.setText(still.category);
                stillHolder.itemCategory.setVisibility(View.VISIBLE);
            } else {
                stillHolder.itemCategory.setVisibility(View.GONE);
            }

            stillHolder.itemTimeRange.setText(UiFormatters.timeOnly(still.startTimeDate) + " — " +
                    UiFormatters.timeOnly(still.endTimeDate));

            stillHolder.itemDuration.setText(UiFormatters.duration(still.startTimeDate, still.endTimeDate));


            int stillColor = getStillColor(still, stillHolder.itemView.getContext());

            if (stillHolder.color.getBackground() != null) {
                DrawableCompat.setTint(stillHolder.color.getBackground().mutate(), stillColor);
            }

            // Set icon
            int iconXml = R.drawable.ic_still;
            if (still.icon != null) {
                iconXml = getStillIconRes(still);
            }
            stillHolder.itemIcon.setImageResource(iconXml);

            // Apply colors
            stillHolder.itemTitle.setTextColor(stillColor);
            stillHolder.itemIcon.setColorFilter(stillColor);
            stillHolder.iconContainer.setCardBackgroundColor(stillColor & 0x20FFFFFF);

            stillHolder.btnLabel.setOnClickListener(v -> {
                if (labelClickListener != null) labelClickListener.onLabelClick(still);
            });

        } else {
            // --------------- Handle movement items ---------------
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
            movementHolder.itemIcon.setColorFilter(moveColor);
            movementHolder.iconContainer.setCardBackgroundColor(moveColor & 0x20FFFFFF);



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
                    if (stop.placeAddress != null && !stop.placeAddress.isEmpty()) {
                        stopAddress.setText(stop.placeAddress);
                        stopAddress.setVisibility(View.VISIBLE);
                    } else {
                        stopAddress.setVisibility(View.GONE);
                    }

                    // Set stop icon based on category
                    int stopIconXml = R.drawable.ic_still;
                    if (stop.icon != null) {;
                        stopIconXml = getStillIconRes(stop);

                    }
                    stopIcon.setImageResource(stopIconXml);

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
        TextView itemTitle, itemTimeRange, itemDuration, itemAddress, itemCategory;
        View color;
        android.widget.ImageView itemIcon;
        Button btnLabel;
        CardView iconContainer;

        StillViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            color = itemView.findViewById(R.id.color);
            itemIcon = itemView.findViewById(R.id.itemIcon);
            btnLabel = itemView.findViewById(R.id.btnLabel);
            itemAddress = itemView.findViewById(R.id.itemAddress);
            itemCategory = itemView.findViewById(R.id.itemCategory);
            iconContainer = itemView.findViewById(R.id.iconContainer);
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle;
        TextView itemTimeRange;
        TextView itemDuration;
        View color;
        android.widget.ImageView itemIcon;
        LinearLayout stopsContainer;
        CardView iconContainer;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);

            itemTimeRange = itemView.findViewById(R.id.itemTimeRange);
            itemDuration = itemView.findViewById(R.id.itemDuration);
            color = itemView.findViewById(R.id.color);
            itemIcon = itemView.findViewById(R.id.itemIcon);
            stopsContainer = itemView.findViewById(R.id.stopsContainer);
            iconContainer = itemView.findViewById(R.id.iconContainer);
        }
    }
}
