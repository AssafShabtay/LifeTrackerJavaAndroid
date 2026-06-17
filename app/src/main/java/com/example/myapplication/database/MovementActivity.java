package com.example.myapplication.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity(tableName = "movement_activities")
public class MovementActivity implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String activityTypeName;
    public Double startLat;
    public Double startLng;
    public Double endLat;
    public Double endLng;

    public Date startTimeDate;
    public Date endTimeDate;

    @Ignore
    public List<StillLocation> stops = new ArrayList<>();

    @Override
    public Date getStartTime() {
        return startTimeDate;
    }

    @Override
    public Date getEndTime() {
        if (!stops.isEmpty()) {
            return stops.get(stops.size() - 1).getEndTime();
        }
        return endTimeDate;
    }
}
