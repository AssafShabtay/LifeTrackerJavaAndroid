package com.example.myapplication.helpers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class PlaceDropdownAdapter extends ArrayAdapter<String> {
    private final List<String> items;
    private final List<String> filteredItems;

    public PlaceDropdownAdapter(@NonNull Context context, @NonNull List<String> objects) {
        super(context, R.layout.item_place_dropdown, objects);
        this.items = new ArrayList<>(objects);
        this.filteredItems = new ArrayList<>(objects);
    }

    @Override
    public int getCount() {
        return filteredItems.size();
    }

    @Nullable
    @Override
    public String getItem(int position) {
        return filteredItems.get(position);
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

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = items;
                    results.count = items.size();
                } else {
                    String filterString = constraint.toString().toLowerCase();
                    ArrayList<String> filtered = new ArrayList<>();
                    for (String item : items) {
                        if (item.toLowerCase().contains(filterString)) {
                            filtered.add(item);
                        }
                    }
                    results.values = filtered;
                    results.count = filtered.size();
                }
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredItems.clear();
                if (results.values != null) {
                    filteredItems.addAll((List<String>) results.values);
                }
                notifyDataSetChanged();
            }
        };
    }
}
