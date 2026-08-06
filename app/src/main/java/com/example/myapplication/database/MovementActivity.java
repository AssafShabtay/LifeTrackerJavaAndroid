package com.example.myapplication.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Entity(tableName = "movement_activities")
public class MovementActivity implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String activityTypeName;
    private Double startLat;
    private Double startLng;
    private Double endLat;
    private Double endLng;

    private Date startTimeDate;
    private Date endTimeDate;

    @Ignore
    private List<StillLocation> stops = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MovementActivity that = (MovementActivity) o;
        return id == that.id && Objects.equals(activityTypeName, that.activityTypeName) && Objects.equals(startLat, that.startLat) && Objects.equals(startLng, that.startLng) && Objects.equals(endLat, that.endLat) && Objects.equals(endLng, that.endLng) && Objects.equals(startTimeDate, that.startTimeDate) && Objects.equals(endTimeDate, that.endTimeDate) && Objects.equals(stops, that.stops);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, activityTypeName, startLat, startLng, endLat, endLng, startTimeDate, endTimeDate, stops);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getActivityTypeName() {
        return activityTypeName;
    }

    public void setActivityTypeName(String activityTypeName) {
        this.activityTypeName = activityTypeName;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLng() {
        return startLng;
    }

    public void setStartLng(Double startLng) {
        this.startLng = startLng;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public Double getEndLng() {
        return endLng;
    }

    public void setEndLng(Double endLng) {
        this.endLng = endLng;
    }

    public Date getStartTimeDate() {
        return startTimeDate;
    }

    public void setStartTimeDate(Date startTimeDate) {
        this.startTimeDate = startTimeDate;
    }

    public Date getEndTimeDate() {
        return endTimeDate;
    }

    public void setEndTimeDate(Date endTimeDate) {
        this.endTimeDate = endTimeDate;
    }

    public List<StillLocation> getStops() {
        return stops;
    }

    public void setStops(List<StillLocation> stops) {
        this.stops = stops;
    }
}
