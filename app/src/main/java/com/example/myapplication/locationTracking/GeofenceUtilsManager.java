package com.example.myapplication.locationTracking;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.distanceInMeters;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.Logger;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.libraries.places.api.net.SearchNearbyResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GeofenceUtilsManager {
    private static final String TAG = "GeofenceUtilsManager";
    private final PlaceDao placeDao;
    private final Context context;
    private final PlacesClient placesClient;
    private final GeofenceManager geofenceManager;

    public GeofenceUtilsManager(PlaceDao placeDao, Context context, PlacesClient placesClient, GeofenceManager geofenceManager) {
        this.placeDao = placeDao;
        this.context = context;
        this.placesClient = placesClient;
        this.geofenceManager = geofenceManager;
    }


    void findPlaceAndUpdateStill(Location currentLocation, StillLocation still) {
        Place nearby = findNearbyPlace(currentLocation.getLatitude(), currentLocation.getLongitude());
        if (nearby != null) {
            // Use geofence data
            still.setPlaceId(nearby.getId());
            still.setPlaceName(nearby.getName());
            still.setIcon(nearby.getIcon());
            still.setPlaceAddress(nearby.getAddress());
            still.setCategory(nearby.getCategory());
            still.setLat(nearby.getLat());
            still.setLng(nearby.getLng());
            still.setColor(nearby.getColor());
        } else {
            // Otherwise, use Google places
            detectGooglePlace(still, currentLocation);
        }
    }

    private void detectGooglePlace(StillLocation still, Location location) {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            List<com.google.android.libraries.places.api.model.Place.Field> placeFields =
                    Arrays.asList(
                            com.google.android.libraries.places.api.model.Place.Field.DISPLAY_NAME,
                            com.google.android.libraries.places.api.model.Place.Field.ID,
                            com.google.android.libraries.places.api.model.Place.Field.TYPES,
                            com.google.android.libraries.places.api.model.Place.Field.FORMATTED_ADDRESS);

            // Define a 50-meter circular search area around the user's current location
            CircularBounds circle = CircularBounds.newInstance(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    50.0
            );

            // Request Google Place search using Nearby Search (New API)
            SearchNearbyRequest request = SearchNearbyRequest.builder(circle, placeFields)
                    .setMaxResultCount(1) // We only need the top result
                    .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
                    .build();

            SearchNearbyResponse response = Tasks.await(placesClient.searchNearby(request), 5, TimeUnit.SECONDS);

            if (response != null && !response.getPlaces().isEmpty()) {

                // Extract the closest place
                com.google.android.libraries.places.api.model.Place googlePlace = response.getPlaces().get(0);

                Place newPlace = new Place();
                newPlace.setName(googlePlace.getDisplayName());
                newPlace.setAddress(googlePlace.getFormattedAddress());

                    newPlace.setLat(location.getLatitude()); // Fallback to current location if Google Place doesn't have LatLng
                    newPlace.setLng(location.getLongitude());


                if (googlePlace.getPlaceTypes() != null && !googlePlace.getPlaceTypes().isEmpty()) {
                    newPlace.setIcon(googlePlace.getPlaceTypes().get(0));
                    newPlace.setCategory(googlePlace.getPlaceTypes().get(0)); // Assuming category is the first type
                }
                newPlace.setGeofencePlaceId(googlePlace.getId());
                long newPlaceId = placeDao.insertPlace(newPlace);
                newPlace.setId(newPlaceId);

                // Update still location with newly created place data
                still.setPlaceId(newPlace.getId());
                still.setPlaceName(newPlace.getName());
                still.setIcon(newPlace.getIcon());
                still.setPlaceAddress(newPlace.getAddress());
                still.setCategory(newPlace.getCategory());
                still.setLat(newPlace.getLat());
                still.setLng(newPlace.getLng());
                still.setColor(newPlace.getColor());
                still.setGeofencePlaceId(newPlace.getGeofencePlaceId());

                // Add a geofence for the newly created place
                geofenceManager.addGeofence(newPlace);

                String msg = String.format(Locale.US, "Google Places detected and new Place created: %s at [%.6f, %.6f]", still.getPlaceName(), location.getLatitude(), location.getLongitude());
                Log.d(TAG, msg);
                Logger.saveLog(context, msg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to detect Google Place", e);
        }
    }

    private Place findNearbyPlace(double lat, double lng) {
        List<Place> places = placeDao.getAllPlaces();
        for (Place p : places) {
            float dist = distanceInMeters(lat, lng, p.getLat(), p.getLng());
            if (dist < 75f) {
                return p;
            }
        }
        return null;
    }
}