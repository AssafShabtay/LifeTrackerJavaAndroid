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

    public GeofenceUtilsManager(PlaceDao placeDao, Context context, PlacesClient placesClient) {
        this.placeDao = placeDao;
        this.context = context;
        this.placesClient = placesClient;
    }


    void findPlaceAndUpdateStill(Location currentLocation, StillLocation still) {
        Place nearby = findNearbyPlace(currentLocation.getLatitude(), currentLocation.getLongitude());
        if (nearby != null) {
            // Use geofence data
            still.placeId = String.valueOf(nearby.id);
            still.placeName = nearby.name;
            still.icon = nearby.category;
            still.placeAddress = nearby.address;
            still.category = nearby.category;
            still.lat = nearby.lat;
            still.lng = nearby.lng;
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

            // Define a 50-meter circular search area around the user\'s current location
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
                com.google.android.libraries.places.api.model.Place place = response.getPlaces().get(0);

                // Extract place data
                still.placeName = place.getDisplayName();
                still.placeId = place.getId();
                still.placeAddress = place.getFormattedAddress();

                // Map Google Types to your icon/category if needed
                if (place.getPlaceTypes() != null && !place.getPlaceTypes().isEmpty()) {
                    still.icon = place.getPlaceTypes().get(0);
                }

                String msg = String.format(Locale.US, "Google Places detected: %s at [%.6f, %.6f]", still.placeName, location.getLatitude(), location.getLongitude());
                Log.d(TAG, msg);
                Logger.saveLog(context, msg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to detect Google Place", e);
        }
    }

    private Place findNearbyPlace(double lat, double lng) {
        List<Place> places = placeDao.getAllPlacesSync();
        for (Place p : places) {
            float dist = distanceInMeters(lat, lng, p.lat, p.lng);
            if (dist < 75f) {
                return p;
            }
        }
        return null;
    }
}
