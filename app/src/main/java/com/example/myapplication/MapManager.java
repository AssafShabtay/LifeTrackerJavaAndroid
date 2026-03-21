package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.db.MovementActivity;
import com.example.myapplication.db.StillLocation;
import com.example.myapplication.db.TimelineItem;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapManager implements OnMapReadyCallback {
    private static final String TAG = "MapManager";
    private final AppCompatActivity activity;
    private final int fragmentId;
    private GoogleMap mMap;
    private View mapFragmentView;

    public MapManager(AppCompatActivity activity, int fragmentId) {
        this.activity = activity;
        this.fragmentId = fragmentId;
    }

    public void init() {
        mapFragmentView = activity.findViewById(fragmentId);
        SupportMapFragment mapFragment = (SupportMapFragment) activity.getSupportFragmentManager()
                .findFragmentById(fragmentId);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        updateMyLocationEnabled();
    }

    public void onResume() {
        updateMyLocationEnabled();
    }

    public void setVisibility(int visibility) {
        if (mapFragmentView != null) {
            mapFragmentView.setVisibility(visibility);
        }
    }

    public void focusOnItem(TimelineItem item) {
        if (mMap == null) return;

        mMap.clear();

        if (item instanceof StillLocation) {
            StillLocation still = (StillLocation) item;
            if (still.lat != null && still.lng != null) {
                LatLng pos = new LatLng(still.lat, still.lng);
                mMap.addMarker(new MarkerOptions().position(pos).title("Still Location"));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
            }
        } else if (item instanceof MovementActivity) {
            MovementActivity movement = (MovementActivity) item;
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            boolean hasPoints = false;

            if (movement.startLat != null && movement.startLng != null) {
                LatLng start = new LatLng(movement.startLat, movement.startLng);
                mMap.addMarker(new MarkerOptions().position(start).title("Start"));
                builder.include(start);
                hasPoints = true;
            }
            if (movement.endLat != null && movement.endLng != null) {
                LatLng end = new LatLng(movement.endLat, movement.endLng);
                mMap.addMarker(new MarkerOptions().position(end).title("End"));
                builder.include(end);
                hasPoints = true;
            }

            if (hasPoints) {
                try {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                } catch (IllegalStateException e) {
                    // In case view hasn't laid out yet or points are too close
                    if (movement.startLat != null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(movement.startLat, movement.startLng), 15f));
                    }
                }
            }
        }
    }

    private void updateMyLocationEnabled() {
        if (mMap != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException setting my location enabled", e);
            }
        }
    }
}
