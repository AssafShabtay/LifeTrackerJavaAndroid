package com.example.myapplication.ui.statistics.locationStatistics;

import com.example.myapplication.R;

import java.util.Map;

public class WorkAddressPickerBottomSheet extends BaseAddressPickerBottomSheet {

    public interface OnWorkAddressSelectedListener {
        void onWorkAddressSelected(String address);
    }

    private OnWorkAddressSelectedListener listener;

    public static WorkAddressPickerBottomSheet newInstance(OnWorkAddressSelectedListener listener, Map<String, Long> placeDurations) {
        WorkAddressPickerBottomSheet fragment = new WorkAddressPickerBottomSheet();
        fragment.listener = listener;
        fragment.setArguments(createArgs(placeDurations));
        return fragment;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.bottom_sheet_work_address_picker;
    }

    @Override
    protected int getInputEditTextResId() {
        return R.id.etWorkAddressInput;
    }

    @Override
    protected int getTextInputLayoutResId() {
        return R.id.tilWorkAddress;
    }

    @Override
    protected int getSaveButtonResId() {
        return R.id.btnSaveWorkAddress;
    }

    @Override
    protected String getEmptyErrorMessage() {
        return "Please enter a work address.";
    }

    @Override
    protected void onAddressSaved(String address) {
        if (listener != null) {
            listener.onWorkAddressSelected(address);
        }
    }
}
