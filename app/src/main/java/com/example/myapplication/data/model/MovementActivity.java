package com.example.myapplication.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity(tableName = "movement_activities")
public class MovementActivity extends BaseActivity {
    private Double startLat;
    private Double startLng;
    private Double endLat;
    private Double endLng;

    @Ignore
    private List<StillLocation> stops = new ArrayList<>();

    public MovementActivity copy() {
        return copyWithTypeName(getStoredTypeName());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MovementActivity that = (MovementActivity) o;
        return id == that.id && Objects.equals(getStoredTypeName(), that.getStoredTypeName()) && Objects.equals(startLat, that.startLat) && Objects.equals(startLng, that.startLng) && Objects.equals(endLat, that.endLat) && Objects.equals(endLng, that.endLng) && Objects.equals(startTimeDate, that.startTimeDate) && Objects.equals(endTimeDate, that.endTimeDate) && Objects.equals(stops, that.stops);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getStoredTypeName(), startLat, startLng, endLat, endLng, startTimeDate, endTimeDate, stops);
    }

    public String getActivityTypeName() {
        return storedTypeName;
    }


    @androidx.annotation.ColorRes
    public int getColorRes() {
        return R.color.activity_walking;
    }

    @androidx.annotation.DrawableRes
    public int getIconRes() {
        return R.drawable.ic_walk;
    }

    public static MovementActivity createFromTypeName(String name) {
        String type = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        MovementActivity activity;
        if (type.contains("driving") || type.contains("vehicle")) activity = new DrivingActivity();
        else if (type.contains("running")) activity = new RunningActivity();
        else if (type.contains("cycling") || type.contains("bicycle")) activity = new CyclingActivity();
        else if (type.contains("walking") || type.contains("foot")) activity = new WalkingActivity();
        else return new UnknownMovementActivity(name);
        // Preserve legacy spellings/aliases for existing SQL equality queries.
        if (!Objects.equals(name, activity.getActivityTypeName())) activity.storedTypeName = name;
        return activity;
    }
    @ColumnInfo(name = "activityTypeName")
    private String storedTypeName;

    public final String getStoredTypeName() {
        return storedTypeName == null ? getActivityTypeName() : storedTypeName;
    }

    public void setStoredTypeName(String name) {
        storedTypeName = name;
    }

    public MovementActivity toActivity() {
        return copyWithTypeName(getStoredTypeName());
    }

    public MovementActivity copyWithTypeName(String name) {
        MovementActivity target = createFromTypeName(name);
        copyBaseTo(target);
        target.startLat = startLat;
        target.startLng = startLng;
        target.endLat = endLat;
        target.endLng = endLng;
        if (stops == null) target.stops = null;
        else {
            target.stops = new ArrayList<>();
            for (StillLocation stop : stops) target.stops.add(stop.copy());
        }
        return target;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLng() {
        return startLng;
    }

    public void setStartLng(Double startLng) {
        this.startLng = startLng;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public Double getEndLng() {
        return endLng;
    }

    public void setEndLng(Double endLng) {
        this.endLng = endLng;
    }

    public List<StillLocation> getStops() {
        return stops;
    }

    public void setStops(List<StillLocation> stops) {
        this.stops = stops;
    }
}
