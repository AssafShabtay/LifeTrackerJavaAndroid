package com.example.myapplication.mainScreen.statisticsScreen.llm;

public class LlmResponse {
    private final String habit;
    private final String anomaly;

    public LlmResponse(String habit, String anomaly) {
        this.habit = habit;
        this.anomaly = anomaly;
    }

    public String getHabit() {
        return habit;
    }

    public String getAnomaly() {
        return anomaly;
    }
}