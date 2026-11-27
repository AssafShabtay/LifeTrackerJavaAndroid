package com.trace.memory.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Cached reverse geocoding result for a ~1 km grid cell, to minimise geocoder requests. */
@Entity(tableName = "geo_cache")
public class GeoCacheEntry {
    @PrimaryKey
    @NonNull
    public String cellKey = "";

    public String locality;
    public String adminArea;
    public String countryName;
    public String countryCode;
    public long resolvedAt;
}
