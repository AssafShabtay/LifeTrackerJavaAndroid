package com.example.myapplication.mainScreen.statisticsScreen;

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
import com.example.myapplication.helpers.AddressAutocompleteHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WorkAddressPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnWorkAddressSelectedListener {
        void onWorkAddressSelected(String address);
    }

    private static final String ARG_PLACE_SUGGESTIONS = "place_suggestions";

    private AutoCompleteTextView etWorkAddress;
    private Button btnSaveWorkAddress;
    private OnWorkAddressSelectedListener listener;
    private List<String> placeSuggestions;

    // passing parms to the fragment
    public static WorkAddressPickerBottomSheet newInstance(OnWorkAddressSelectedListener listener, Map<String, Long> placeDurations) {
        WorkAddressPickerBottomSheet fragment = new WorkAddressPickerBottomSheet();
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
        return inflater.inflate(R.layout.bottom_sheet_work_address_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etWorkAddress = view.findViewById(R.id.etWorkAddressInput);
        btnSaveWorkAddress = view.findViewById(R.id.btnSaveWorkAddress);


        // Display common places as suggestions
        if (placeSuggestions != null && !placeSuggestions.isEmpty()) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, placeSuggestions);
            etWorkAddress.setAdapter(adapter);

            // TODO FIX COMMENT Post the showDropDown call to the view's message queue so it waits for the layout to finish
            etWorkAddress.post(() -> etWorkAddress.showDropDown());
        }

        // Initialize AddressAutocompleteHelper after setting initial suggestions,
        // takes over when the user types
        new AddressAutocompleteHelper(requireContext(), etWorkAddress);

        // Save address handler
        btnSaveWorkAddress.setOnClickListener(v -> {
            String address = etWorkAddress.getText().toString().trim();
            if (address.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a work address.", Toast.LENGTH_SHORT).show();
            } else if (listener != null) {
                listener.onWorkAddressSelected(address);
                dismiss();
            }
        });
    }
}
