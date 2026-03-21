package com.example.myapplication;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Logger {
    private Logger() {}

    public static void saveLog(Context context, String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = time + " - " + message + "\n";

        File file = new File(context.getFilesDir(), "activity_log.txt");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file, true);
            writer.write(logMessage);
        } catch (IOException ignored) {
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
        }
    }
}
