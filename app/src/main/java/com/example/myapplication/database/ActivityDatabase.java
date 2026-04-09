package com.example.myapplication.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {StillLocation.class, MovementActivity.class, Place.class},
        version = 4,
        exportSchema = false
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
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
