package com.example.myapplication.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

@Dao
public interface ActivityDao {

    @Insert
    long insertStillLocation(StillLocation stillLocation);

    @Update
    void updateStillLocation(StillLocation stillLocation);

    @Query("DELETE FROM still_locations WHERE id = :id")
    void deleteStillLocation(long id);

    @Query("UPDATE still_locations SET endTimeDate = :endTimeDate WHERE id = :id")
    void endStillLocation(long id, Date endTimeDate);

    @Query("UPDATE still_locations SET endTimeDate = :endTimeDate WHERE id = :id")
    void updateStillEndTime(long id, Date endTimeDate);

    @Query("UPDATE still_locations SET startTimeDate = :startTimeDate WHERE id = :id")
    void updateStillStartTime(long id, Date startTimeDate);

    @Query("SELECT * FROM still_locations WHERE id = :id LIMIT 1")
    StillLocation getStillLocationById(long id);

    @Query("SELECT * FROM still_locations WHERE endTimeDate IS NULL ORDER BY startTimeDate DESC LIMIT 1")
    StillLocation getActiveStillLocation();

    @Query("SELECT * FROM still_locations WHERE endTimeDate IS NOT NULL ORDER BY endTimeDate DESC LIMIT 1")
    StillLocation getLastCompletedStillLocation();

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

    @Query("SELECT * FROM still_locations")
    List<StillLocation> getAllStillLocations();

    @Query("SELECT * FROM still_locations WHERE startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")
    List<StillLocation> getStillForRange(Date start, Date end);

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

    @Transaction
    default void deleteMovementAndExtendStill(long movementId, long stillId, Date newEndTime) {
        deleteMovementActivity(movementId);
        updateStillEndTime(stillId, newEndTime);
    }

    @Transaction
    default void deleteMovementAndPrependToStill(long movementId, long stillId, Date newStartTime) {
        deleteMovementActivity(movementId);
        updateStillStartTime(stillId, newStartTime);
    }
}
