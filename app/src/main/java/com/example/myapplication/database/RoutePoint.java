package com.example.myapplication.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "route_points",
        foreignKeys = @ForeignKey(entity = MovementActivity.class,
                parentColumns = "id",
                childColumns = "movementActivityId",
                onDelete = ForeignKey.CASCADE))
public class RoutePoint {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long movementActivityId;
    public double lat;
    public double lng;
    public long timestamp;
}
