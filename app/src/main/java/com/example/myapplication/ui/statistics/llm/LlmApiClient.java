package com.example.myapplication.ui.statistics.llm;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.util.ErrorLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.TextPart;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LlmApiClient {

    private static final String TAG = "LlmApiClient";

    public static CompletableFuture<LlmResponse> getHabitAndAnomaly(String timelineSequence) {
        CompletableFuture<LlmResponse> future = new CompletableFuture<>();

        Context context;
        try {
            context = FirebaseApp.getInstance().getApplicationContext();
        } catch (Exception e) {
            future.completeExceptionally(new IllegalStateException("FirebaseApp is not initialized or context is unavailable.", e));
            return future;
        }

        LifeTrackerApp app = (LifeTrackerApp) context;

        app.getBackgroundWriteExecutor().execute(() -> {
            try {
                GenerationConfig config = new GenerationConfig.Builder()
                        .setResponseMimeType("application/json")
                        .build();

                GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.vertexAI())
                        .generativeModel("gemini-2.5-flash", config);

                GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

                String prompt = "You are a warm, observant personal lifestyle assistant for the LifeTracker app.\n" +
                        "Your task is to analyze the user's location history over the past 14 days and uncover meaningful, personalized insights.\n\n" +
                        "### Guidelines:\n" +
                        "1. HABIT INSIGHT:\n" +
                        "   - Identify a clear, recurring daily or weekly routine (e.g., regular work schedule, weekend cafe visits, workout routine, typical stay duration).\n" +
                        "   - Use specific details like days of the week, times of day, place names, and approximate durations when available.\n" +
                        "   - Phrase it warmly in second person (e.g., \"You consistently visit...\").\n\n" +
                        "2. ANOMALY INSIGHT:\n" +
                        "   - Identify an interesting deviation, outlier, or unusual pattern (e.g., visiting a location at an unexpected time, staying much longer/shorter than usual, skipping a routine, or visiting an uncommon spot).\n" +
                        "   - Highlight the contrast with normal behavior.\n" +
                        "   - Phrase it warmly in second person (e.g., \"On Thursday, you stayed at...\").\n\n" +
                        "3. FORMAT & OUTPUT:\n" +
                        "   - Output ONLY a valid JSON object strictly adhering to this schema:\n" +
                        "     {\n" +
                        "       \"habit\": \"One concise, engaging sentence describing a distinct habit.\",\n" +
                        "       \"anomaly\": \"One concise, engaging sentence describing a notable anomaly or deviation.\"\n" +
                        "     }\n" +
                        "   - Do NOT include extra explanation text outside JSON.\n\n" +
                        "### Timeline Data (Chronological):\n" + timelineSequence;

                Content promptContent = new Content.Builder()
                        .setParts(Collections.singletonList(new TextPart(prompt)))
                        .build();

                ListenableFuture<GenerateContentResponse> listenableResponse = model.generateContent(promptContent);
                GenerateContentResponse generateContentResponse = listenableResponse.get();

                String llmText = null;
                if (generateContentResponse != null) {
                    llmText = generateContentResponse.getText();
                }

                LlmResponse parsedResponse = parseJsonResponse(llmText);
                future.complete(parsedResponse);

            } catch (Exception e) {
                Log.e(TAG, "Error generating LLM insights", e);
                ErrorLogger.logError(context, TAG, "Error generating LLM insights", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public static LlmResponse parseJsonResponse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new LlmResponse("No clear habit identified.", "No clear anomaly detected.");
        }

        String cleanedText = rawText.trim();
        if (cleanedText.startsWith("```json")) {
            cleanedText = cleanedText.substring(7);
        } else if (cleanedText.startsWith("```")) {
            cleanedText = cleanedText.substring(3);
        }
        if (cleanedText.endsWith("```")) {
            cleanedText = cleanedText.substring(0, cleanedText.length() - 3);
        }
        cleanedText = cleanedText.trim();

        try {
            JSONObject json = new JSONObject(cleanedText);
            String habit = json.optString("habit", "No clear habit identified.");
            String anomaly = json.optString("anomaly", "No clear anomaly detected.");
            return new LlmResponse(habit, anomaly);
        } catch (JSONException e) {
            return fallbackParse(rawText);
        }
    }

    private static LlmResponse fallbackParse(String text) {
        String habit = "No clear habit identified.";
        String anomaly = "No clear anomaly detected.";

        if (text != null && !text.isEmpty()) {
            String[] sentences = text.split("[.\n]");
            List<String> validSentences = new ArrayList<>();
            for (String s : sentences) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    validSentences.add(trimmed);
                }
            }
            if (!validSentences.isEmpty()) {
                habit = validSentences.get(0) + ".";
            }
            if (validSentences.size() > 1) {
                anomaly = validSentences.get(1) + ".";
            }
        }
        return new LlmResponse(habit, anomaly);
    }
}
