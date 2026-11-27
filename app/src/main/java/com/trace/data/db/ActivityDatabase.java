package com.trace.data.db;
import com.trace.data.model.MovementActivity;
import com.trace.data.model.Place;
import com.trace.data.model.RoutePoint;
import com.trace.data.model.StillLocation;
import com.trace.data.model.SleepSession;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {StillLocation.class, MovementActivity.class, Place.class, RoutePoint.class, SleepSession.class},
        version = 5 ,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class ActivityDatabase extends RoomDatabase {

    public abstract ActivityDao activityDao();
    public abstract PlaceDao placeDao();
    public abstract SleepDao sleepDao();

    private static volatile ActivityDatabase INSTANCE;

    public static ActivityDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (ActivityDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    ActivityDatabase.class,
                                    "activity_database"
                            )
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void closeInstance() {
        if (INSTANCE != null) {
            if (INSTANCE.isOpen()) {
                INSTANCE.close();
            }
            INSTANCE = null;
        }
    }
}
