package com.example.myapplication.ui.statistics.efficiency;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.List;

public class InsightCardAdapter extends RecyclerView.Adapter<InsightCardAdapter.ViewHolder> {

    private final List<InsightCardData> items;

    public InsightCardAdapter(List<InsightCardData> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dashboard_insight_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InsightCardData data = items.get(position);
        holder.tvTitle.setText(data.getTitle());
        holder.tvMessage.setText(data.getMessage());

        if ("win".equalsIgnoreCase(data.getIconType())) {
            holder.ivIcon.setImageResource(R.drawable.ic_check_circle);
        } else if ("alert".equalsIgnoreCase(data.getIconType())) {
            holder.ivIcon.setImageResource(R.drawable.ic_still);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_location_on);
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvMessage;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_insight_icon);
            tvTitle = itemView.findViewById(R.id.tv_insight_title);
            tvMessage = itemView.findViewById(R.id.tv_insight_message);
        }
    }
}
