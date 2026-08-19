package com.example.myapplication.mainScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getStillColor;
import static com.example.myapplication.helpers.UiFormatters.category;

import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.AddressAutocompleteHelper;
import com.example.myapplication.helpers.UiFormatters;
import com.example.myapplication.helpers.ColorAndIcons;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.ActivityDatabase;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class AddCustomActivitySheet extends BottomSheetDialogFragment {

    public interface OnActivityAddedListener {
        void onActivityAdded();
    }

    private OnActivityAddedListener listener;
    private PlacesClient placesClient;
    private PlaceDao placeDao;
    private AddressAutocompleteHelper addressAutocompleteHelper;

    private AutoCompleteTextView actvName;
    private AutoCompleteTextView etAddress;
    private Button btnStartTime, btnEndTime;

    private View layoutIconPicker;
    private View viewSelectedColor;
    private ImageView ivSelectedIcon;

    private Date editedStartTime, editedEndTime;
    private Integer selectedColor;
    private String selectedIcon;
    private Place selectedPlace;
    private String selectedCategory;
    private String selectedGeofenceId;
    private LifeTrackerApp app;
    private Date selectedDate;

    public static AddCustomActivitySheet newInstance(Date date, OnActivityAddedListener listener) {
        AddCustomActivitySheet fragment = new AddCustomActivitySheet();
        fragment.selectedDate = date;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.add_custom_activity_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(requireContext(), BuildConfig.GOOGLE_API_KEY);
        }
        placesClient = Places.createClient(requireContext());

        placeDao = ActivityDatabase.getDatabase(requireContext()).placeDao();

        actvName = view.findViewById(R.id.actvName);
        etAddress = view.findViewById(R.id.etAddress);
        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);

        layoutIconPicker = view.findViewById(R.id.layoutIconPicker);
        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);

        View btnSave = view.findViewById(R.id.btnSave);

        app = (LifeTrackerApp) requireActivity().getApplication();

        // Initialize state
        Calendar cal = Calendar.getInstance();
        if (selectedDate != null) cal.setTime(selectedDate);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        editedStartTime = cal.getTime();

        cal.add(Calendar.HOUR_OF_DAY, 1);
        editedEndTime = cal.getTime();

        selectedColor = Color.parseColor("#4CAF50");
        selectedCategory = null;
        selectedIcon = "Still";

        getChildFragmentManager().setFragmentResultListener("icon_picker_request", this, (requestKey, bundle) -> {
            selectedIcon = bundle.getString("selectedIcon");
            selectedColor = bundle.getInt("selectedColor");
            updateIconAndColorUi();
        });

        updateIconAndColorUi();
        addressAutocompleteHelper = new AddressAutocompleteHelper(requireContext(), etAddress);

        if (layoutIconPicker != null) {
            layoutIconPicker.setOnClickListener(v -> showIconPickerDialog());
        }

        btnStartTime.setOnClickListener(v -> showTimePickerDialog(true));
        btnEndTime.setOnClickListener(v -> showTimePickerDialog(false));

        updateTimeButtons();

        btnSave.setOnClickListener(v -> {
            String name = actvName.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Custom Activity";
            }

            final String finalName = name;
            final String address = etAddress.getText().toString().trim();

            app.getDatabaseWriteExecutor().execute(() -> {
                StillLocation newStill = new StillLocation();
                newStill.setPlaceName(finalName);
                newStill.setPlaceAddress(address);
                newStill.setStartTimeDate(editedStartTime);
                newStill.setEndTimeDate(editedEndTime);
                newStill.setIcon(selectedIcon);
                newStill.setColor(selectedColor);
                newStill.setCategory(selectedCategory);
                newStill.setGeofencePlaceId(selectedGeofenceId);

                // Add default lat lng if 0 (we don't have location here unless we select from map, using 0 for custom)
                newStill.setLat(0.0);
                newStill.setLng(0.0);

                if (selectedPlace != null) {
                    newStill.setPlaceId(selectedPlace.getId());
                } else {
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

                requireActivity().runOnUiThread(() -> {
                    if (listener != null) listener.onActivityAdded();
                    dismiss();
                });
            });
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void showIconPickerDialog() {
        IconPickerDialog dialog = IconPickerDialog.newInstance(selectedIcon, selectedColor);
        dialog.show(getChildFragmentManager(), "icon_picker");
    }

    private void updateIconAndColorUi() {
        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(selectedColor & 0x20FFFFFF);
            viewSelectedColor.setBackground(gd);
        }

        if (ivSelectedIcon != null) {
            StillLocation tempStill = new StillLocation();
            tempStill.setIcon(selectedIcon);
            int iconRes = ColorAndIcons.getStillIconRes(tempStill);

            ivSelectedIcon.setImageResource(iconRes);
            ivSelectedIcon.setColorFilter(selectedColor);
        }
    }

    private void updateTimeButtons() {
        btnStartTime.setText("Start: " + UiFormatters.timeOnly(editedStartTime));
        btnEndTime.setText("End: " + UiFormatters.timeOnly(editedEndTime));
    }

    private void showTimePickerDialog(boolean isStart) {
        Date initialDate = isStart ? editedStartTime : editedEndTime;
        final Date baseDate = (initialDate == null) ? new Date() : initialDate;


        Calendar cal = Calendar.getInstance();
        cal.setTime(baseDate);

        TimePickerDialog picker = new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            Calendar newCal = Calendar.getInstance();
            newCal.setTime(baseDate);
            newCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            newCal.set(Calendar.MINUTE, minute);
            newCal.set(Calendar.SECOND, 0);

            if (isStart) editedStartTime = newCal.getTime();
            else editedEndTime = newCal.getTime();

            updateTimeButtons();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
        picker.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (placesClient != null) {
            placesClient = null;
        }
        if (addressAutocompleteHelper != null) {
            addressAutocompleteHelper.release();
            addressAutocompleteHelper = null;
        }
    }
}