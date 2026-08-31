package com.example.myapplication.database;

import androidx.annotation.NonNull;
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

    @Update
    void updateMovementActivity(MovementActivity movement);


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
    @NonNull
    @Query("SELECT * FROM still_locations WHERE placeId = :placeId AND startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")
    List<StillLocation> getStillsFromRangeAndPlace(long placeId, Date start, Date end);

    @Query("UPDATE movement_activities SET endTimeDate = NULL WHERE id = :id")
    void resumeMovementActivity(long id);

    @Query("SELECT * FROM movement_activities WHERE id = :id LIMIT 1")
    MovementActivity getMovementActivityById(long id);

    @NonNull
    @Query("SELECT * FROM movement_activities WHERE endTimeDate IS NULL")
    List<MovementActivity> getActiveMovementActivities();
    @Query("SELECT * FROM movement_activities WHERE activityTypeName = :type AND endTimeDate IS NULL ORDER BY startTimeDate DESC LIMIT 1")
    MovementActivity getActiveMovementActivityByType(String type);

    @Query("SELECT * FROM movement_activities WHERE activityTypeName = :type AND endTimeDate IS NOT NULL ORDER BY endTimeDate DESC LIMIT 1")
    MovementActivity getLastCompletedMovementActivity(String type);

    @NonNull
    @Query("SELECT * FROM still_locations")
    List<StillLocation> getAllStillLocations();

    @NonNull
    @Query("SELECT * FROM still_locations WHERE startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")
    List<StillLocation> getStillsFromRange(Date start, Date end);

    @NonNull
    @Query("SELECT * FROM movement_activities WHERE startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")
    List<MovementActivity> getMovementsFromRange(Date start, Date end);

    @Query("SELECT COUNT(*) FROM movement_activities WHERE (startTimeDate < :endTime AND (endTimeDate IS NULL OR endTimeDate > :startTime))")
    int countMovementActivitiesBetween(Date startTime, Date endTime);

    @Insert
    void insertRoutePoint(RoutePoint point);

    @NonNull 
    @Query("SELECT * FROM route_points WHERE movementActivityId = :movementId ORDER BY timestamp ASC")
    List<RoutePoint> getRoutePointsForMovement(long movementId);

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

    @Query("DELETE FROM still_locations")
    void deleteStillLocations();

    @Query("DELETE FROM movement_activities")
    void deleteMovementActivities();

    @Transaction
    default void deleteAllActivities() {
        deleteStillLocations();
        deleteMovementActivities();
    }

    @Query("UPDATE still_locations SET category = :category, " +
            "placeName = CASE WHEN :category = 'Home' THEN 'Home' ELSE placeName END, " +
            "icon = CASE WHEN :category = 'Home' THEN 'Home' ELSE icon END " +
            "WHERE lat BETWEEN :minLat AND :maxLat " +
            "AND lng BETWEEN :minLng AND :maxLng")
    void updateStillsWithinBounds(double minLat, double maxLat, double minLng, double maxLng, String category);

    @Query("SELECT SUM(CASE WHEN endTimeDate IS NULL THEN :now ELSE endTimeDate END - startTimeDate) FROM still_locations WHERE category = 'Home' AND startTimeDate >= :sevenDaysAgo")
    long getTimeAtHomeSince(long sevenDaysAgo, long now);

    @Query("UPDATE places SET name = :name, address = :address, lat = :lat, lng = :lng, category = :category, icon = :icon, color = :color, geofencePlaceId = :geofencePlaceId WHERE id = :id")
    void updatePlaceInfo(long id, String name, String address, double lat, double lng, String category, String icon, Integer color, String geofencePlaceId);

    @Query("UPDATE still_locations SET placeName = :placeName, placeAddress = :placeAddress, lat = :lat, lng = :lng, category = :category, icon = :icon, color = :color, geofencePlaceId = :geofencePlaceId WHERE placeId = :placeId")
    void updateStillsByPlaceId(long placeId, String placeName, String placeAddress, Double lat, Double lng, String category, String icon, Integer color, String geofencePlaceId);

    @Transaction
    default void updateStillAndSyncPlace(StillLocation still) {
        updateStillLocation(still);
        if (still.getPlaceId() != null) {
            double lat = still.getLat() != null ? still.getLat() : 0.0;
            double lng = still.getLng() != null ? still.getLng() : 0.0;
            updatePlaceInfo(still.getPlaceId(), still.getPlaceName(), still.getPlaceAddress(), lat, lng, still.getCategory(), still.getIcon(), still.getColor(), still.getGeofencePlaceId());
            updateStillsByPlaceId(still.getPlaceId(), still.getPlaceName(), still.getPlaceAddress(), still.getLat(), still.getLng(), still.getCategory(), still.getIcon(), still.getColor(), still.getGeofencePlaceId());
        }
    }

    /**
     * Calculates the sum duration of all activities (StillLocation and MovementActivity)
     * within the last seven days, handling overlapping activities.
     *     * @param sevenDaysAgoMillis The timestamp representing seven days ago in milliseconds.
     *      * @param currentTimeMillis  The current timestamp in milliseconds.

     * @return The total merged duration of activities in milliseconds.
     */
    @Query("WITH AllEvents AS (" +
            "    SELECT " +
            "        startTimeDate AS event_time, " +
            "        1 AS event_type " + // 1 for start
            "    FROM " +
            "        still_locations " +
            "    WHERE " +
            "        startTimeDate <= :currentTimeMillis AND (endTimeDate IS NULL OR endTimeDate >= :sevenDaysAgoMillis) " +
            "    UNION ALL " +
            "    SELECT " +
            "        CASE WHEN endTimeDate IS NULL THEN :currentTimeMillis ELSE endTimeDate END AS event_time, " +
            "        -1 AS event_type " + // -1 for end
            "    FROM " +
            "        still_locations " +
            "    WHERE " +
            "        startTimeDate <= :currentTimeMillis AND (endTimeDate IS NULL OR endTimeDate >= :sevenDaysAgoMillis) " +
            "    UNION ALL " +
            "    SELECT " +
            "        startTimeDate AS event_time, " +
            "        1 AS event_type " + // 1 for start
            "    FROM " +
            "        movement_activities " +
            "    WHERE " +
            "        startTimeDate <= :currentTimeMillis AND (endTimeDate IS NULL OR endTimeDate >= :sevenDaysAgoMillis) " +
            "    UNION ALL " +
            "    SELECT " +
            "        CASE WHEN endTimeDate IS NULL THEN :currentTimeMillis ELSE endTimeDate END AS event_time, " +
            "        -1 AS event_type " + // -1 for end
            "    FROM " +
            "        movement_activities " +
            "    WHERE " +
            "        startTimeDate <= :currentTimeMillis AND (endTimeDate IS NULL OR endTimeDate >= :sevenDaysAgoMillis) " +
            "), " +
            "Changes AS (" +
            "    SELECT " +
            "        event_time, " +
            "        event_type, " +
            "        SUM(event_type) OVER (ORDER BY event_time, event_type DESC) AS running_overlap_count " +
            "    FROM " +
            "        AllEvents " +
            "), " +
            "StartEndPoints AS (" +
            "    SELECT " +
            "        event_time AS point_time, " +
            "        running_overlap_count AS current_count, " +
            "        LAG(running_overlap_count, 1, 0) OVER (ORDER BY event_time, event_type DESC) AS prev_count " +
            "    FROM " +
            "        Changes " +
            ") " +
            "SELECT " +
            "    SUM(CASE " +
            "        WHEN current_count = 0 AND prev_count > 0 THEN point_time " + // End of a merged interval
            "        WHEN current_count > 0 AND prev_count = 0 THEN -point_time " + // Start of a merged interval
            "        ELSE 0 " +
            "    END) AS total_merged_duration " +
            "FROM " +
            "    StartEndPoints " +
            "WHERE " +
            "    (current_count = 0 AND prev_count > 0) OR (current_count > 0 AND prev_count = 0)")
    long getSumDurationOfAllActivitiesLastSevenDays(long sevenDaysAgoMillis, long currentTimeMillis);
}
