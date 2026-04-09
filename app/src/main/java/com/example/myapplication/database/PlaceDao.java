package com.example.myapplication.database;

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

    @Query("SELECT * FROM places")
    LiveData<List<Place>> getAllPlaces();

    @Query("SELECT * FROM places")
    List<Place> getAllPlacesSync();

    @Query("SELECT * FROM places WHERE id = :id")
    Place getPlaceById(long id);
}
