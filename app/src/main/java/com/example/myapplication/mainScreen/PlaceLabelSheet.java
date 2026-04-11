package com.example.myapplication.mainScreen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class PlaceLabelSheet extends BottomSheetDialogFragment {

    public interface OnLabelSavedListener {
        void onLabelSaved(StillLocation still, String name, String category);
    }

    private StillLocation still;
    private OnLabelSavedListener listener;

    public static PlaceLabelSheet newInstance(StillLocation still, OnLabelSavedListener listener) {
        PlaceLabelSheet fragment = new PlaceLabelSheet();
        fragment.still = still;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_place_label_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ChipGroup chipGroup = view.findViewById(R.id.chipGroup);
        EditText etCustom = view.findViewById(R.id.etCustom);
        View btnSave = view.findViewById(R.id.btnSave);

        if (still.placeName != null && !still.placeName.equals("Stationary")) {
            etCustom.setText(still.placeName);
        }

        btnSave.setOnClickListener(v -> {
            String name = etCustom.getText().toString().trim();
            String category = "Other";

            int checkedId = chipGroup.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip chip = view.findViewById(checkedId);
                String chipText = chip.getText().toString();
                category = chipText;
                if (name.isEmpty()) {
                    name = chipText;
                }
            }

            if (!name.isEmpty() && listener != null) {
                listener.onLabelSaved(still, name, category);
                dismiss();
            } else if (name.isEmpty()) {
                etCustom.setError("Please enter a name or select a category");
            }
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = view.findViewById(checkedIds.get(0));
                if (etCustom.getText().toString().trim().isEmpty()) {
                    etCustom.setText(chip.getText());
                }
            }
        });
    }
}
