package com.example.myapplication.databaseviewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.db.MovementActivity;
import com.example.myapplication.db.StillLocation;
import com.example.myapplication.db.TimelineItem;
import com.example.myapplication.helpers.UiFormatters;

import java.util.ArrayList;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_STILL = 0;
    private static final int TYPE_MOVEMENT = 1;

    private final List<TimelineItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TimelineItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
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

        if (holder instanceof StillViewHolder) {
            StillLocation still = (StillLocation) item;
            StillViewHolder h = (StillViewHolder) holder;
            h.tvTitle.setText("Still Session #" + still.id);
            h.tvCoords.setText("Lat: " + UiFormatters.decimal(still.lat) +
                    "   Lng: " + UiFormatters.decimal(still.lng));
            h.tvTimeRange.setText(UiFormatters.dateTime(still.startTimeDate) + " → " +
                    UiFormatters.dateTime(still.endTimeDate));
            h.tvDuration.setText("Duration: " + UiFormatters.duration(still.startTimeDate, still.endTimeDate));
        } else {
            MovementActivity movement = (MovementActivity) item;
            MovementViewHolder h = (MovementViewHolder) holder;
            h.tvTitle.setText(movement.activityType != null ? movement.activityType : "Movement");
            h.tvTimeRange.setText(UiFormatters.dateTime(movement.startTimeDate) + " → " +
                    UiFormatters.dateTime(movement.endTimeDate));
            h.tvDuration.setText("Duration: " + UiFormatters.duration(movement.startTimeDate, movement.endTimeDate));
            h.tvSpeed.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StillViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCoords, tvTimeRange, tvDuration;

        StillViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvCoords = itemView.findViewById(R.id.tvCoords);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSpeed, tvTimeRange, tvDuration;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSpeed = itemView.findViewById(R.id.tvSpeed);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }
}
