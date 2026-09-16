package com.example.myapplication.mainScreen.dashboardScreen.model;

public class ContextualInsight {
    public enum Type {
        POSITIVE,
        WARNING,
        INFO
    }

    private final String title;
    private final String description;
    private final Type type;
    private final String metric;

    public ContextualInsight(String title, String description, Type type, String metric) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.metric = metric;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public String getMetric() {
        return metric;
    }
}
