package com.example.myapplication.ui.statistics.askTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TimelineChatSession {

    private static TimelineChatSession instance;
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private boolean isQueryInProgress = false;
    private int sessionVersion = 0;

    private TimelineChatSession() {
        chatHistory.add(new ChatMessage(
                UUID.randomUUID().toString(),
                "Hi! Ask me anything about your timeline history, places you've visited, or activity patterns.",
                false,
                System.currentTimeMillis(),
                new ArrayList<>()
        ));
    }

    public static synchronized TimelineChatSession getInstance() {
        if (instance == null) {
            instance = new TimelineChatSession();
        }
        return instance;
    }

    public synchronized List<ChatMessage> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }

    public synchronized void addMessage(ChatMessage message) {
        if (message != null) {
            chatHistory.add(message);
        }
    }

    public synchronized boolean isQueryInProgress() {
        return isQueryInProgress;
    }

    public synchronized void setQueryInProgress(boolean inProgress) {
        this.isQueryInProgress = inProgress;
    }

    public synchronized int getSessionVersion() {
        return sessionVersion;
    }

    public synchronized void clearHistory() {
        sessionVersion++;
        isQueryInProgress = false;
        chatHistory.clear();
        chatHistory.add(new ChatMessage(
                UUID.randomUUID().toString(),
                "Hi! Ask me anything about your timeline history, places you've visited, or activity patterns.",
                false,
                System.currentTimeMillis(),
                new ArrayList<>()
        ));
    }
}
