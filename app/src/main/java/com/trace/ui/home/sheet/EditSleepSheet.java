package com.trace.ui.home.sheet;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.trace.R;
import com.trace.data.model.SleepSession;
import com.trace.util.UiFormatters;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.Date;

public class EditSleepSheet extends BottomSheetDialogFragment {

    public interface OnSleepInteractionListener {
        void onUpdate(SleepSession sleep);
        void onDelete(SleepSession sleep);
    }

    private SleepSession sleep;
    private OnSleepInteractionListener listener;
    private Date editedStartTime;
    private Date editedEndTime;

    private Button btnStartTime;
    private Button btnEndTime;
    private Button btnSave;
    private MaterialButton btnDelete;

    public static EditSleepSheet newInstance(SleepSession sleep, OnSleepInteractionListener listener) {
        EditSleepSheet fragment = new EditSleepSheet();
        fragment.sleep = sleep;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.edit_sleep_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnStartTime = view.findViewById(R.id.btnStartTime);
        btnEndTime = view.findViewById(R.id.btnEndTime);
        btnSave = view.findViewById(R.id.btnSave);
        btnDelete = view.findViewById(R.id.btnDelete);

        editedStartTime = sleep.getStartTime();
        editedEndTime = sleep.getEndTime();

        updateTimeButtons();

        btnStartTime.setOnClickListener(v -> showTimePickerDialog(true));
        btnEndTime.setOnClickListener(v -> showTimePickerDialog(false));

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete Sleep Session?")
                        .setMessage("Are you sure you want to delete this sleep session?")
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("Delete", (dialog, which) -> {
                            if (listener != null) {
                                listener.onDelete(sleep);
                            }
                            dismiss();
                        })
                        .show();
            });
        }

        btnSave.setOnClickListener(v -> {
            sleep.setStartTime(editedStartTime);
            sleep.setEndTime(editedEndTime);
            if (listener != null) {
                listener.onUpdate(sleep);
            }
            dismiss();
        });
    }

    private void updateTimeButtons() {
        if (editedStartTime != null) {
            btnStartTime.setText(UiFormatters.timeOnly(editedStartTime));
        } else {
            btnStartTime.setText("Not set");
        }

        if (editedEndTime != null) {
            btnEndTime.setText(UiFormatters.timeOnly(editedEndTime));
        } else {
            btnEndTime.setText("Not set");
        }
    }

    private void showTimePickerDialog(boolean isStart) {
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

            if (isStart) {
                editedStartTime = newCal.getTime();
            } else {
                editedEndTime = newCal.getTime();
            }

            updateTimeButtons();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
        picker.show();
    }
}
