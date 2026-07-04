package com.example.myapplication.database;

import java.util.Date;

public interface TimelineItem {
    long getId();
    Date getStartTimeDate();
    Date getEndTimeDate();

    boolean equals(Object object);
}
