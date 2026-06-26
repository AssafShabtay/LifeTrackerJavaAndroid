package com.example.myapplication.mainScreen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.myapplication.helpers.PlaceAutocompleteHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

public class HomeAddressPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnHomeAddressSelectedListener {
        void onAddressSelected(String address);
    }

    private static final String ARG_PLACE_SUGGESTIONS = "place_suggestions";

    private AutoCompleteTextView etHomeAddress;
    private Button btnSaveHomeAddress;
    private OnHomeAddressSelectedListener listener;
    private List<String> placeSuggestions;

    // passing parms to the fragment
    public static HomeAddressPickerBottomSheet newInstance(OnHomeAddressSelectedListener listener, Map<String, Long> placeDurations) {
        HomeAddressPickerBottomSheet fragment = new HomeAddressPickerBottomSheet();
        fragment.listener = listener;

        // bundling the place suggestions
        Bundle args = new Bundle();
        if (placeDurations != null && !placeDurations.isEmpty()) {
            List<Map.Entry<String, Long>> sortedPlaces = new ArrayList<>(placeDurations.entrySet());
            Collections.sort(sortedPlaces, (e1, e2) -> e2.getValue().compareTo(e1.getValue())); // Sort by duration descending
            ArrayList<String> suggestions = new ArrayList<>();
            for (int i = 0; i < Math.min(sortedPlaces.size(), 5); i++) { // Take top 5
                suggestions.add(sortedPlaces.get(i).getKey());
            }
            args.putStringArrayList(ARG_PLACE_SUGGESTIONS, suggestions);
        }
        fragment.setArguments(args);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            placeSuggestions = getArguments().getStringArrayList(ARG_PLACE_SUGGESTIONS);
        }
        return inflater.inflate(R.layout.bottom_sheet_home_address_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etHomeAddress = view.findViewById(R.id.etHomeAddressInput);
        btnSaveHomeAddress = view.findViewById(R.id.btnSaveHomeAddress);


        // Display common places as suggestions
        if (placeSuggestions != null && !placeSuggestions.isEmpty()) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, placeSuggestions);
            etHomeAddress.setAdapter(adapter);

            // TODO FIX COMMENT Post the showDropDown call to the view's message queue so it waits for the layout to finish
            etHomeAddress.post(() -> etHomeAddress.showDropDown());
        }

        // Initialize PlaceAutocompleteHelper after setting initial suggestions,
        // takes over when the user types
        new PlaceAutocompleteHelper(requireContext(), etHomeAddress);

        // Save address handler
        btnSaveHomeAddress.setOnClickListener(v -> {
            String address = etHomeAddress.getText().toString().trim();
            if (address.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a home address.", Toast.LENGTH_SHORT).show();
            } else if (listener != null) {
                listener.onAddressSelected(address);
                dismiss();
            }
        });
    }
}
