package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A user curated set of experiences that is not restricted to a date range. */
@Entity(tableName = "collections",
        foreignKeys = @ForeignKey(entity = MediaItem.class, parentColumns = "id", childColumns = "coverMediaId", onDelete = ForeignKey.SET_NULL),
        indices = {@Index("coverMediaId")})
public class MemoryCollection {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String description;
    public Long coverMediaId;
    public long createdAt;
    public long updatedAt;
}
