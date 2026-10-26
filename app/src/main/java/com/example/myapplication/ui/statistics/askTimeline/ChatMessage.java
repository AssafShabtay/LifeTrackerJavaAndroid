package com.example.myapplication.ui.statistics.askTimeline;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {
    private final String id;
    private final String content;
    private final boolean isUser;
    private final long timestamp;
    private final List<DateLink> referencedDates;

    public ChatMessage(String id, String content, boolean isUser, long timestamp, List<DateLink> referencedDates) {
        this.id = id;
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.referencedDates = referencedDates != null ? referencedDates : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<DateLink> getReferencedDates() {
        return referencedDates;
    }
}
