package com.example.myapplication.mainScreen;

import static com.example.myapplication.helpers.UiFormatters.category;

import android.app.TimePickerDialog;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.helpers.ColorAndIcons;
import com.example.myapplication.helpers.UiFormatters;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class EditMovementActivitySheet extends BottomSheetDialogFragment {

    public interface OnVisitInteractionListener {
        void onUpdate(MovementActivity movement);
        void onDelete(MovementActivity movement);
    }

    private MovementActivity movement;
    private OnVisitInteractionListener listener;
    private PlacesClient placesClient;
    private PlaceDao placeDao;

    private Button btnStartTime, btnEndTime;
    private MaterialButton btnDelete;
    private View viewSelectedColor;
    private ImageView ivSelectedIcon;
    private Date editedStartTime, editedEndTime;
    private Integer selectedColor;
    private Integer selectedIcon;

    private String selectedActivityName;
    private LifeTrackerApp app;
    private AutoCompleteTextView actvActivityType; // Added

    public static EditMovementActivitySheet newInstance(MovementActivity movement, OnVisitInteractionListener listener) {
        EditMovementActivitySheet fragment = new EditMovementActivitySheet();
        fragment.movement = movement;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.edit_movement_activity_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("EditmovementActivitySheet", "onViewCreated called.");

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.GOOGLE_API_KEY);
        }
        placesClient = Places.createClient(requireContext());

        // Initialize PlaceDao
        placeDao = ActivityDatabase.getDatabase(requireContext()).placeDao();

        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);
        btnDelete = view.findViewById(R.id.btnDelete);

        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);
        actvActivityType = view.findViewById(R.id.actvActivityType); // Initialize AutoCompleteTextView

        View btnSave = view.findViewById(R.id.btnSave);

        app = (LifeTrackerApp) requireActivity().getApplication();

        // Initialize state
        editedStartTime = movement.getStartTimeDate();
        editedEndTime = movement.getEndTimeDate();

        selectedActivityName = movement.getActivityTypeName();
        // Setup activity type dropdown
        String[] activityTypes = {"Cycling", "Walking", "Running", "Driving"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, activityTypes);
        actvActivityType.setAdapter(adapter);

        if (selectedActivityName != null) {
            actvActivityType.setText(selectedActivityName, false);
        }

        actvActivityType.setOnItemClickListener((parent, view1, position, id) -> {
            movement.setActivityTypeName((String) parent.getItemAtPosition(position));
            selectedActivityName = (String) parent.getItemAtPosition(position);
            updateIconAndColorUi();
        });

        updateIconAndColorUi(); // Added this call here

        btnStartTime.setOnClickListener(v -> showTimePickerDialog(true));
        btnEndTime.setOnClickListener(v -> showTimePickerDialog(false));

        updateTimeButtons();

        btnSave.setOnClickListener(v -> {
//todo
            movement.setActivityTypeName(actvActivityType.getText().toString()); // Ensure activity type is saved
            if (listener != null) {
                listener.onUpdate(movement);
            }
            dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Activity?")
                    .setMessage("Are you sure you want to delete this activity? This action cannot be undone.")
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (listener != null) {
                            listener.onDelete(movement);
                        }
                        dismiss();
                    })
                    .show();
        });
    }

    // Add this method to handle keyboard behavior
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }


    private void updateIconAndColorUi() {

        int[] iconRes = ColorAndIcons.getMovementColorAndIcon(selectedActivityName.toLowerCase());


        selectedColor = ContextCompat.getColor(requireContext(), iconRes[0]);
        selectedIcon = iconRes[1];
        Log.d("EditmovementActivitySheet", "updateIconAndColorUi called. Selected Icon: " + selectedIcon + ", Selected Color: " + String.format("#%06X", (0xFFFFFF & selectedColor)));
        // Update color preview
        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);

            gd.setColor(selectedColor & 0x20FFFFFF);
            viewSelectedColor.setBackground(gd);
            Log.d("EditmovementActivitySheet", "Color preview updated.");
        }

        // Update icon preview
         if (ivSelectedIcon != null) {
             ivSelectedIcon.setImageResource(selectedIcon);
             ivSelectedIcon.setColorFilter(selectedColor); // Set color filter to apply the selected color
             Log.d("EditmovementActivitySheet", "Icon preview updated with resource: " + iconRes + " and color: " + String.format("#%06X", (0xFFFFFF & selectedColor)));
         }
    }


    private void updateTimeButtons() {
        btnStartTime.setText("Start: " + UiFormatters.timeOnly(editedStartTime));
        btnEndTime.setText("End: " + UiFormatters.timeOnly(editedEndTime));

        boolean isOngoing = (editedEndTime == null);
        btnEndTime.setEnabled(!isOngoing);
        btnEndTime.setAlpha(isOngoing ? 0.5f : 1.0f);
    }

    private void showTimePickerDialog(boolean isStart) {
        // time picker dialog
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
    }
}