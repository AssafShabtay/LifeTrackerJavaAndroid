package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Many-to-many association between experiences and media.
 * {@link #excluded} rows remember that the user removed an automatic association,
 * so reprocessing never re-adds it.
 */
@Entity(tableName = "experience_media",
        primaryKeys = {"experienceId", "mediaId"},
        foreignKeys = {
                @ForeignKey(entity = Experience.class, parentColumns = "id", childColumns = "experienceId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = MediaItem.class, parentColumns = "id", childColumns = "mediaId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("mediaId")})
public class ExperienceMedia {

    public static final String ORIGIN_AUTO = "AUTO";
    public static final String ORIGIN_MANUAL = "MANUAL";

    public long experienceId;
    public long mediaId;
    public String origin;
    public boolean excluded;
    public long addedAt;

    public ExperienceMedia() {
    }

    public ExperienceMedia(long experienceId, long mediaId, String origin, boolean excluded, long addedAt) {
        this.experienceId = experienceId;
        this.mediaId = mediaId;
        this.origin = origin;
        this.excluded = excluded;
        this.addedAt = addedAt;
    }
}
