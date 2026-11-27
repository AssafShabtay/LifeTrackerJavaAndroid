package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A meaningful period of life defined by the user ("University", "Living abroad").
 * Experiences, trips, photos and notes within the period belong to the chapter.
 */
@Entity(tableName = "life_chapters",
        foreignKeys = @ForeignKey(entity = MediaItem.class, parentColumns = "id", childColumns = "coverMediaId", onDelete = ForeignKey.SET_NULL),
        indices = {@Index("startTime"), @Index("coverMediaId")})
public class LifeChapter {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String description;
    public long startTime;
    /** Null while the chapter is still being lived. */
    public Long endTime;
    public Long coverMediaId;
    public long createdAt;
    public long updatedAt;
}
