package com.example.myapplication.ui.general;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;

import java.util.List;

public class PlaceDropdownAdapter extends ContainsArrayAdapter {

    public PlaceDropdownAdapter(@NonNull Context context, @NonNull List<String> objects) {
        super(context, R.layout.item_place_dropdown, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_place_dropdown, parent, false);
        }

        String item = getItem(position);
        TextView tvPlaceName = convertView.findViewById(R.id.tvPlaceName);
        if (tvPlaceName != null) {
            tvPlaceName.setText(item);
        }

        return convertView;
    }
}
