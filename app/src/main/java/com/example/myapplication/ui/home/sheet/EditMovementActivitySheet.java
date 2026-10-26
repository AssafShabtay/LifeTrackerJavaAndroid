package com.example.myapplication.ui.home.sheet;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.myapplication.data.model.MovementActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class EditMovementActivitySheet extends BaseMovementActivitySheet {

    // listener for communicating update delete operations
    public interface OnVisitInteractionListener {
        void onUpdate(MovementActivity movement);
        void onDelete(MovementActivity movement);
    }

    private MovementActivity movement;
    private OnVisitInteractionListener listener;
    private MaterialButton btnDelete;

    public static EditMovementActivitySheet newInstance(MovementActivity movement, OnVisitInteractionListener listener) {
        EditMovementActivitySheet fragment = new EditMovementActivitySheet();
        fragment.movement = movement;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.edit_movement_activity_sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        btnDelete = view.findViewById(R.id.btnDelete);
        super.onViewCreated(view, savedInstanceState);

        // Delete activity with confirmation dialog
        if (btnDelete != null) {
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
    }

    @Override
    protected void onInitializeData() {
        // Load values from the MovementActivity object
        editedStartTime = movement.getStartTimeDate();
        editedEndTime = movement.getEndTimeDate();

        selectedActivityName = movement.getActivityTypeName();
    }

    @Override
    protected void onSaveClicked() {
        // Save updated visit details
        movement.setActivityTypeName(actvActivityType.getText().toString());
        movement.setStartTimeDate(editedStartTime);
        movement.setEndTimeDate(editedEndTime);
        if (listener != null) {
            listener.onUpdate(movement);
        }
        dismiss();
    }
}
