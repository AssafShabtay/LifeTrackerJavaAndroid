package com.example.myapplication.mainScreen;

import android.app.TimePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.myapplication.BuildConfig;
import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.UiFormatters;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
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

    private AutoCompleteTextView actvName;
    private AutoCompleteTextView etAddress;
    private Button btnStartTime, btnEndTime;

    private View layoutIconPicker;
    private View viewSelectedColor;
    private ImageView ivSelectedIcon;


    private Date editedStartTime, editedEndTime;
    private Integer selectedColor;
    private String selectedIcon;

    private List<AutocompletePrediction> lastPredictions = new ArrayList<>();

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
        Log.d("EditActivitySheet", "onViewCreated called."); // Added log

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(requireContext(), BuildConfig.GOOGLE_API_KEY);
        }
        placesClient = Places.createClient(requireContext());

        actvName = view.findViewById(R.id.actvName);
        etAddress = view.findViewById(R.id.etAddress);
        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);

        layoutIconPicker = view.findViewById(R.id.layoutIconPicker);
        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);


        View btnSave = view.findViewById(R.id.btnSave);

        // Initialize state
        editedStartTime = still.startTimeDate;
        editedEndTime = still.endTimeDate;
        selectedColor = still.color != null ? still.color : 0xFF9E9E9E;
        selectedIcon = still.icon != null ? still.icon : "Still";

        // Populate UI
        if (still.placeName != null) actvName.setText(still.placeName);
        if (still.placeCoords != null) etAddress.setText(still.placeCoords);
        else if (still.lat != null && still.lng != null) etAddress.setText(still.lat + ", " + still.lng);

        updateIconAndColorUi();
        fetchNearbyPlaceSuggestions();
        setupAddressAutocomplete();

        if (layoutIconPicker != null) {
            layoutIconPicker.setOnClickListener(v -> showIconPickerDialog());
        }

        btnStartTime.setOnClickListener(v -> showTimePicker(true));
        btnEndTime.setOnClickListener(v -> showTimePicker(false));

        updateTimeButtons();

        btnSave.setOnClickListener(v -> {
            still.placeName = actvName.getText().toString().trim();
            still.placeCoords = etAddress.getText().toString().trim();
            still.startTimeDate = editedStartTime;
            still.endTimeDate = editedEndTime;
            still.icon = selectedIcon;
            still.color = selectedColor;

            if (listener != null) {
                listener.onUpdate(still);
            }
            dismiss();
        });
    }

    private void setupAddressAutocomplete() {
        AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line);
        etAddress.setAdapter(adapter);

        etAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() < 3) return;

                FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(token)
                        .setQuery(s.toString())
                        .build();

                placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener(response -> {
                            lastPredictions = response.getAutocompletePredictions();
                            List<String> suggestions = new ArrayList<>();
                            for (AutocompletePrediction prediction : lastPredictions) {
                                suggestions.add(prediction.getFullText(null).toString());
                            }
                            adapter.clear();
                            adapter.addAll(suggestions);
                            adapter.notifyDataSetChanged();
                        })
                        .addOnFailureListener(e -> Log.e("EditActivitySheet", "Autocomplete error", e));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etAddress.setOnItemClickListener((parent, view, position, id) -> {
            if (position < lastPredictions.size()) {
                AutocompletePrediction prediction = lastPredictions.get(position);
                String placeId = prediction.getPlaceId();
                fetchPlaceDetails(placeId);
            }
        });
    }

    private void fetchPlaceDetails(String placeId) {
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG, Place.Field.FORMATTED_ADDRESS);
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

        placesClient.fetchPlace(request).addOnSuccessListener(response -> {
            Place place = response.getPlace();
            if (place.getLatLng() != null) {
                still.lat = place.getLatLng().latitude;
                still.lng = place.getLatLng().longitude;
                // Update the EditText to show the formatted address if available
                if (place.getAddress() != null) {
                    etAddress.setText(place.getAddress());
                }
            }
        }).addOnFailureListener(e -> Log.e("EditActivitySheet", "Fetch Place Details error", e));
    }

    private void showIconPickerDialog() {
        Log.d("EditActivitySheet", "showIconPickerDialog called.");
        IconPickerDialog dialog = IconPickerDialog.newInstance(selectedIcon, selectedColor);
        dialog.setOnIconSelectedListener((iconName, color) -> {
            Log.d("EditActivitySheet", "Icon selected callback. Icon: " + iconName + ", Color: " + String.format("#%06X", (0xFFFFFF & color)));
            selectedIcon = iconName;
            selectedColor = color;
            updateIconAndColorUi();
        });
        dialog.show(getChildFragmentManager(), "icon_picker");
    }

    private void updateIconAndColorUi() {
        Log.d("EditActivitySheet", "updateIconAndColorUi called. Selected Icon: " + selectedIcon + ", Selected Color: " + String.format("#%06X", (0xFFFFFF & selectedColor)));

        // Update color preview
        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(selectedColor);
            viewSelectedColor.setBackground(gd);
            Log.d("EditActivitySheet", "Color preview updated.");
        }

        // Update icon preview
        if (ivSelectedIcon != null) {
            int iconRes = R.drawable.ic_still;
            String icon = selectedIcon.toLowerCase();

            if (icon.contains("home")) iconRes = R.drawable.ic_home;
            else if (icon.contains("work")) iconRes = R.drawable.ic_work;
            else if (icon.contains("gym")) iconRes = R.drawable.ic_gym;
            else if (icon.contains("school")) iconRes = R.drawable.ic_school;
            else if (icon.contains("restaurant") || icon.contains("eat")) iconRes = R.drawable.ic_restaurant;
            else if (icon.contains("coffee") || icon.contains("cafe")) iconRes = R.drawable.ic_coffee;
            else if (icon.contains("car") || icon.contains("drive")) iconRes = R.drawable.ic_car;
            else if (icon.contains("bike") || icon.contains("cycle")) iconRes = R.drawable.ic_bike;
            else if (icon.contains("walk")) iconRes = R.drawable.ic_walk;

            ivSelectedIcon.setImageResource(iconRes);

            // Remove the custom color filter so the XML app:tint can do its job
            ivSelectedIcon.clearColorFilter();

            Log.d("EditActivitySheet", "Icon preview updated with resource: " + iconRes);
        }
    }

    private void fetchNearbyPlaceSuggestions() {
        if (still.lat == null || still.lng == null) return;

        List<Place.Field> placeFields = Arrays.asList(Place.Field.DISPLAY_NAME, Place.Field.ID);
        CircularBounds circle = CircularBounds.newInstance(new LatLng(still.lat, still.lng), 100.0);
        SearchNearbyRequest request = SearchNearbyRequest.builder(circle, placeFields)
                .setMaxResultCount(10)
                .build();

        placesClient.searchNearby(request)
                .addOnSuccessListener(response -> {
                    List<String> names = new ArrayList<>();
                    for (Place p : response.getPlaces()) {
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
