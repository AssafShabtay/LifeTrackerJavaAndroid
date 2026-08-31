package com.example.myapplication.mainScreen.statisticsScreen;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.helpers.AddressAutocompleteHelper;
import com.example.myapplication.helpers.PlaceDropdownAdapter;
import com.example.myapplication.mainScreen.homeScreen.MapPickerActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkAddressPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnWorkAddressSelectedListener {
        void onWorkAddressSelected(String address);
    }

    private static final String ARG_PLACE_SUGGESTIONS = "place_suggestions";

    private AutoCompleteTextView etWorkAddress;
    private TextInputLayout tilWorkAddress;
    private Button btnSaveWorkAddress;
    private OnWorkAddressSelectedListener listener;
    private List<String> placeSuggestions;
    private final Handler validationHandler = new Handler(Looper.getMainLooper());
    private Runnable validationRunnable;

    private final ActivityResultLauncher<Intent> mapPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_ADDRESS);
                    if (address != null) {
                        etWorkAddress.setText(address);
                    }
                }
            }
    );

    // passing parms to the fragment
    public static WorkAddressPickerBottomSheet newInstance(OnWorkAddressSelectedListener listener, Map<String, Long> placeDurations) {
        WorkAddressPickerBottomSheet fragment = new WorkAddressPickerBottomSheet();
        fragment.listener = listener;

        // bundling the place suggestions
        Bundle args = new Bundle();
        if (placeDurations != null && !placeDurations.isEmpty()) {
            List<Map.Entry<String, Long>> sortedPlaces = new ArrayList<>(placeDurations.entrySet());
            sortedPlaces.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue())); // Sort by duration descending
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
        tilWorkAddress = view.findViewById(R.id.tilWorkAddress);
        btnSaveWorkAddress = view.findViewById(R.id.btnSaveWorkAddress);


        // Display common places as suggestions
        if (placeSuggestions != null && !placeSuggestions.isEmpty()) {
            PlaceDropdownAdapter adapter = new PlaceDropdownAdapter(requireContext(), placeSuggestions);
            etWorkAddress.setAdapter(adapter);

            // show the drop down after the ui is loaded
            etWorkAddress.post(() -> etWorkAddress.showDropDown());
        }

        AddressAutocompleteHelper addressAutocomplete = new AddressAutocompleteHelper(requireContext(), etWorkAddress);
        addressAutocomplete.setMapPickerLauncher(mapPickerLauncher);

        setupAddressValidation();

        // Save address handler
        btnSaveWorkAddress.setOnClickListener(v -> {
            String address = etWorkAddress.getText().toString().trim();
            if (address.isEmpty()) {
                tilWorkAddress.setError("Please enter a work address.");
            } else {
                validateAndSave(address);
            }
        });
    }

    private void setupAddressValidation() {
        validationRunnable = () -> {
            String address = etWorkAddress.getText().toString().trim();
            if (address.isEmpty()) {
                tilWorkAddress.setError(null);
                btnSaveWorkAddress.setEnabled(true);
                return;
            }
            performAddressValidation(address);
        };

        etWorkAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilWorkAddress.setError(null);
                validationHandler.removeCallbacks(validationRunnable);
                validationHandler.postDelayed(validationRunnable, 1000);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performAddressValidation(String address) {
        LifeTrackerApp app = (LifeTrackerApp) requireActivity().getApplication();
        app.getDatabaseWriteExecutor().execute(() -> {
            if (getContext() == null) return;
            Geocoder geocoder = new Geocoder(requireContext());
            try {
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                boolean isValid = addresses != null && !addresses.isEmpty();

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (isValid) {
                            tilWorkAddress.setError(null);
                            btnSaveWorkAddress.setEnabled(true);
                        } else {
                            tilWorkAddress.setError("Invalid address. Please check and try again.");
                            btnSaveWorkAddress.setEnabled(false);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        //TODO HANDLE ERRORS
                        //In case of error (no internet ect), don't block the user but  don't clear errors if any
                        btnSaveWorkAddress.setEnabled(true);
                    });
                }
            }
        });
    }

    private void validateAndSave(String address) {
        btnSaveWorkAddress.setEnabled(false);
        LifeTrackerApp app = (LifeTrackerApp) requireActivity().getApplication();
        app.getDatabaseWriteExecutor().execute(() -> {
            if (getContext() == null) return;
            Geocoder geocoder = new Geocoder(requireContext());
            try {
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                boolean isValid = addresses != null && !addresses.isEmpty();

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (isValid) {
                            if (listener != null) {
                                listener.onWorkAddressSelected(address);
                                dismiss();
                            }
                        } else {
                            tilWorkAddress.setError("Invalid address. Please check and try again.");
                            btnSaveWorkAddress.setEnabled(true);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        // If validation fails due to network, allow saving anyway to be safe
                        if (listener != null) {
                            listener.onWorkAddressSelected(address);
                            dismiss();
                        }
                    });
                }
            }
        });
    }
}
