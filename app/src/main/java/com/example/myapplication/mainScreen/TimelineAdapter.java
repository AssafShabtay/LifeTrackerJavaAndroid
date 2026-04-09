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

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof StillLocation) {
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

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem item = items.get(position);
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
            StillLocation still = (StillLocation) item;
            StillViewHolder h = (StillViewHolder) holder;
            
            String title = still.placeName != null ? still.placeName : "Stationary";
            h.tvTitle.setText(title);
            
            if (still.placeAddress != null) {
                h.tvCoords.setText(still.placeAddress);
            } else {
                h.tvCoords.setText(UiFormatters.decimal(still.lat) + ", " + UiFormatters.decimal(still.lng));
            }
            
            h.tvTimeRange.setText(UiFormatters.timeOnly(still.startTimeDate) + " — " +
                    UiFormatters.timeOnly(still.endTimeDate));
            h.tvDuration.setText(UiFormatters.duration(still.startTimeDate, still.endTimeDate));
            h.indicator.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), R.color.activity_still));

            h.btnLabel.setOnClickListener(v -> {
                if (labelClickListener != null) {
                    labelClickListener.onLabelClick(still);
                }
            });
            
            // Hide the button if it's already labeled
            if (still.placeName != null && !still.placeName.equals("Stationary")) {
                h.btnLabel.setText("Edit Label");
            } else {
                h.btnLabel.setText("Label Place");
            }
        } else {
            MovementActivity movement = (MovementActivity) item;
            MovementViewHolder h = (MovementViewHolder) holder;
            String type = movement.activityType != null ? movement.activityType : "Movement";
            h.tvTitle.setText(type);
            h.tvTimeRange.setText(UiFormatters.timeOnly(movement.startTimeDate) + " — " +
                    UiFormatters.timeOnly(movement.endTimeDate));
            h.tvDuration.setText(UiFormatters.duration(movement.startTimeDate, movement.endTimeDate));
            h.tvSpeed.setVisibility(View.GONE);
            
            int colorRes = R.color.activity_walking;
            if (type.equalsIgnoreCase("Driving") || type.equalsIgnoreCase("IN_VEHICLE")) {
                colorRes = R.color.activity_vehicle;
            } else if (type.equalsIgnoreCase("WALKING") || type.equalsIgnoreCase("ON_FOOT") || type.equalsIgnoreCase("RUNNING") || type.equalsIgnoreCase("Walking")) {
                colorRes = R.color.activity_walking;
            }
            h.indicator.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), colorRes));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StillViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCoords, tvTimeRange, tvDuration;
        View indicator;
        Button btnLabel;

        StillViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvCoords = itemView.findViewById(R.id.tvCoords);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            indicator = itemView.findViewById(R.id.indicator);
            btnLabel = itemView.findViewById(R.id.btnLabel);
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSpeed, tvTimeRange, tvDuration;
        View indicator;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSpeed = itemView.findViewById(R.id.tvSpeed);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            indicator = itemView.findViewById(R.id.indicator);
        }
    }
}
