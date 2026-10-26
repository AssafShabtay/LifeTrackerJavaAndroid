package com.example.myapplication.ui.home.sheet;

import com.example.myapplication.ui.home.map.MapPickerActivity;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.db.PlaceDao;
import com.example.myapplication.data.model.Place;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.ui.general.ColorAndIcons;
import com.example.myapplication.ui.general.ContainsArrayAdapter;
import com.example.myapplication.ui.general.PlaceDropdownAdapter;
import com.example.myapplication.util.AddressAutocompleteHelper;
import com.example.myapplication.util.ErrorLogger;
import com.example.myapplication.util.UiFormatters;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseStillActivitySheet extends BottomSheetDialogFragment {

    protected static final String TAG = "BaseStillActivitySheet";

    protected PlacesClient placesClient;
    protected PlaceDao placeDao;
    protected AddressAutocompleteHelper addressAutocompleteHelper;

    protected AutoCompleteTextView actvName;
    protected AutoCompleteTextView etAddress;
    protected AutoCompleteTextView actvCategory;
    protected TextView tvAddressStatus;
    protected Button btnStartTime, btnEndTime;
    protected View btnSave;

    protected View layoutIconPicker;
    protected View viewSelectedColor;
    protected ImageView ivSelectedIcon;

    protected Date editedStartTime, editedEndTime;
    protected Integer selectedColor;
    protected String selectedIcon;
    protected Place selectedPlace;
    protected String selectedCategory;
    protected String selectedGeofenceId;
    protected LifeTrackerApp app;

    // Receives address result when user picks a point in the map picker
    protected final ActivityResultLauncher<Intent> mapPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_ADDRESS);
                    if (address != null) {
                        etAddress.setText(address);
                        if (tvAddressStatus != null) {
                            tvAddressStatus.setText("Location picked from map");
                            tvAddressStatus.setVisibility(View.VISIBLE);
                            tvAddressStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark));
                        }
                    }
                }
            }
    );

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract void onInitializeData();

    protected abstract void onSaveClicked();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(getLayoutResId(), container, false);
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
        actvCategory = view.findViewById(R.id.actvCategory);
        tvAddressStatus = view.findViewById(R.id.tvAddressStatus);
        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);

        layoutIconPicker = view.findViewById(R.id.layoutIconPicker);
        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);

        btnSave = view.findViewById(R.id.btnSave);

        app = (LifeTrackerApp) requireActivity().getApplication();

        // listener for icon picker dialog
        getChildFragmentManager().setFragmentResultListener("icon_picker_request", this, (requestKey, bundle) -> {
            selectedIcon = bundle.getString("selectedIcon");
            selectedColor = bundle.getInt("selectedColor");
            updateIconAndColorUi();
        });

        onInitializeData();

        setupCategoryDropdown();
        updateIconAndColorUi();

        addressAutocompleteHelper = new AddressAutocompleteHelper(requireContext(), etAddress);
        addressAutocompleteHelper.setMapPickerLauncher(mapPickerLauncher);

        // Set up icon picker
        if (layoutIconPicker != null) {
            layoutIconPicker.setOnClickListener(v -> showIconPickerDialog());
        }

        btnStartTime.setOnClickListener(v -> showTimePickerDialog(true));
        btnEndTime.setOnClickListener(v -> showTimePickerDialog(false));

        updateTimeButtons();

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> onSaveClicked());
        }

        setupAddressValidation();
    }

    protected void setupAddressValidation() {
        // address validation logic
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }
        Handler validationHandler = new Handler(Looper.getMainLooper());
        Runnable validationRunnable = new Runnable() {
            @Override
            public void run() {
                String address = etAddress.getText().toString().trim();
                if (address.isEmpty()) {
                    if (tvAddressStatus != null) {
                        tvAddressStatus.setVisibility(View.INVISIBLE);
                        tvAddressStatus.setText(null);
                    }
                    if (btnSave != null) {
                        btnSave.setEnabled(true);
                    }
                    return;
                }

                app.getBackgroundWriteExecutor().execute(() -> {
                    if (getContext() == null) return;
                    Geocoder geocoder = new Geocoder(requireContext());
                    try {
                        List<Address> addresses = geocoder.getFromLocationName(address, 1);
                        boolean isValid = addresses != null && !addresses.isEmpty();

                        List<Place> nearbyPlaces = new ArrayList<>();
                        double lat = 0;
                        double lng = 0;
                        if (isValid) {
                            Address add = addresses.get(0);
                            lat = add.getLatitude();
                            lng = add.getLongitude();

                            // search for user's places near the selected location
                            List<Place> allPlaces = placeDao.getAllPlaces();
                            for (Place p : allPlaces) {
                                float[] results = new float[1];
                                Location.distanceBetween(lat, lng, p.getLat(), p.getLng(), results);
                                if (results[0] <= 75) {
                                    nearbyPlaces.add(p);
                                }
                            }
                        }

                        final double finalLat = lat;
                        final double finalLng = lng;
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                if (isValid) {
                                    if (tvAddressStatus != null) {
                                        tvAddressStatus.setVisibility(View.VISIBLE);
                                        tvAddressStatus.setText("Valid Address");
                                        tvAddressStatus.setTextColor(Color.parseColor("#4CAF50"));
                                    }
                                    if (btnSave != null) {
                                        btnSave.setEnabled(true);
                                    }

                                    setupPlacesDropdown(finalLat, finalLng, nearbyPlaces);

                                    // Ask user if they want to link an existing nearby place
                                    if (!nearbyPlaces.isEmpty()) {
                                        Place firstPlace = nearbyPlaces.get(0);
                                        new MaterialAlertDialogBuilder(requireContext())
                                            .setTitle(HtmlCompat.fromHtml("Is the place: <b>" + firstPlace.getName() + "</b>?", HtmlCompat.FROM_HTML_MODE_LEGACY))
                                            .setMessage(HtmlCompat.fromHtml("Is the place you're looking for <b>" + firstPlace.getName() + "</b>?", HtmlCompat.FROM_HTML_MODE_LEGACY))
                                            .setPositiveButton("Yes", (dialog, which) -> {
                                                actvName.setText(firstPlace.getName(), false);
                                                if (firstPlace.getIcon() != null) {
                                                    selectedIcon = firstPlace.getIcon();
                                                }
                                                if (firstPlace.getColor() != null) {
                                                    selectedColor = firstPlace.getColor();
                                                }
                                                selectedGeofenceId = firstPlace.getGeofencePlaceId();
                                                selectedPlace = firstPlace;
                                                updateIconAndColorUi();
                                            })
                                            .setNegativeButton("No", (dialog, which) -> {
                                                // Show selection list of all nearby local places
                                                String[] placeNames = new String[nearbyPlaces.size()];
                                                for (int i = 0; i < nearbyPlaces.size(); i++) {
                                                    placeNames[i] = nearbyPlaces.get(i).getName();
                                                }

                                                new MaterialAlertDialogBuilder(requireContext())
                                                        .setTitle("Select a place")
                                                        .setItems(placeNames, (dialog1, which1) -> {
                                                            Place selected = nearbyPlaces.get(which1);
                                                            actvName.setText(selected.getName(), false);
                                                            if (selected.getIcon() != null) {
                                                                selectedIcon = selected.getIcon();
                                                            }
                                                            if (selected.getColor() != null) {
                                                                selectedColor = selected.getColor();
                                                            }
                                                            selectedGeofenceId = selected.getGeofencePlaceId();
                                                            selectedPlace = selected;
                                                            updateIconAndColorUi();
                                                        })
                                                        .setNegativeButton("Not here", (dialog1, which1) -> {
                                                            setupPlacesDropdown(finalLat, finalLng, nearbyPlaces);
                                                        })
                                                        .setOnDismissListener(dialog1 -> {
                                                            if (selectedPlace == null) {
                                                                setupPlacesDropdown(finalLat, finalLng, nearbyPlaces);
                                                            }
                                                        })
                                                        .show();
                                            })
                                            .show();
                                    }
                                } else {
                                    if (tvAddressStatus != null) {
                                        tvAddressStatus.setVisibility(View.VISIBLE);
                                        tvAddressStatus.setText("Invalid Address");
                                        tvAddressStatus.setTextColor(Color.RED);
                                    }
                                    if (btnSave != null) {
                                        btnSave.setEnabled(false);
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        Context context = getContext();
                        if (context != null) {
                            ErrorLogger.logError(context, TAG, "Error validating address", e);
                        }
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                // Allow saving typed address when offline or geocoder unavailable
                                if (tvAddressStatus != null) {
                                    tvAddressStatus.setVisibility(View.VISIBLE);
                                    tvAddressStatus.setText("Offline - Address saved as typed");
                                    tvAddressStatus.setTextColor(Color.GRAY);
                                }
                                if (btnSave != null) {
                                    btnSave.setEnabled(true);
                                }
                            });
                        }
                    }
                });
            }
        };

        etAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                etAddress.setError(null);
                selectedPlace = null;

                // don't execute address validation while user is actively typing
                validationHandler.removeCallbacks(validationRunnable);
                if (tvAddressStatus != null) {
                    tvAddressStatus.setVisibility(View.VISIBLE);
                    tvAddressStatus.setText("Checking...");
                    tvAddressStatus.setTextColor(Color.GRAY);
                }
                if (btnSave != null) {
                    btnSave.setEnabled(false);
                }
                validationHandler.postDelayed(validationRunnable, 1000);
            }
        });
    }

    protected void setupCategoryDropdown() {
        // category dropdown choices from categories.json
        Map<String, String> categoryMap = loadCategories();
        List<String> displayNames = new ArrayList<>(categoryMap.keySet());
        Collections.sort(displayNames);

        ContainsArrayAdapter adapter = new ContainsArrayAdapter(requireContext(),
                R.layout.item_dropdown_compact, displayNames);
        actvCategory.setAdapter(adapter);

        if (selectedCategory != null) {
            actvCategory.setText(UiFormatters.category(selectedCategory), false);
        }

        actvCategory.setOnClickListener(v -> actvCategory.showDropDown());

        actvCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDisplay = (String) parent.getItemAtPosition(position);
            selectedCategory = categoryMap.get(selectedDisplay);
        });

        actvCategory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    selectedCategory = null;
                } else {
                    selectedCategory = categoryMap.getOrDefault(text, text);
                }
            }
        });
    }

    protected Map<String, String> loadCategories() {
        // load categories from JSON file
        Map<String, String> map = new HashMap<>();
        try (InputStream is = getResources().openRawResource(R.raw.categories);
             InputStreamReader reader = new InputStreamReader(is)) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> categories = new Gson().fromJson(reader, type);
            if (categories != null) {
                for (List<String> list : categories.values()) {
                    for (String raw : list) {
                        map.put(UiFormatters.category(raw), raw);
                    }
                }
            }
        } catch (Exception e) {
            ErrorLogger.logError(requireContext(), TAG, "Failed to load categories", e);
        }
        return map;
    }

    // Adjust window soft input mode so software keyboard resizes the bottom sheet properly
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    protected void setupPlacesDropdown(double lat, double lng, @Nullable List<Place> nearbyPlaces) {
        if (getActivity() == null || placesClient == null) return;

        List<com.google.android.libraries.places.api.model.Place.Field> placeFields = Arrays.asList(
                com.google.android.libraries.places.api.model.Place.Field.DISPLAY_NAME,
                com.google.android.libraries.places.api.model.Place.Field.TYPES,
                com.google.android.libraries.places.api.model.Place.Field.ID,
                com.google.android.libraries.places.api.model.Place.Field.LOCATION
        );

        LatLng latLng = new LatLng(lat, lng);
        CircularBounds circle = CircularBounds.newInstance(latLng, 100.0);
        SearchNearbyRequest request = SearchNearbyRequest.builder(circle, placeFields)
                .setMaxResultCount(10)
                .build();

        placesClient.searchNearby(request)
                .addOnSuccessListener(response -> {
                    if (isAdded() && response.getPlaces() != null) {
                        List<com.google.android.libraries.places.api.model.Place> googlePlaces = response.getPlaces();
                        List<String> names = new ArrayList<>();

                        if (nearbyPlaces != null) {
                            for (Place p : nearbyPlaces) {
                                if (p.getName() != null && !names.contains(p.getName())) {
                                    names.add(p.getName());
                                }
                            }
                        }

                        for (com.google.android.libraries.places.api.model.Place p : googlePlaces) {
                            if (p.getDisplayName() != null && !names.contains(p.getDisplayName())) {
                                names.add(p.getDisplayName());
                            }
                        }

                        if (names.isEmpty()) return;

                        PlaceDropdownAdapter adapter = new PlaceDropdownAdapter(requireContext(), names);
                        actvName.setAdapter(adapter);
                        actvName.setOnItemClickListener((parent, view, position, id) -> {
                            String selectedName = adapter.getItem(position);

                            if (nearbyPlaces != null) {
                                for (Place p : nearbyPlaces) {
                                    if (p.getName() != null && p.getName().equals(selectedName)) {
                                        actvName.setText(p.getName(), false);
                                        selectedPlace = p;
                                        if (p.getIcon() != null) selectedIcon = p.getIcon();
                                        if (p.getColor() != null) selectedColor = p.getColor();
                                        selectedGeofenceId = p.getGeofencePlaceId();
                                        updateIconAndColorUi();
                                        return;
                                    }
                                }
                            }

                            for (com.google.android.libraries.places.api.model.Place p : googlePlaces) {
                                if (p.getDisplayName() != null && p.getDisplayName().equals(selectedName)) {
                                    actvName.setText(p.getDisplayName(), false);
                                    Place newLocalPlace = new Place();
                                    newLocalPlace.setName(p.getDisplayName());
                                    if (p.getLocation() != null) {
                                        newLocalPlace.setLat(p.getLocation().latitude);
                                        newLocalPlace.setLng(p.getLocation().longitude);
                                    } else {
                                        newLocalPlace.setLat(lat);
                                        newLocalPlace.setLng(lng);
                                    }
                                    newLocalPlace.setGeofencePlaceId(p.getId());
                                    selectedGeofenceId = p.getId();
                                    selectedPlace = newLocalPlace;
                                    updateIconAndColorUi();
                                    break;
                                }
                            }
                        });
                        actvName.showDropDown();
                    }
                })
                .addOnFailureListener(e -> {
                    ErrorLogger.logError(requireContext(), TAG, "Google Places search failed", e);
                    if (isAdded() && nearbyPlaces != null && !nearbyPlaces.isEmpty()) {
                        List<String> names = new ArrayList<>();
                        for (Place p : nearbyPlaces) {
                            if (p.getName() != null && !names.contains(p.getName())) {
                                names.add(p.getName());
                            }
                        }
                        PlaceDropdownAdapter adapter = new PlaceDropdownAdapter(requireContext(), names);
                        actvName.setAdapter(adapter);
                        actvName.setOnItemClickListener((parent, view, position, id) -> {
                            String selectedName = adapter.getItem(position);
                            for (Place p : nearbyPlaces) {
                                if (p.getName() != null && p.getName().equals(selectedName)) {
                                    actvName.setText(p.getName(), false);
                                    selectedPlace = p;
                                    if (p.getIcon() != null) selectedIcon = p.getIcon();
                                    if (p.getColor() != null) selectedColor = p.getColor();
                                    selectedGeofenceId = p.getGeofencePlaceId();
                                    updateIconAndColorUi();
                                    break;
                                }
                            }
                        });
                    }
                });
    }

    protected void showIconPickerDialog() {
        IconPickerDialog dialog = IconPickerDialog.newInstance(selectedIcon, selectedColor);
        dialog.show(getChildFragmentManager(), "icon_picker");
    }

    protected void updateIconAndColorUi() {
        // Update color preview
        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(selectedColor & 0x20FFFFFF);
            viewSelectedColor.setBackground(gd);
        }

        // Update icon preview
        if (ivSelectedIcon != null) {
            // Create a dummy StillLocation to pass to getStillIconRes
            StillLocation tempStill = new StillLocation();
            tempStill.setIcon(selectedIcon);
            int iconRes = ColorAndIcons.getStillIconRes(tempStill);

            ivSelectedIcon.setImageResource(iconRes);
            ivSelectedIcon.setColorFilter(selectedColor);
        }
    }

    protected void updateTimeButtons() {
        btnStartTime.setText("Start: " + UiFormatters.timeOnly(editedStartTime));
        btnEndTime.setText("End: " + UiFormatters.timeOnly(editedEndTime));

        boolean isOngoing = (editedEndTime == null);
        btnEndTime.setEnabled(!isOngoing);
        btnEndTime.setAlpha(isOngoing ? 0.5f : 1.0f);
    }

    protected void showTimePickerDialog(boolean isStart) {
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
