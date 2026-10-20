package com.example.myapplication.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ErrorLogger {

    private static final String LOG_FILE_NAME = "error_log.txt";
    private static final String TAG = "ErrorLogger";

    public static void logError(Context context, String tag, String message, Throwable throwable) {
        if (context == null || context.getFilesDir() == null) {
            Log.e(TAG, "Cannot log error: context or filesDir is null. Tag: " + tag + ", Message: " + message, throwable);
            return;
        }
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        try (FileWriter fileWriter = new FileWriter(logFile, true);
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
            printWriter.flush();

        } catch (Exception e) {
            Log.e("ErrorLogger", "Failed to write error to log file", e);
        }
    }

    public static void logError(Context context, String tag, String message) {
        logError(context, tag, message, null);
    }
}