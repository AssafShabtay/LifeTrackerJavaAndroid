package com.example.myapplication.ui.statistics.askTimeline;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {

    public interface OnDateLinkClickListener {
        void onDateLinkClick(DateLink dateLink);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final OnDateLinkClickListener dateLinkClickListener;

    public ChatMessageAdapter(OnDateLinkClickListener dateLinkClickListener) {
        this.dateLinkClickListener = dateLinkClickListener;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message, dateLinkClickListener);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final View layoutUserContainer;
        private final TextView tvUserMessage;

        private final View layoutAssistantContainer;
        private final TextView tvAssistantMessage;
        private final LinearLayout layoutReferencedDatesHeader;
        private final ChipGroup chipGroupReferencedDates;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUserContainer = itemView.findViewById(R.id.layout_user_container);
            tvUserMessage = itemView.findViewById(R.id.tv_user_message);

            layoutAssistantContainer = itemView.findViewById(R.id.layout_assistant_container);
            tvAssistantMessage = itemView.findViewById(R.id.tv_assistant_message);
            layoutReferencedDatesHeader = itemView.findViewById(R.id.layout_referenced_dates_header);
            chipGroupReferencedDates = itemView.findViewById(R.id.chip_group_referenced_dates);
        }

        public void bind(ChatMessage message, OnDateLinkClickListener dateLinkClickListener) {
            if (message.isUser()) {
                layoutUserContainer.setVisibility(View.VISIBLE);
                layoutAssistantContainer.setVisibility(View.GONE);
                tvUserMessage.setText(message.getContent());
            } else {
                layoutUserContainer.setVisibility(View.GONE);
                layoutAssistantContainer.setVisibility(View.VISIBLE);
                tvAssistantMessage.setText(message.getContent());

                List<DateLink> dates = message.getReferencedDates();
                chipGroupReferencedDates.removeAllViews();

                if (dates != null && !dates.isEmpty()) {
                    layoutReferencedDatesHeader.setVisibility(View.VISIBLE);
                    Context context = itemView.getContext();

                    for (DateLink dateLink : dates) {
                        Chip chip = new Chip(context);
                        chip.setText(dateLink.getDisplayLabel());
                        chip.setChipIconResource(R.drawable.ic_calendar_today);
                        chip.setChipIconVisible(true);
                        chip.setClickable(true);
                        chip.setFocusable(true);
                        chip.setOnClickListener(v -> {
                            if (dateLinkClickListener != null) {
                                dateLinkClickListener.onDateLinkClick(dateLink);
                            }
                        });
                        chipGroupReferencedDates.addView(chip);
                    }
                } else {
                    layoutReferencedDatesHeader.setVisibility(View.GONE);
                }
            }
        }
    }
}
