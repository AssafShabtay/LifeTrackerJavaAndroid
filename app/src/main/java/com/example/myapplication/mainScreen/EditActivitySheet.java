package com.example.myapplication.mainScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getStillColor;

import android.app.TimePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager; // Import WindowManager
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.UiFormatters;
import com.example.myapplication.helpers.PlaceAutocompleteHelper; // Import the new helper
import com.example.myapplication.helpers.ColorAndIcons; // Import ColorAndIcons
import com.example.myapplication.database.Place; // Added import for Place
import com.example.myapplication.database.PlaceDao; // Added import for PlaceDao
import com.example.myapplication.database.ActivityDatabase; // Added import for ActivityDatabase
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
//import com.google.android.libraries.places.api.model.AutocompleteSessionToken; // Can remove if not used elsewhere
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class EditActivitySheet extends BottomSheetDialogFragment {

    public interface OnVisitUpdatedListener {
        void onUpdate(StillLocation still);
    }

    private StillLocation still;
    private OnVisitUpdatedListener listener;
    private PlacesClient placesClient;
    private PlaceDao placeDao; // Added PlaceDao member variable

    private AutoCompleteTextView actvName;
    private AutoCompleteTextView etAddress;
    private Button btnStartTime, btnEndTime;

    private View layoutIconPicker;
    private View viewSelectedColor;
    private ImageView ivSelectedIcon;
    private Date editedStartTime, editedEndTime;
    private Integer selectedColor;
    private String selectedIcon;

    public static EditActivitySheet newInstance(StillLocation still, OnVisitUpdatedListener listener) {
        EditActivitySheet fragment = new EditActivitySheet();
        fragment.still = still;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.edit_activity_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("EditActivitySheet", "onViewCreated called.");

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(requireContext(), BuildConfig.GOOGLE_API_KEY);
        }
        placesClient = Places.createClient(requireContext());

        // Initialize PlaceDao
        placeDao = ActivityDatabase.getDatabase(requireContext()).placeDao();

        actvName = view.findViewById(R.id.actvName);
        etAddress = view.findViewById(R.id.etAddress);
        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);

        layoutIconPicker = view.findViewById(R.id.layoutIconPicker);
        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);

        View btnSave = view.findViewById(R.id.btnSave);

        // Initialize state
        editedStartTime = still.getStartTimeDate();
        editedEndTime = still.getEndTimeDate();

        selectedColor = getStillColor(still, requireContext());

        selectedIcon = still.getIcon() != null ? still.getIcon() : "Still";

        // listener for icon picker dialog
        getChildFragmentManager().setFragmentResultListener("icon_picker_request", this, (requestKey, bundle) -> {
            selectedIcon = bundle.getString("selectedIcon");
            selectedColor = bundle.getInt("selectedColor");
            updateIconAndColorUi();
        });

        // Set place name and address
        if (still.getPlaceName() != null) actvName.setText(still.getPlaceName());
        if (still.getPlaceAddress() != null) etAddress.setText(still.getPlaceAddress());

        updateIconAndColorUi();
        fetchNearbyPlaceSuggestions();
        new PlaceAutocompleteHelper(requireContext(), etAddress);

        // Set up icon picker
        if (layoutIconPicker != null) {
            layoutIconPicker.setOnClickListener(v -> showIconPickerDialog());
        }

        btnStartTime.setOnClickListener(v -> showTimePicker(true));
        btnEndTime.setOnClickListener(v -> showTimePicker(false));

        updateTimeButtons();

        btnSave.setOnClickListener(v -> {
            still.setPlaceName(actvName.getText().toString().trim());
            still.setPlaceAddress(etAddress.getText().toString().trim());
            still.setStartTimeDate(editedStartTime);
            still.setEndTimeDate(editedEndTime);
            still.setIcon(selectedIcon);
            still.setColor(selectedColor);

            // Create and save new Place
            Place newPlace = new Place();
            newPlace.setName(actvName.getText().toString().trim());
            newPlace.setAddress(etAddress.getText().toString().trim());
            if (still.getLat() != null && still.getLng() != null) {
                newPlace.setLat(still.getLat());
                newPlace.setLng(still.getLng());
            }
            newPlace.setIcon(selectedIcon);
            newPlace.setColor(selectedColor);
            newPlace.setCategory(null); // Or derive from icon if possible, but for now null.

            // TODO: Room database operations should be done on a background thread.
            // For simplicity, directly calling here, but consider AsyncTask or Coroutines for production.
            long placeId = placeDao.insertPlace(newPlace);
            still.setPlaceId(placeId); // Set the generated placeId to StillLocation

            if (listener != null) {
                listener.onUpdate(still);
            }
            dismiss();
        });
    }

    // Add this method to handle keyboard behavior
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    private void showIconPickerDialog() {
        Log.d("EditActivitySheet", "showIconPickerDialog called.");
        IconPickerDialog dialog = IconPickerDialog.newInstance(selectedIcon, selectedColor);
        dialog.show(getChildFragmentManager(), "icon_picker");
    }

    private void updateIconAndColorUi() {
        Log.d("EditActivitySheet", "updateIconAndColorUi called. Selected Icon: " + selectedIcon + ", Selected Color: " + String.format("#%06X", (0xFFFFFF & selectedColor)));

        // Update color preview
        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(selectedColor & 0x20FFFFFF);
            viewSelectedColor.setBackground(gd);
            Log.d("EditActivitySheet", "Color preview updated.");
        }

        // Update icon preview
        if (ivSelectedIcon != null) {
            // Create a dummy StillLocation to pass to getStillIconRes
            StillLocation tempStill = new StillLocation();
            tempStill.setIcon(selectedIcon);
            int iconRes = ColorAndIcons.getStillIconRes(tempStill);

            ivSelectedIcon.setImageResource(iconRes);
            ivSelectedIcon.setColorFilter(selectedColor); // Set color filter to apply the selected color
            Log.d("EditActivitySheet", "Icon preview updated with resource: " + iconRes + " and color: " + String.format("#%06X", (0xFFFFFF & selectedColor)));
        }
    }

    private void fetchNearbyPlaceSuggestions() {
        if (still.getLat() == null || still.getLng() == null) return;

        List<com.google.android.libraries.places.api.model.Place.Field> placeFields = Arrays.asList(com.google.android.libraries.places.api.model.Place.Field.DISPLAY_NAME, com.google.android.libraries.places.api.model.Place.Field.ID);
        CircularBounds circle = CircularBounds.newInstance(new LatLng(still.getLat(), still.getLng()), 100.0);
        SearchNearbyRequest request = SearchNearbyRequest.builder(circle, placeFields)
                .setMaxResultCount(10)
                .build();

        placesClient.searchNearby(request)
                .addOnSuccessListener(response -> {
                    List<String> names = new ArrayList<>();
                    for (com.google.android.libraries.places.api.model.Place p : response.getPlaces()) {
                        names.add(p.getDisplayName());
                    }
                    if (isAdded()) {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                                android.R.layout.simple_dropdown_item_1line, names);
                        actvName.setAdapter(adapter);
                    }
                })
                .addOnFailureListener(e -> {
                    // Silently fail
                });
    }

    private void updateTimeButtons() {
        btnStartTime.setText("Start: " + UiFormatters.timeOnly(editedStartTime));
        btnEndTime.setText("End: " + UiFormatters.timeOnly(editedEndTime));

        boolean isOngoing = (editedEndTime == null);
        btnEndTime.setEnabled(!isOngoing);
        btnEndTime.setAlpha(isOngoing ? 0.5f : 1.0f);
    }

    private void showTimePicker(boolean isStart) {
        if (!isStart && editedEndTime == null) return;

        Date baseDate = isStart ? editedStartTime : editedEndTime;
        if (baseDate == null) baseDate = new Date();

        Calendar cal = Calendar.getInstance();
        cal.setTime(baseDate);

        TimePickerDialog picker = new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            Calendar newCal = Calendar.getInstance();
            Date currentVal = isStart ? editedStartTime : editedEndTime;
            newCal.setTime(currentVal != null ? currentVal : new Date());
            newCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            newCal.set(Calendar.MINUTE, minute);

            if (isStart) editedStartTime = newCal.getTime();
            else editedEndTime = newCal.getTime();

            updateTimeButtons();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
        picker.show();
    }
}