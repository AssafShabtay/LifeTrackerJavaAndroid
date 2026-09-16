package com.example.myapplication.mainScreen.dashboardScreen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.mainScreen.dashboardScreen.model.ContextualInsight;

import java.util.ArrayList;
import java.util.List;

public class InsightCardAdapter extends RecyclerView.Adapter<InsightCardAdapter.ViewHolder> {

    private final List<ContextualInsight> items = new ArrayList<>();

    public void setItems(List<ContextualInsight> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_insight_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContextualInsight item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvDescription.setText(item.getDescription());
        holder.tvMetric.setText(item.getMetric());

        int iconRes = R.drawable.ic_check_circle;
        if (item.getType() == ContextualInsight.Type.WARNING) {
            iconRes = R.drawable.ic_dark_mode;
        }

        holder.ivIcon.setImageResource(iconRes);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvDescription;
        TextView tvMetric;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivInsightIcon);
            tvTitle = itemView.findViewById(R.id.tvInsightTitle);
            tvDescription = itemView.findViewById(R.id.tvInsightDescription);
            tvMetric = itemView.findViewById(R.id.tvInsightMetric);
        }
    }
}
