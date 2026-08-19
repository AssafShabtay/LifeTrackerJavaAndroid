package com.example.myapplication.mainScreen;

import android.app.TimePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.helpers.ColorAndIcons;
import com.example.myapplication.helpers.UiFormatters;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;
import java.util.Date;

public class AddCustomMovementActivitySheet extends BottomSheetDialogFragment {

    public interface OnActivityAddedListener {
        void onActivityAdded();
    }

    private OnActivityAddedListener listener;

    private Button btnStartTime, btnEndTime;
    private View viewSelectedColor;
    private ImageView ivSelectedIcon;
    private Date editedStartTime, editedEndTime;
    private Integer selectedColor;
    private Integer selectedIcon;

    private String selectedActivityName;
    private LifeTrackerApp app;
    private AutoCompleteTextView actvActivityType;
    private Date selectedDate;

    public static AddCustomMovementActivitySheet newInstance(Date date, OnActivityAddedListener listener) {
        AddCustomMovementActivitySheet fragment = new AddCustomMovementActivitySheet();
        fragment.selectedDate = date;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.add_custom_movement_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);
        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);
        actvActivityType = view.findViewById(R.id.actvActivityType);

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

        selectedActivityName = "Walking"; // Default activity type
        
        // Setup activity type dropdown
        String[] activityTypes = {"Cycling", "Walking", "Running", "Driving"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, activityTypes);
        actvActivityType.setAdapter(adapter);
        actvActivityType.setText(selectedActivityName, false);

        actvActivityType.setOnItemClickListener((parent, view1, position, id) -> {
            selectedActivityName = (String) parent.getItemAtPosition(position);
            updateIconAndColorUi();
        });

        updateIconAndColorUi();

        btnStartTime.setOnClickListener(v -> showTimePickerDialog(true));
        btnEndTime.setOnClickListener(v -> showTimePickerDialog(false));

        updateTimeButtons();

        btnSave.setOnClickListener(v -> {
            String finalActivityName = actvActivityType.getText().toString();
            if (finalActivityName.isEmpty()) {
                finalActivityName = "Walking";
            }

            final String activityNameToSave = finalActivityName;
            
            app.getDatabaseWriteExecutor().execute(() -> {
                MovementActivity newMovement = new MovementActivity();
                newMovement.setActivityTypeName(activityNameToSave);
                newMovement.setStartTimeDate(editedStartTime);
                newMovement.setEndTimeDate(editedEndTime);

                ActivityDatabase.getDatabase(requireContext()).activityDao().insertMovementActivity(newMovement);

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

    private void updateIconAndColorUi() {
        int[] iconRes = ColorAndIcons.getMovementColorAndIcon(selectedActivityName.toLowerCase());

        selectedColor = ContextCompat.getColor(requireContext(), iconRes[0]);
        selectedIcon = iconRes[1];

        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(selectedColor & 0x20FFFFFF);
            viewSelectedColor.setBackground(gd);
        }

        if (ivSelectedIcon != null) {
            ivSelectedIcon.setImageResource(selectedIcon);
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
}