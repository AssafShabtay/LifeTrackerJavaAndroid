package com.example.myapplication.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.util.Date;
import java.util.Objects;
@Entity(tableName = "still_locations",
        foreignKeys = @ForeignKey(
                entity = Place.class,
                parentColumns = "id",
                childColumns = "placeId",
                onDelete = ForeignKey.SET_NULL
        )
)
public class StillLocation implements TimelineItem {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private Double lat;
    private Double lng;
    private Date startTimeDate;
    private Date endTimeDate;
    private String wasSupposedToBeActivity;

    @ColumnInfo(index = true)
    private Long placeId;

    private String placeName;
    private String category;
    private String icon;
    private Integer color;
    private boolean isStop;
    private String placeAddress;
    private String geofencePlaceId;



    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StillLocation that = (StillLocation) o;
        return id == that.id && isStop == that.isStop && Objects.equals(lat, that.lat) && Objects.equals(lng, that.lng) && Objects.equals(startTimeDate, that.startTimeDate) && Objects.equals(endTimeDate, that.endTimeDate) && Objects.equals(wasSupposedToBeActivity, that.wasSupposedToBeActivity) && Objects.equals(placeId, that.placeId) && Objects.equals(placeName, that.placeName) && Objects.equals(category, that.category) && Objects.equals(icon, that.icon) && Objects.equals(color, that.color) && Objects.equals(placeAddress, that.placeAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lat, lng, startTimeDate, endTimeDate, wasSupposedToBeActivity, placeId, placeName, category, icon, color, isStop, placeAddress);
    }
    public String getGeofencePlaceId() {
        return geofencePlaceId;
    }

    public void setGeofencePlaceId(String geofencePlaceId) {
        this.geofencePlaceId = geofencePlaceId;
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    @Override
    public Date getStartTimeDate() {
        return startTimeDate;
    }

    public void setStartTimeDate(Date startTimeDate) {
        this.startTimeDate = startTimeDate;
    }

    @Override
    public Date getEndTimeDate() {
        return endTimeDate;
    }

    public void setEndTimeDate(Date endTimeDate) {
        this.endTimeDate = endTimeDate;
    }

    public String getWasSupposedToBeActivity() {
        return wasSupposedToBeActivity;
    }

    public void setWasSupposedToBeActivity(String wasSupposedToBeActivity) {
        this.wasSupposedToBeActivity = wasSupposedToBeActivity;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    public boolean getIsStop() {
        return isStop;
    }

    public void setIsStop(boolean stop) {
        isStop = stop;
    }

    public String getPlaceAddress() {
        return placeAddress;
    }

    public void setPlaceAddress(String placeAddress) {
        this.placeAddress = placeAddress;
    }
}