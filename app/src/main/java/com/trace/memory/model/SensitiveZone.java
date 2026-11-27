package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A place the user excluded from their memories. Experiences are never generated inside it,
 * it is hidden from maps and no external place lookups are made for it.
 */
@Entity(tableName = "sensitive_zones")
public class SensitiveZone {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String label;
    public double lat;
    public double lng;
    public float radiusMeters;
    public long createdAt;
}
