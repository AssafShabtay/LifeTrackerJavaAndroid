package com.trace.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.trace.data.model.SleepSession;

import java.util.Date;
import java.util.List;

@Dao
public interface SleepDao {
    @Insert
    long insertSleepSession(SleepSession sleepSession);
    
    @Update
    void updateSleepSession(SleepSession sleepSession);

    @Delete
    void deleteSleepSession(SleepSession sleepSession);
    
    @Query("SELECT * FROM sleep_session WHERE startTime < :endTime AND endTime > :startTime")
    List<SleepSession> getOverlappingSleepSessions(Date startTime, Date endTime);

    @Query("SELECT * FROM sleep_session WHERE (startTime >= :start AND startTime <= :end) OR (endTime >= :start AND endTime <= :end) OR (startTime <= :start AND endTime >= :end) ORDER BY startTime ASC")
    List<SleepSession> getSleepSessionsFromRange(Date start, Date end);
}
