package com.example.myapplication.ui.statistics.askTimeline;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.data.db.ActivityDao;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.MovementActivity;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.util.ErrorLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.TextPart;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AskTimelineLlmClient {

    private static final String TAG = "AskTimelineLlmClient";
    private static final Pattern DATE_PATTERN = Pattern.compile("\\[Date:\\s*(\\d{4}-\\d{2}-\\d{2})]");

    public static CompletableFuture<ChatMessage> askTimeline(Context context, String userQuery) {
        CompletableFuture<ChatMessage> future = new CompletableFuture<>();

        Context appContext = context.getApplicationContext();
        LifeTrackerApp app = (LifeTrackerApp) appContext;

        app.getBackgroundWriteExecutor().execute(() -> {
            try {
                // extract last 30 day data for the llm
                String timelineContext = buildTimelineContext(appContext);

                // Set low temperature for direct/fast responses and maxOutputTokens to prevent runaway generation
                GenerationConfig config = new GenerationConfig.Builder()
                        .setTemperature(0.2f)
                        .setMaxOutputTokens(512)
                        .build();

                // gemini-2.0-flash offers optimal low latency
                GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.vertexAI())
                        .generativeModel("gemini-2.0-flash", config);

                GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

                SimpleDateFormat todayFormat = new SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.getDefault());
                String todayStr = todayFormat.format(new Date());

                String systemPrompt = "You are 'Ask Your Timeline', a helpful AI assistant inside the LifeTracker app.\n" +
                        "TODAY'S DATE: " + todayStr + "\n\n" +
                        "Your job is to answer questions about the user's location history, places, activities, screen time, and habits based strictly on the provided timeline data.\n\n" +
                        "### GUIDELINES:\n" +
                        "1. Provide concise, clear, and natural answers.\n" +
                        "2. Summarize activity patterns, locations, and durations as requested (1-3 paragraphs max).\n" +
                        "3. Whenever you reference or mention specific dates or days in your response, tag the exact date in format `[Date: YYYY-MM-DD]` (e.g. `[Date: 2025-02-18]`). You can write naturally like: \"On Wednesday, Feb 19 [Date: 2025-02-19], you visited...\"\n" +
                        "4. If the user asks about something not recorded in the timeline logs, politely explain what data is available.\n\n" +
                        "### USER LOCAL TIMELINE DATA (Past 30 Days):\n" + timelineContext + "\n\n" +
                        "### USER QUESTION:\n" + userQuery;
                Content promptContent = new Content.Builder()
                        .setParts(Collections.singletonList(new TextPart(systemPrompt)))
                        .build();

                ListenableFuture<GenerateContentResponse> listenableResponse = model.generateContent(promptContent);
                GenerateContentResponse generateContentResponse = listenableResponse.get();

                String rawAnswer;
                if (generateContentResponse != null && generateContentResponse.getText() != null) {
                    rawAnswer = generateContentResponse.getText();
                } else {
                    rawAnswer = "I couldn't process your request right now. Please try again.";
                }

                // Process tags and extract referenced date links
                ChatMessage assistantMessage = processResponseWithDateLinks(rawAnswer);
                future.complete(assistantMessage);

            } catch (Exception e) {
                Log.e(TAG, "Error querying Gemini for timeline question", e);
                ErrorLogger.logError(appContext, TAG, "Error querying Gemini", e);

                // Provide a friendly local fallback if network/API fails
                ChatMessage fallbackMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        "Sorry, I encountered an issue reaching the AI service. Please check your connection and try asking again.",
                        false,
                        System.currentTimeMillis(),
                        new ArrayList<>()
                );
                future.complete(fallbackMessage);
            }
        });

        return future;
    }

    private static class TimelineEvent {
        final long startTime;
        long endTime;
        final String description;
        final boolean isStill;

        TimelineEvent(long startTime, long endTime, String description, boolean isStill) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.description = description;
            this.isStill = isStill;
        }
    }

    private static String buildTimelineContext(Context context) {
        ActivityDatabase db = ActivityDatabase.getDatabase(context);
        ActivityDao dao = db.activityDao();

        Calendar cal = Calendar.getInstance();
        Date endDate = cal.getTime();

        cal.add(Calendar.DAY_OF_YEAR, -30);
        Date startDate = cal.getTime();

        List<StillLocation> stills = dao.getStillsFromRange(startDate, endDate);
        List<MovementActivity> movements = dao.getMovementsFromRange(startDate, endDate);

        List<TimelineEvent> rawEvents = new ArrayList<>();

        for (StillLocation s : stills) {
            if (s.getStartTimeDate() != null) {
                long start = s.getStartTimeDate().getTime();
                long end = s.getEndTimeDate() != null ? s.getEndTimeDate().getTime() : Long.MAX_VALUE;
                String place = (s.getPlaceName() != null && !s.getPlaceName().trim().isEmpty()) ? s.getPlaceName() :
                        ((s.getCategory() != null && !s.getCategory().trim().isEmpty()) ? s.getCategory() : "Unknown location");
                rawEvents.add(new TimelineEvent(start, end, "Still at " + place, true));
            }
        }

        for (MovementActivity m : movements) {
            if (m.getStartTimeDate() != null) {
                long start = m.getStartTimeDate().getTime();
                long end = m.getEndTimeDate() != null ? m.getEndTimeDate().getTime() : Long.MAX_VALUE;
                String type = (m.getActivityTypeName() != null && !m.getActivityTypeName().trim().isEmpty()) ? m.getActivityTypeName() : "Moving";
                rawEvents.add(new TimelineEvent(start, end, "Movement (" + type + ")", false));
            }
        }

        if (rawEvents.isEmpty()) {
            return "No timeline activities recorded in the last 30 days.";
        }

        // Sort events chronologically
        rawEvents.sort(Comparator.comparingLong(e -> e.startTime));

        // Merge contiguous/overlapping identical still locations (e.g. fragmented Home logs within 5 min)
        List<TimelineEvent> mergedEvents = new ArrayList<>();
        long gapToleranceMs = 5 * 60 * 1000L; // 5 minutes

        for (TimelineEvent event : rawEvents) {
            if (mergedEvents.isEmpty()) {
                mergedEvents.add(event);
            } else {
                TimelineEvent last = mergedEvents.get(mergedEvents.size() - 1);
                if (last.isStill && event.isStill && last.description.equalsIgnoreCase(event.description)
                        && event.startTime <= last.endTime + gapToleranceMs) {
                    last.endTime = Math.max(last.endTime, event.endTime);
                } else {
                    mergedEvents.add(event);
                }
            }
        }

        // Group by day for clean, compact output
        SimpleDateFormat dayHeaderFormat = new SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        Map<String, List<String>> dayMap = new LinkedHashMap<>();

        for (TimelineEvent event : mergedEvents) {
            Date eventStartDate = new Date(event.startTime);
            String dayHeader = dayHeaderFormat.format(eventStartDate);

            String startStr = timeFormat.format(eventStartDate);
            String endStr = (event.endTime != Long.MAX_VALUE) ? timeFormat.format(new Date(event.endTime)) : "Ongoing";

            String line = "- " + startStr + "-" + endStr + ": " + event.description;

            List<String> entries = dayMap.computeIfAbsent(dayHeader, k -> new ArrayList<>());
            entries.add(line);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : dayMap.entrySet()) {
            sb.append("Date: ").append(entry.getKey()).append("\n");
            for (String line : entry.getValue()) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }

        return sb.length() > 0 ? sb.toString().trim() : "No timeline activities recorded in the last 30 days.";
    }

    private static ChatMessage processResponseWithDateLinks(String rawAnswer) {
        Matcher matcher = DATE_PATTERN.matcher(rawAnswer);

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        List<DateLink> dateLinks = new ArrayList<>();
        List<String> addedDateStrings = new ArrayList<>();

        while (matcher.find()) {
            String dateStr = matcher.group(1);
            if (dateStr != null && !addedDateStrings.contains(dateStr)) {
                addedDateStrings.add(dateStr);
                try {
                    Date date = isoFormat.parse(dateStr);
                    if (date != null) {
                        String label = displayFormat.format(date);
                        dateLinks.add(new DateLink(label, date));
                    }
                } catch (ParseException e) {
                    Log.w(TAG, "Failed to parse date from LLM: " + dateStr, e);
                }
            }
        }

        // Clean up the `[Date: YYYY-MM-DD]` tags from display content
        String cleanContent = rawAnswer.replaceAll("\\[Date:\\s*\\d{4}-\\d{2}-\\d{2}]", "").trim();

        return new ChatMessage(
                UUID.randomUUID().toString(),
                cleanContent,
                false,
                System.currentTimeMillis(),
                dateLinks
        );
    }
}

