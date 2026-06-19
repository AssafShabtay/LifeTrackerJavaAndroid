package com.example.myapplication.mainScreen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.myapplication.helpers.PlaceAutocompleteHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class HomeAddressPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnHomeAddressSelectedListener {
        void onAddressSelected(String address);
    }

    private AutoCompleteTextView etHomeAddress;
    private Button btnSaveHomeAddress;
    private OnHomeAddressSelectedListener listener;

    public static HomeAddressPickerBottomSheet newInstance(OnHomeAddressSelectedListener listener) {
        HomeAddressPickerBottomSheet fragment = new HomeAddressPickerBottomSheet();
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_home_address_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etHomeAddress = view.findViewById(R.id.etHomeAddressInput);
        btnSaveHomeAddress = view.findViewById(R.id.btnSaveHomeAddress);

        new PlaceAutocompleteHelper(requireContext(), etHomeAddress);

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
