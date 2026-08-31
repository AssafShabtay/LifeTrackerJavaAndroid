package com.example.myapplication.mainScreen.homeScreen;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.myapplication.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_ADDRESS = "extra_address";
    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";

    private GoogleMap mMap;
    private TextView tvAddress;
    private LatLng selectedLatLng;
    private String selectedAddress;
    private Geocoder geocoder;
    private MaterialButton btnConfirm;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvAddress = findViewById(R.id.tv_current_address);
        btnConfirm = findViewById(R.id.btn_confirm_location);
        btnConfirm.setEnabled(false);

        geocoder = new Geocoder(this, Locale.getDefault());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnConfirm.setOnClickListener(v -> {
            if (selectedAddress != null && selectedLatLng != null) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_ADDRESS, selectedAddress);
                resultIntent.putExtra(EXTRA_LAT, selectedLatLng.latitude);
                resultIntent.putExtra(EXTRA_LNG, selectedLatLng.longitude);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(false); // Clean UI: hide default zoom controls
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setCompassEnabled(false);

        // Try to center on user's current location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f));
                } else {
                    centerOnDefault();
                }
            });
        } else {
            centerOnDefault();
        }

        mMap.setOnCameraMoveStartedListener(reason -> {
            btnConfirm.setEnabled(false);
            tvAddress.setText("Searching...");
        });

        mMap.setOnCameraIdleListener(() -> {
            selectedLatLng = mMap.getCameraPosition().target;
            updateAddress(selectedLatLng);
        });
    }

    private void centerOnDefault() {
        // Default location: Tel Aviv
        LatLng defaultLocation = new LatLng(32.0853, 34.7818);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f));
    }

    private void updateAddress(LatLng latLng) {
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        selectedAddress = address.getAddressLine(0);
                        tvAddress.setText(selectedAddress);
                        btnConfirm.setEnabled(true);
                    } else {
                        selectedAddress = null;
                        tvAddress.setText("Address not found");
                        btnConfirm.setEnabled(false);
                    }
                });
            } catch (IOException e) {
                Log.e("MapPickerActivity", "Geocoder error", e);
                runOnUiThread(() -> {
                    selectedAddress = null;
                    tvAddress.setText("Error finding address");
                    btnConfirm.setEnabled(false);
                });
            }
        }).start();
    }
}
