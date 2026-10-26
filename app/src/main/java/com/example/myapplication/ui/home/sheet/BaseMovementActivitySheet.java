package com.example.myapplication.ui.home.sheet;

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

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.ui.general.ColorAndIcons;
import com.example.myapplication.util.UiFormatters;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public abstract class BaseMovementActivitySheet extends BottomSheetDialogFragment {

    protected Button btnStartTime, btnEndTime;
    protected View viewSelectedColor;
    protected ImageView ivSelectedIcon;
    protected Date editedStartTime, editedEndTime;
    protected Integer selectedColor;
    protected Integer selectedIcon;

    protected String selectedActivityName;
    protected LifeTrackerApp app;
    protected AutoCompleteTextView actvActivityType;
    protected View btnSave;

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

        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);
        viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        ivSelectedIcon = view.findViewById(R.id.ivSelectedIcon);
        actvActivityType = view.findViewById(R.id.actvActivityType);

        btnSave = view.findViewById(R.id.btnSave);
        app = (LifeTrackerApp) requireActivity().getApplication();

        onInitializeData();

        // Setup activity type dropdown
        List<String> activityTypes = Arrays.asList("Cycling", "Walking", "Running", "Driving");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_dropdown_compact, activityTypes);
        actvActivityType.setAdapter(adapter);

        if (selectedActivityName != null) {
            actvActivityType.setText(selectedActivityName, false);
        }

        actvActivityType.setOnItemClickListener((parent, view1, position, id) -> {
            selectedActivityName = (String) parent.getItemAtPosition(position);
            updateIconAndColorUi();
        });

        updateIconAndColorUi();

        btnStartTime.setOnClickListener(v -> showTimePickerDialog(true));
        btnEndTime.setOnClickListener(v -> showTimePickerDialog(false));

        updateTimeButtons();

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> onSaveClicked());
        }
    }

    // Adjust window soft input mode so software keyboard resizes the bottom sheet properly
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    protected void updateIconAndColorUi() {
        if (selectedActivityName == null) return;
        int[] iconRes = ColorAndIcons.getMovementColorAndIcon(selectedActivityName.toLowerCase());

        selectedColor = ContextCompat.getColor(requireContext(), iconRes[0]);
        selectedIcon = iconRes[1];

        // Update color preview
        if (viewSelectedColor != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(selectedColor & 0x20FFFFFF);
            viewSelectedColor.setBackground(gd);
        }

        // Update icon preview
        if (ivSelectedIcon != null) {
            ivSelectedIcon.setImageResource(selectedIcon);
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
}
