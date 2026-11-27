package com.trace.tracking.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.trace.TraceApp;
import com.trace.data.db.ActivityDatabase;
import com.trace.data.db.SleepDao;
import com.trace.data.model.SleepSession;
import com.trace.util.Logger;
import com.google.android.gms.location.SleepSegmentEvent;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SleepReceiver extends BroadcastReceiver {

    private static final String TAG = "SleepReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        if (SleepSegmentEvent.hasEvents(intent)) {
            List<SleepSegmentEvent> sleepSegmentEvents = SleepSegmentEvent.extractEvents(intent);

            TraceApp app = (TraceApp) context.getApplicationContext();
            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            SleepDao sleepDao = db.sleepDao();

            app.getBackgroundWriteExecutor().execute(() -> {
                for (SleepSegmentEvent event : sleepSegmentEvents) {
                    Date startTime = new Date(event.getStartTimeMillis());
                    Date endTime = new Date(event.getEndTimeMillis());

                    // check if this sleep session is already in the database (or overlaps with an existing/edited one)
                    List<SleepSession> overlaps = sleepDao.getOverlappingSleepSessions(startTime, endTime);
                    boolean isDuplicate = false;

                    for (SleepSession existing : overlaps) {
                        long overlapStart = Math.max(existing.getStartTime().getTime(), startTime.getTime());
                        long overlapEnd = Math.min(existing.getEndTime().getTime(), endTime.getTime());
                        long overlapDuration = Math.max(0, overlapEnd - overlapStart);

                        long newDuration = endTime.getTime() - startTime.getTime();
                        long existingDuration = existing.getEndTime().getTime() - existing.getStartTime().getTime();

                        // If the overlap covers more than 50% of either session, consider it a duplicate/update to skip
                        if (overlapDuration > (newDuration / 2) || overlapDuration > (existingDuration / 2)) {
                            isDuplicate = true;
                            break;
                        }
                    }

                    if (isDuplicate) {
                        Log.d(TAG, "Sleep session already exists or overlaps significantly, skipping duplicate.");
                        continue;
                    }

                    SleepSession session = new SleepSession();
                    session.setStartTime(startTime);
                    session.setEndTime(endTime);

                    sleepDao.insertSleepSession(session);

                    String msg = String.format(Locale.US, "DB Update from SleepReceiver: Inserted sleep session from %s to %s",
                            session.getStartTime().toString(), session.getEndTime().toString());
                    Logger.saveLog(context, msg);
                    Log.d(TAG, msg);
                }
            });
        }
    }
}
