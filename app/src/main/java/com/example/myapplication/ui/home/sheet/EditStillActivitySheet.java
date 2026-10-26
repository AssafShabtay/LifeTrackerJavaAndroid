package com.example.myapplication.ui.home.sheet;

import static com.example.myapplication.ui.general.ColorAndIcons.getStillColor;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.myapplication.data.model.Place;
import com.example.myapplication.data.model.StillLocation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class EditStillActivitySheet extends BaseStillActivitySheet {

    // listener for communicating update delete operations
    public interface OnVisitChangeListener {
        void onUpdate(StillLocation still);
        void onDelete(StillLocation still);
    }

    private static final String TAG = "EditStillActivitySheet";
    private StillLocation still;
    private OnVisitChangeListener listener;
    private MaterialButton btnDelete;

    public static EditStillActivitySheet newInstance(StillLocation still, OnVisitChangeListener listener) {
        EditStillActivitySheet fragment = new EditStillActivitySheet();
        fragment.still = still;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.edit_still_activity_sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        btnDelete = view.findViewById(R.id.btnDelete);
        super.onViewCreated(view, savedInstanceState);

        // Delete activity with confirmation dialog
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Activity?")
                    .setMessage("Are you sure you want to delete this activity? This action cannot be undone.")
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (listener != null) {
                            listener.onDelete(still);
                        }
                        dismiss();
                    })
                    .show();
            });
        }
    }

    @Override
    protected void onInitializeData() {
        // Load values from the StillLocation object
        editedStartTime = still.getStartTimeDate();
        editedEndTime = still.getEndTimeDate();

        selectedColor = getStillColor(still, requireContext());
        selectedCategory = still.getCategory();
        selectedIcon = still.getIcon() != null ? still.getIcon() : "Still";
        selectedGeofenceId = still.getGeofencePlaceId();

        // Fetch linked Place entity if it exists
        if (still.getPlaceId() != null) {
            app.getBackgroundWriteExecutor().execute(() -> {
                selectedPlace = placeDao.getPlaceById(still.getPlaceId());
            });
        }

        // Set place name and address
        if (still.getPlaceName() != null) actvName.setText(still.getPlaceName());
        if (still.getPlaceAddress() != null) etAddress.setText(still.getPlaceAddress());

        // Find existing saved places near current coordinates (within 75 meters)
        if (still.getLat() != null && still.getLng() != null) {
            double initialLat = still.getLat();
            double initialLng = still.getLng();
            app.getBackgroundWriteExecutor().execute(() -> {
                List<Place> allPlaces = placeDao.getAllPlaces();
                List<Place> nearbyPlaces = new ArrayList<>();
                for (Place p : allPlaces) {
                    float[] results = new float[1];
                    Location.distanceBetween(initialLat, initialLng, p.getLat(), p.getLng(), results);
                    if (results[0] <= 75) {
                        nearbyPlaces.add(p);
                    }
                }
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> setupPlacesDropdown(initialLat, initialLng, nearbyPlaces));
                }
            });
        }
    }

    @Override
    protected void onSaveClicked() {
        // Save updated visit details
        String name = actvName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        Context context = requireContext().getApplicationContext();

        app.getBackgroundWriteExecutor().execute(() -> {
            still.setPlaceName(name);
            still.setPlaceAddressAndUpdateCoordinates(address, context);
            still.setStartTimeDate(editedStartTime);
            still.setEndTimeDate(editedEndTime);
            still.setIcon(selectedIcon);
            still.setColor(selectedColor);
            still.setCategory(selectedCategory);
            still.setGeofencePlaceId(selectedGeofenceId);

            if (selectedPlace != null && selectedPlace.getId() != 0) {
                still.setPlaceId(selectedPlace.getId());
            } else {
                // Create and save a new Place
                Place newPlace = selectedPlace != null ? selectedPlace : new Place();
                newPlace.setName(name);
                newPlace.setAddress(address);
                if (still.getLat() != null && still.getLng() != null) {
                    newPlace.setLat(still.getLat());
                    newPlace.setLng(still.getLng());
                }
                newPlace.setIcon(selectedIcon);
                newPlace.setColor(selectedColor);
                newPlace.setCategory(selectedCategory);
                newPlace.setGeofencePlaceId(selectedGeofenceId);
                long placeId = placeDao.insertPlace(newPlace);
                still.setPlaceId(placeId);
            }

            if (listener != null) {
                listener.onUpdate(still);
            }

            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(this::dismiss);
            }
        });
    }
}
