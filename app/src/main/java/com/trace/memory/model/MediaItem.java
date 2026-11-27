package com.trace.memory.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A reference to a photo or video that lives in the device media library (or a picked document).
 * Trace never copies original media into its own storage and never deletes originals.
 */
@Entity(tableName = "media_items",
        indices = {
                @Index(value = "uri", unique = true),
                @Index("takenAt")
        })
public class MediaItem {

    public static final String SOURCE_MEDIASTORE = "MEDIASTORE";
    public static final String SOURCE_PICKED = "PICKED";
    public static final String SOURCE_CAMERA = "CAMERA";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String uri = "";
    public long mediaStoreId;
    public String mimeType;
    public boolean video;
    public long takenAt;
    public int width;
    public int height;
    public long durationMs;
    public Double lat;
    public Double lng;
    /** False when the underlying content can no longer be opened (deleted or access revoked). */
    public boolean available = true;
    public String source;
    public long addedAt;
}
