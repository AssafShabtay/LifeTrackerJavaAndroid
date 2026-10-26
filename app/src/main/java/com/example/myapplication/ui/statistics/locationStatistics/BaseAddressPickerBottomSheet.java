package com.example.myapplication.ui.statistics.locationStatistics;

import android.app.Activity;
import android.content.Context;
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
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.ui.general.PlaceDropdownAdapter;
import com.example.myapplication.ui.home.map.MapPickerActivity;
import com.example.myapplication.util.AddressAutocompleteHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class BaseAddressPickerBottomSheet extends BottomSheetDialogFragment {

    protected static final String ARG_PLACE_SUGGESTIONS = "place_suggestions";

    protected AutoCompleteTextView etAddress;
    protected TextInputLayout tilAddress;
    protected Button btnSaveAddress;
    protected List<String> placeSuggestions;
    private final Handler validationHandler = new Handler(Looper.getMainLooper());
    private Runnable validationRunnable;

    private final ActivityResultLauncher<Intent> mapPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_ADDRESS);
                    if (address != null && etAddress != null) {
                        etAddress.setText(address);
                    }
                }
            }
    );

    protected static Bundle createArgs(Map<String, Long> placeDurations) {
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
        return args;
    }

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract int getInputEditTextResId();

    protected abstract int getTextInputLayoutResId();

    protected abstract int getSaveButtonResId();

    protected abstract String getEmptyErrorMessage();

    protected abstract void onAddressSaved(String address);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            placeSuggestions = getArguments().getStringArrayList(ARG_PLACE_SUGGESTIONS);
        }
        return inflater.inflate(getLayoutResId(), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etAddress = view.findViewById(getInputEditTextResId());
        tilAddress = view.findViewById(getTextInputLayoutResId());
        btnSaveAddress = view.findViewById(getSaveButtonResId());

        if (placeSuggestions != null && !placeSuggestions.isEmpty() && etAddress != null) {
            PlaceDropdownAdapter adapter = new PlaceDropdownAdapter(requireContext(), placeSuggestions);
            etAddress.setAdapter(adapter);

            etAddress.post(() -> etAddress.showDropDown());
        }

        if (etAddress != null) {
            AddressAutocompleteHelper addressAutocomplete = new AddressAutocompleteHelper(requireContext(), etAddress);
            addressAutocomplete.setMapPickerLauncher(mapPickerLauncher);
            setupAddressValidation();
        }

        if (btnSaveAddress != null) {
            btnSaveAddress.setOnClickListener(v -> {
                String address = etAddress != null ? etAddress.getText().toString().trim() : "";
                if (address.isEmpty()) {
                    if (tilAddress != null) {
                        tilAddress.setError(getEmptyErrorMessage());
                    }
                } else {
                    validateAndSave(address);
                }
            });
        }
    }

    private void setupAddressValidation() {
        validationRunnable = () -> {
            if (etAddress == null || tilAddress == null || btnSaveAddress == null) return;
            String address = etAddress.getText().toString().trim();
            if (address.isEmpty()) {
                tilAddress.setError(null);
                btnSaveAddress.setEnabled(true);
                return;
            }
            performAddressValidation(address);
        };

        etAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilAddress != null) {
                    tilAddress.setError(null);
                }
                validationHandler.removeCallbacks(validationRunnable);
                validationHandler.postDelayed(validationRunnable, 1000);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performAddressValidation(String address) {
        if (getActivity() == null) return;
        LifeTrackerApp app = (LifeTrackerApp) requireActivity().getApplication();
        app.getBackgroundWriteExecutor().execute(() -> {
            Context context = getContext();
            if (context == null) return;
            Geocoder geocoder = new Geocoder(context.getApplicationContext());
            try {
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                boolean isValid = addresses != null && !addresses.isEmpty();

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (isValid) {
                            if (tilAddress != null) tilAddress.setError(null);
                            if (btnSaveAddress != null) btnSaveAddress.setEnabled(true);
                        } else {
                            if (tilAddress != null) tilAddress.setError("Invalid address. Please check and try again.");
                            if (btnSaveAddress != null) btnSaveAddress.setEnabled(false);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (tilAddress != null) tilAddress.setError(null);
                        if (btnSaveAddress != null) btnSaveAddress.setEnabled(true);
                    });
                }
            }
        });
    }

    private void validateAndSave(String address) {
        if (btnSaveAddress != null) btnSaveAddress.setEnabled(false);
        if (getActivity() == null) return;
        LifeTrackerApp app = (LifeTrackerApp) requireActivity().getApplication();
        app.getBackgroundWriteExecutor().execute(() -> {
            Context context = getContext();
            if (context == null) return;
            Geocoder geocoder = new Geocoder(context.getApplicationContext());
            try {
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                boolean isValid = addresses != null && !addresses.isEmpty();

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (isValid) {
                            onAddressSaved(address);
                            dismiss();
                        } else {
                            if (tilAddress != null) tilAddress.setError("Invalid address. Please check and try again.");
                            if (btnSaveAddress != null) btnSaveAddress.setEnabled(true);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        onAddressSaved(address);
                        dismiss();
                    });
                }
            }
        });
    }
}
