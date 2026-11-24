package com.example.lifetracker.data.db;
import com.example.lifetracker.data.model.MovementActivity;
import com.example.lifetracker.data.model.Place;
import com.example.lifetracker.data.model.RoutePoint;
import com.example.lifetracker.data.model.StillLocation;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {StillLocation.class, MovementActivity.class, Place.class, RoutePoint.class},
        version = 4 ,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class ActivityDatabase extends RoomDatabase {

    public abstract ActivityDao activityDao();
    public abstract PlaceDao placeDao();

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
