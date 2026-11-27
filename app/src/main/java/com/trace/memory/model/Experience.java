package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.trace.data.model.Place;

/**
 * The fundamental entity of Trace: a meaningful event or related sequence of activities.
 * <p>
 * Raw tracking records (visits, movements, route points) are not duplicated. They are
 * associated with an experience through its time range ({@link #startTime}..{@link #endTime})
 * and its anchor place, because the tracking backend continuously merges and replaces raw rows.
 * Media, notes, trips and collections are associated through explicit relationships.
 */
@Entity(tableName = "experiences",
        foreignKeys = {
                @ForeignKey(entity = Trip.class, parentColumns = "id", childColumns = "tripId", onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = MediaItem.class, parentColumns = "id", childColumns = "coverMediaId", onDelete = ForeignKey.SET_NULL)
        },
        indices = {
                @Index("startTime"),
                @Index("endTime"),
                @Index("dayKey"),
                @Index("tripId"),
                @Index("placeId"),
                @Index("coverMediaId"),
                @Index("locality")
        })
public class Experience {

    public static final String SOURCE_AUTO = "AUTO";
    public static final String SOURCE_MANUAL = "MANUAL";

    /** Bits in {@link #userLocks}: fields the user has customized and that reprocessing must preserve. */
    public static final int LOCK_TITLE = 1;
    public static final int LOCK_TIME = 1 << 1;
    public static final int LOCK_DESCRIPTION = 1 << 2;
    public static final int LOCK_PLACE = 1 << 3;
    public static final int LOCK_CATEGORY = 1 << 4;
    public static final int LOCK_COVER = 1 << 5;
    public static final int LOCK_TRIP = 1 << 6;

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String description;

    public long startTime;
    public long endTime;
    /** Local calendar day of {@link #startTime} as yyyyMMdd. */
    public int dayKey;

    /** One of {@link ExperienceCategory} names. */
    public String category;

    public Long placeId;
    public String placeName;
    public String placeCategory;
    public Double lat;
    public Double lng;

    public String locality;
    public String countryName;
    public String countryCode;
    public long geoResolvedAt;

    public double distanceMeters;
    public long activeMovementMs;
    /** Comma separated movement modes observed during the experience (e.g. "Walking,Driving"). */
    public String movementModes;
    public int visitCount;

    public Long coverMediaId;
    public Long tripId;

    public String source;
    public String sourceKey;
    public boolean confirmed;
    public int userLocks;
    /** Tombstone: a deleted automatic experience is kept hidden so reprocessing never recreates it. */
    public boolean deleted;
    public boolean favorite;
    public boolean ongoing;
    /** Heuristic importance used for highlights and recaps. */
    public double salience;

    public long createdAt;
    public long updatedAt;

    public boolean isLocked(int lock) {
        return (userLocks & lock) != 0;
    }

    public void lock(int lock) {
        userLocks |= lock;
    }

    public boolean isAuto() {
        return SOURCE_AUTO.equals(source);
    }

    public long durationMs() {
        return Math.max(0, endTime - startTime);
    }
}
