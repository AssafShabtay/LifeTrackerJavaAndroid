package com.example.myapplication.ui.home.sheet;

import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.MovementActivity;

import java.util.Calendar;
import java.util.Date;

public class AddCustomMovementActivitySheet extends BaseMovementActivitySheet {

    // listener for communicating activity added operations
    public interface OnActivityAddedListener {
        void onActivityAdded();
    }

    private OnActivityAddedListener listener;
    private Date selectedDate;

    public static AddCustomMovementActivitySheet newInstance(Date date, OnActivityAddedListener listener) {
        AddCustomMovementActivitySheet fragment = new AddCustomMovementActivitySheet();
        fragment.selectedDate = date;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.add_custom_movement_sheet;
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

        selectedActivityName = "Walking"; // Default activity type
    }

    @Override
    protected void onSaveClicked() {
        // Save new activity details
        String finalActivityName = actvActivityType.getText().toString();
        if (finalActivityName.isEmpty()) {
            finalActivityName = "Walking";
        }

        final String activityNameToSave = finalActivityName;

        app.getBackgroundWriteExecutor().execute(() -> {
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
    }
}
