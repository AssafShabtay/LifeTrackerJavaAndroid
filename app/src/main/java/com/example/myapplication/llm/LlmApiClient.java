package com.example.myapplication.llm;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.TextPart;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LlmApiClient {

    // Initialize an ExecutorService for background tasks
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Placeholder for LLM interaction
    public static CompletableFuture<LlmResponse> getHabitAndAnomaly(String timelineSequence) {
        CompletableFuture<LlmResponse> future = new CompletableFuture<>();

        // Ensure Firebase is initialized. You should initialize Firebase in your Application class.
        // For example, in your Application's onCreate: FirebaseApp.initializeApp(context);
        if (FirebaseApp.getInstance() == null) {
            future.completeExceptionally(new IllegalStateException("FirebaseApp is not initialized. Call FirebaseApp.initializeApp() first."));
            return future;
        }

        executorService.submit(() -> {
            try {
                // Initialize the GenerativeModel. Replace "gemini-pro" with your desired model.
                // You might need to configure the model with an API key if not using Firebase Auth.
                GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.vertexAI())
                        .generativeModel("gemini-2.5-flash");

                GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

                // Construct the prompt for the LLM
                Content promptContent = new Content.Builder()
                        .setParts(Collections.singletonList(new TextPart(
                                "Analyze the following timeline sequence to identify daily habits and any unusual anomalies. " +
                                "Respond with two distinct sentences: one describing a general habit and another describing an anomaly. " +
                                "Timeline: " + timelineSequence
                        )))
                        .build();

                // Make the LLM call
                ListenableFuture<GenerateContentResponse> listenableResponse = model.generateContent(promptContent);

                GenerateContentResponse generateContentResponse = listenableResponse.get();
                // Parse the response

                String llmText = null;
                if (generateContentResponse != null) {
                    llmText = generateContentResponse.getText();
                }

                // Basic parsing to separate habit and anomaly (you might need more robust parsing)
                String habit = "No clear habit identified.";
                String anomaly = "No clear anomaly detected.";

                if (llmText != null && !llmText.isEmpty()) {
                    String[] sentences = llmText.split("\\.");
                    if (sentences.length >= 1) {
                        habit = sentences[0].trim() + ".";
                    }
                    if (sentences.length >= 2) {
                        anomaly = sentences[1].trim() + ".";
                    } else if (sentences.length == 1 && llmText.contains("anomaly")) { // Simple check if only one sentence contains "anomaly"
                        anomaly = sentences[0].trim() + ".";
                        habit = "No distinct habit identified from the single response.";
                    }
                }

                future.complete(new LlmResponse(habit, anomaly));

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }
}
