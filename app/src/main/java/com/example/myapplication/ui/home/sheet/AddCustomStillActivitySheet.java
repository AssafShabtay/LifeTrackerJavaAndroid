package com.example.myapplication.ui.home.sheet;

import android.app.Activity;
import android.graphics.Color;

import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.Place;
import com.example.myapplication.data.model.StillLocation;

import java.util.Calendar;
import java.util.Date;

public class AddCustomStillActivitySheet extends BaseStillActivitySheet {

    private static final String TAG = "AddCustomStillActivitySheet";

    // listener for communicating activity added operations
    public interface OnActivityAddedListener {
        void onActivityAdded();
    }

    private OnActivityAddedListener listener;
    private Date selectedDate;

    public static AddCustomStillActivitySheet newInstance(Date date, OnActivityAddedListener listener) {
        AddCustomStillActivitySheet fragment = new AddCustomStillActivitySheet();
        fragment.selectedDate = date;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.add_custom_activity_sheet;
    }

    @Override
    protected void onInitializeData() {
        // Initialize default start and end times 12:00 PM to 1:00 PM
        Calendar cal = Calendar.getInstance();
        if (selectedDate != null) cal.setTime(selectedDate);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        editedStartTime = cal.getTime();

        cal.add(Calendar.HOUR_OF_DAY, 1);
        editedEndTime = cal.getTime();

        selectedColor = Color.parseColor("#4CAF50");
        selectedIcon = "Still";
    }

    @Override
    protected void onSaveClicked() {
        // Save new activity details
        String name = actvName.getText().toString().trim();
        if (name.isEmpty()) {
            name = "Custom Activity";
        }

        final String finalName = name;
        final String address = etAddress.getText().toString().trim();

        app.getBackgroundWriteExecutor().execute(() -> {
            StillLocation newStill = new StillLocation();
            newStill.setPlaceName(finalName);
            newStill.setPlaceAddressAndUpdateCoordinates(address, requireContext());
            newStill.setStartTimeDate(editedStartTime);
            newStill.setEndTimeDate(editedEndTime);
            newStill.setIcon(selectedIcon);
            newStill.setColor(selectedColor);
            newStill.setCategory(selectedCategory);
            newStill.setGeofencePlaceId(selectedGeofenceId);

            if (selectedPlace != null) {
                newStill.setPlaceId(selectedPlace.getId());
            } else {
                // Create and save a new Place
                Place newPlace = new Place();
                newPlace.setName(finalName);
                newPlace.setAddress(address);
                newPlace.setIcon(selectedIcon);
                newPlace.setColor(selectedColor);
                newPlace.setCategory(selectedCategory);
                newPlace.setGeofencePlaceId(selectedGeofenceId);
                long placeId = placeDao.insertPlace(newPlace);
                newStill.setPlaceId(placeId);
            }

            ActivityDatabase.getDatabase(requireContext()).activityDao().insertStillLocation(newStill);

            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onActivityAdded();
                    if (isAdded()) dismiss();
                });
            }
        });
    }
}
