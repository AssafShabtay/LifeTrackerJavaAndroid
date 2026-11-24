package com.example.lifetracker.data.model;

import androidx.room.PrimaryKey;
import java.util.Date;

/** Identity and time range shared by stationary visits and movement activities. */
public abstract class BaseActivity implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    protected long id;
    protected Date startTimeDate;
    protected Date endTimeDate;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public Date getStartTimeDate() { return startTimeDate; }
    public void setStartTimeDate(Date startTimeDate) { this.startTimeDate = startTimeDate; }
    public Date getEndTimeDate() { return endTimeDate; }
    public void setEndTimeDate(Date endTimeDate) { this.endTimeDate = endTimeDate; }

    protected void copyBaseTo(BaseActivity target) {
        target.id = id;
        target.startTimeDate = startTimeDate == null ? null : new Date(startTimeDate.getTime());
        target.endTimeDate = endTimeDate == null ? null : new Date(endTimeDate.getTime());
    }
}
