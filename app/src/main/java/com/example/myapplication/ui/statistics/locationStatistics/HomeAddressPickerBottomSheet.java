package com.example.myapplication.ui.statistics.locationStatistics;

import com.example.myapplication.R;

import java.util.Map;

public class HomeAddressPickerBottomSheet extends BaseAddressPickerBottomSheet {

    public interface OnHomeAddressSelectedListener {
        void onHomeAddressSelected(String address);
    }

    private OnHomeAddressSelectedListener listener;

    public static HomeAddressPickerBottomSheet newInstance(OnHomeAddressSelectedListener listener, Map<String, Long> placeDurations) {
        HomeAddressPickerBottomSheet fragment = new HomeAddressPickerBottomSheet();
        fragment.listener = listener;
        fragment.setArguments(createArgs(placeDurations));
        return fragment;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.bottom_sheet_home_address_picker;
    }

    @Override
    protected int getInputEditTextResId() {
        return R.id.etHomeAddressInput;
    }

    @Override
    protected int getTextInputLayoutResId() {
        return R.id.tilHomeAddress;
    }

    @Override
    protected int getSaveButtonResId() {
        return R.id.btnSaveHomeAddress;
    }

    @Override
    protected String getEmptyErrorMessage() {
        return "Please enter a home address.";
    }

    @Override
    protected void onAddressSaved(String address) {
        if (listener != null) {
            listener.onHomeAddressSelected(address);
        }
    }
}
