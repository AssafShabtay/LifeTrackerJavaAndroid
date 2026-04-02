package com.example.myapplication.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.Date;
import java.util.List;

@Dao
public interface ActivityDao {

    @Insert
    long insertStillLocation(StillLocation stillLocation);

    @Query("DELETE FROM still_locations WHERE id = :id")
    void deleteStillLocation(long id);

    @Query("UPDATE still_locations SET endTimeDate = :endTimeDate WHERE id = :id")
    void endStillLocation(long id, Date endTimeDate);

    @Query("UPDATE still_locations SET endTimeDate = :endTimeDate WHERE id = :id")
    void updateStillEndTime(long id, Date endTimeDate);

    @Query("SELECT * FROM still_locations WHERE id = :id LIMIT 1")
    StillLocation getStillLocationById(long id);

    @Query("SELECT * FROM still_locations WHERE endTimeDate IS NULL ORDER BY startTimeDate DESC LIMIT 1")
    StillLocation getActiveStillLocation();

    @Insert
    long insertMovementActivity(MovementActivity movementActivity);

    @Query("DELETE FROM movement_activities WHERE id = :id")
    void deleteMovementActivity(long id);

    @Query("UPDATE movement_activities SET endLat = :endLatitude, endLng = :endLongitude, endTimeDate = :endTimeDate WHERE id = :id")
    void endMovementActivity(long id, Double endLatitude, Double endLongitude, Date endTimeDate);

    @Query("UPDATE movement_activities SET endTimeDate = :endTimeDate WHERE id = :id")
    void updateMovementEndTime(long id, Date endTimeDate);

    @Query("SELECT * FROM movement_activities WHERE id = :id LIMIT 1")
    MovementActivity getMovementActivityById(long id);

    @Query("SELECT * FROM movement_activities WHERE endTimeDate IS NULL")
    List<MovementActivity> getActiveMovementActivities();

    // Any record that overlaps the requested interval should be returned.
    @Query("SELECT * FROM still_locations WHERE startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")
    List<StillLocation> getStillForRange(Date start, Date end);

    // Any record that overlaps the requested interval should be returned.
    @Query("SELECT * FROM movement_activities WHERE startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")
    List<MovementActivity> getMovementForRange(Date start, Date end);

    @Transaction
    default void replaceStillWithMovement(long id, MovementActivity movement) {
        deleteStillLocation(id);
        insertMovementActivity(movement);
    }

    @Transaction
    default void replaceMovementWithStill(long id, StillLocation still) {
        deleteMovementActivity(id);
        insertStillLocation(still);
    }
}
