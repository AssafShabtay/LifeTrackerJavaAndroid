package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "movement_activities")
public class MovementActivity implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String activityType;
    public Double startLat;
    public Double startLng;
    public Double endLat;
    public Double endLng;

    public Date startTimeDate;
    public Date endTimeDate;

    @Override
    public Date getStartTime() {
        return startTimeDate;
    }

    @Override
    public Date getEndTime() {
        return endTimeDate;
    }
}
