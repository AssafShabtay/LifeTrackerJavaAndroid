package com.example.myapplication.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "route_points",
        foreignKeys = @ForeignKey(entity = MovementActivity.class,
                parentColumns = "id",
                childColumns = "movementActivityId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("movementActivityId")})

public class RoutePoint {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private long movementActivityId;
    private double lat;
    private double lng;
    private long timestamp;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getMovementActivityId() {
        return movementActivityId;
    }

    public void setMovementActivityId(long movementActivityId) {
        this.movementActivityId = movementActivityId;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
