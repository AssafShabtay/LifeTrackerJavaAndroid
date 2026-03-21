package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "still_locations")
public class StillLocation implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public Double lat;
    public Double lng;
    public Date startTimeDate;
    public Date endTimeDate;

    public String wasSupposedToBeActivity;
    public String placeId;
    public String placeName;
    public String placeCategory;
    public String placeAddress;

    @Override
    public Date getStartTime() {
        return startTimeDate;
    }

    @Override
    public Date getEndTime() {
        return endTimeDate;
    }
}
