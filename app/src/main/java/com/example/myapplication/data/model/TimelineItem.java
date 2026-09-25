package com.example.myapplication.data.model;

import java.util.Date;

public interface TimelineItem {
    long getId();
    Date getStartTimeDate();
    Date getEndTimeDate();

    boolean equals(Object object);
}
