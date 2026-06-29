package com.example.myapplication.database;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPlace(Place place);

    @Update
    void updatePlace(Place place);

    @Delete
    void deletePlace(Place place);
    @NonNull
    @Query("SELECT * FROM places")
    LiveData<List<Place>> getAllPlaces();

    @NonNull
    @Query("SELECT * FROM places")
    List<Place> getAllPlacesSync();

    @Query("SELECT * FROM places WHERE id = :id")
    Place getPlaceById(long id);

    @Query("SELECT * FROM places WHERE category = 'Home' LIMIT 1")
    Place getHomePlace();
}
