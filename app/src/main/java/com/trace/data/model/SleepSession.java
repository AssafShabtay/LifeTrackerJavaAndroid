package com.trace.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;
import java.util.Objects;

@Entity(tableName = "sleep_session")
public class SleepSession implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private Date startTime;
    private Date endTime;

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    
    @Override
    public Date getStartTimeDate() {
        return startTime;
    }

    @Override
    public Date getEndTimeDate() {
        return endTime;
    }

    public SleepSession copy() {
        SleepSession copy = new SleepSession();
        copy.setId(this.id);
        copy.setStartTime(this.startTime != null ? new Date(this.startTime.getTime()) : null);
        copy.setEndTime(this.endTime != null ? new Date(this.endTime.getTime()) : null);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SleepSession that = (SleepSession) o;
        return id == that.id && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startTime, endTime);
    }
}
