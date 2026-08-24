package com.example.myapplication.helpers;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ErrorLogger {

    private static final String LOG_FILE_NAME = "error_log.txt";

    public static void logError(Context context, String tag, String message, Throwable throwable) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        try (FileWriter fileWriter = new FileWriter(logFile, true); // true for append mode
             PrintWriter printWriter = new PrintWriter(fileWriter)) {

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            printWriter.println("Timestamp: " + timestamp);
            printWriter.println("Tag: " + tag);
            printWriter.println("Message: " + message);

            if (throwable != null) {
                printWriter.println("Exception: " + throwable.getClass().getSimpleName());
                printWriter.println("Stack Trace:");
                throwable.printStackTrace(printWriter);
            }
            printWriter.println("------------------------------------");
            printWriter.flush(); // Ensure data is written to the file immediately

        } catch (IOException e) {
            // If logging to file fails, log to Logcat as a fallback
            android.util.Log.e("ErrorLogger", "Failed to write error to log file", e);
        }
    }

    public static void logError(Context context, String tag, String message) {
        logError(context, tag, message, null);
    }
}