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

public class HomeAddressPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnHomeAddressSelectedListener {
        void onHomeAddressSelected(String address);
    }

    private static final String ARG_PLACE_SUGGESTIONS = "place_suggestions";

    private AutoCompleteTextView etHomeAddress;
    private TextInputLayout tilHomeAddress;
    private Button btnSaveHomeAddress;
    private OnHomeAddressSelectedListener listener;
    private List<String> placeSuggestions;
    private final Handler validationHandler = new Handler(Looper.getMainLooper());
    private Runnable validationRunnable;

    private final ActivityResultLauncher<Intent> mapPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_ADDRESS);
                    if (address != null) {
                        etHomeAddress.setText(address);
                    }
                }
            }
    );

    // passing parms to the fragment
    public static HomeAddressPickerBottomSheet newInstance(OnHomeAddressSelectedListener listener, Map<String, Long> placeDurations) {
        HomeAddressPickerBottomSheet fragment = new HomeAddressPickerBottomSheet();
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
        return inflater.inflate(R.layout.bottom_sheet_home_address_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etHomeAddress = view.findViewById(R.id.etHomeAddressInput);
        tilHomeAddress = view.findViewById(R.id.tilHomeAddress);
        btnSaveHomeAddress = view.findViewById(R.id.btnSaveHomeAddress);


        // Display common places as suggestions
        if (placeSuggestions != null && !placeSuggestions.isEmpty()) {
            PlaceDropdownAdapter adapter = new PlaceDropdownAdapter(requireContext(), placeSuggestions);
            etHomeAddress.setAdapter(adapter);

            // show the drop down after the ui is loaded
            etHomeAddress.post(() -> etHomeAddress.showDropDown());
        }
        AddressAutocompleteHelper addressAutocomplete = new AddressAutocompleteHelper(requireContext(), etHomeAddress);
        addressAutocomplete.setMapPickerLauncher(mapPickerLauncher);

        setupAddressValidation();

        // Save address handler
        btnSaveHomeAddress.setOnClickListener(v -> {
            String address = etHomeAddress.getText().toString().trim();
            if (address.isEmpty()) {
                tilHomeAddress.setError("Please enter a home address.");
            } else {
                validateAndSave(address);
            }
        });
    }

    private void setupAddressValidation() {
        validationRunnable = () -> {
            String address = etHomeAddress.getText().toString().trim();
            if (address.isEmpty()) {
                tilHomeAddress.setError(null);
                btnSaveHomeAddress.setEnabled(true);
                return;
            }
            performAddressValidation(address);
        };

        etHomeAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilHomeAddress.setError(null);
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
                            tilHomeAddress.setError(null);
                            btnSaveHomeAddress.setEnabled(true);
                        } else {
                            tilHomeAddress.setError("Invalid address. Please check and try again.");
                            btnSaveHomeAddress.setEnabled(false);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        //TODO HANDLE ERRORS
                        //In case of error (no internet ect), don't block the user but  don't clear errors if any

                        btnSaveHomeAddress.setEnabled(true);
                    });
                }
            }
        });
    }

    private void validateAndSave(String address) {
        btnSaveHomeAddress.setEnabled(false);
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
                                listener.onHomeAddressSelected(address);
                                dismiss();
                            }
                        } else {
                            tilHomeAddress.setError("Invalid address. Please check and try again.");
                            btnSaveHomeAddress.setEnabled(true);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        // If validation fails due to network, allow saving anyway to be safe
                        if (listener != null) {
                            listener.onHomeAddressSelected(address);
                            dismiss();
                        }
                    });
                }
            }
        });
    }
}
