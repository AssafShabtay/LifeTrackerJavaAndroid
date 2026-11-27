package com.trace.memory.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "collection_experiences",
        primaryKeys = {"collectionId", "experienceId"},
        foreignKeys = {
                @ForeignKey(entity = MemoryCollection.class, parentColumns = "id", childColumns = "collectionId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Experience.class, parentColumns = "id", childColumns = "experienceId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("experienceId")})
public class CollectionExperience {
    public long collectionId;
    public long experienceId;
    public long addedAt;

    public CollectionExperience() {
    }

    public CollectionExperience(long collectionId, long experienceId, long addedAt) {
        this.collectionId = collectionId;
        this.experienceId = experienceId;
        this.addedAt = addedAt;
    }
}
