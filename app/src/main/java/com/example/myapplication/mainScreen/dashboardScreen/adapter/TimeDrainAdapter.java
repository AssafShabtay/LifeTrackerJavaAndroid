package com.example.myapplication.mainScreen.dashboardScreen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.mainScreen.dashboardScreen.model.TimeDrainItem;

import java.util.ArrayList;
import java.util.List;

public class TimeDrainAdapter extends RecyclerView.Adapter<TimeDrainAdapter.ViewHolder> {

    private final List<TimeDrainItem> items = new ArrayList<>();

    public void setItems(List<TimeDrainItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_time_drain, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeDrainItem item = items.get(position);
        holder.tvAppName.setText(item.getAppName());
        holder.tvLocation.setText("Used at " + item.getFocusPlaceName() + " (Focus Place)");

        long minutes = item.getScreenTimeMs() / (60 * 1000);
        holder.tvDuration.setText(minutes + "m");

        if (item.getAppIcon() != null) {
            holder.ivAppIcon.setImageDrawable(item.getAppIcon());
        } else {
            holder.ivAppIcon.setImageResource(R.drawable.ic_dark_mode);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvLocation;
        TextView tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivDrainAppIcon);
            tvAppName = itemView.findViewById(R.id.tvDrainAppName);
            tvLocation = itemView.findViewById(R.id.tvDrainLocation);
            tvDuration = itemView.findViewById(R.id.tvDrainDuration);
        }
    }
}
