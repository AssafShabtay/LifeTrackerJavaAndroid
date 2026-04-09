package com.example.myapplication.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "places")
public class Place {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String address;
    public double lat;
    public double lng;
    public float radius;
    public String category; // e.g., "Home", "Work"
}
