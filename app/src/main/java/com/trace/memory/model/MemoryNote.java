package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Actively captured meaning: a written note, a voice memory or a personal milestone.
 * When its experience is deleted the note survives as a loose moment of that day.
 */
@Entity(tableName = "memory_notes",
        foreignKeys = @ForeignKey(entity = Experience.class, parentColumns = "id", childColumns = "experienceId", onDelete = ForeignKey.SET_NULL),
        indices = {@Index("experienceId"), @Index("momentTime"), @Index("dayKey"), @Index("type")})
public class MemoryNote {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_VOICE = "VOICE";
    public static final String TYPE_MILESTONE = "MILESTONE";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public Long experienceId;
    public String type;
    public String title;
    public String text;
    /** Absolute path of an app-private audio file for voice memories. */
    public String audioPath;
    public long audioDurationMs;
    /** Optional feeling chosen by the user (never inferred). */
    public String feeling;
    public long momentTime;
    public int dayKey;
    public Double lat;
    public Double lng;
    public long createdAt;
    public long updatedAt;

    public boolean isVoice() {
        return TYPE_VOICE.equals(type);
    }

    public boolean isMilestone() {
        return TYPE_MILESTONE.equals(type);
    }
}
