package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A travel journal. Automatically detected trips start as {@link #STATUS_SUGGESTED}
 * and are only treated as facts after the user confirms them.
 * The structure (ordered days, experiences, media, notes, recap) is designed so a trip can later be
 * exported as a digital story or physical book.
 */
@Entity(tableName = "trips",
        foreignKeys = @ForeignKey(entity = MediaItem.class, parentColumns = "id", childColumns = "coverMediaId", onDelete = ForeignKey.SET_NULL),
        indices = {@Index("startTime"), @Index("status"), @Index("coverMediaId")})
public class Trip {

    public static final String STATUS_SUGGESTED = "SUGGESTED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final int LOCK_TITLE = 1;
    public static final int LOCK_DATES = 1 << 1;
    public static final int LOCK_DESTINATIONS = 1 << 2;

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String description;
    public long startTime;
    /** Null while a trip is ongoing. */
    public Long endTime;
    public String status;
    public String source;
    /** Comma separated, user editable list of destinations. */
    public String destinations;
    public String countryCodes;
    public Long coverMediaId;
    public int userLocks;
    public long createdAt;
    public long updatedAt;

    public boolean isSuggested() {
        return STATUS_SUGGESTED.equals(status);
    }

    public boolean isLocked(int lock) {
        return (userLocks & lock) != 0;
    }

    public long effectiveEnd(long now) {
        return endTime != null ? endTime : Math.max(now, startTime);
    }
}
