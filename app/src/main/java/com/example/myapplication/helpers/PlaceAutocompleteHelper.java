package com.example.myapplication.helpers;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.R; // Import R class for custom layout
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.List;

public class PlaceAutocompleteHelper {

    private static final String TAG = "PlaceAutocompleteHelper";
    private final Context context;
    private final AutoCompleteTextView autoCompleteTextView;
    private PlacesClient placesClient;
    private AutocompleteSessionToken autocompleteSessionToken;

    public PlaceAutocompleteHelper(Context context, AutoCompleteTextView autoCompleteTextView) {
        this.context = context;
        this.autoCompleteTextView = autoCompleteTextView;
        initPlacesClient();
        setupAddressAutocomplete();
    }

    private void initPlacesClient() {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.GOOGLE_API_KEY);
        }
        placesClient = Places.createClient(context);
        autocompleteSessionToken = AutocompleteSessionToken.newInstance();
    }

    private void setupAddressAutocomplete() {
        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) { // Only trigger search if user typed more than 2 characters
                    fetchAutocompletePredictions(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Automatically open dropdown when the AutoCompleteTextView gains focus
        autoCompleteTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    autoCompleteTextView.showDropDown();
                }
            }
        });
    }

    private void fetchAutocompletePredictions(String query) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(autocompleteSessionToken)
                .setQuery(query)
                .build();

        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            List<String> addressSuggestions = new ArrayList<>();
            for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                addressSuggestions.add(prediction.getFullText(null).toString());
            }

            // Use custom adapter with custom layout
            AutocompleteAdapter adapter = new AutocompleteAdapter(context, addressSuggestions);
            autoCompleteTextView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
            // Ensure dropdown is shown if there are suggestions and focused
            if (autoCompleteTextView.isFocused() && !addressSuggestions.isEmpty()) {
                autoCompleteTextView.showDropDown();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Autocomplete prediction fetch failed", e);
        });
    }

    // Custom ArrayAdapter for autocomplete suggestions
    private static class AutocompleteAdapter extends ArrayAdapter<String> {

        private final Context context;
        private final List<String> suggestions;

        public AutocompleteAdapter(@NonNull Context context, @NonNull List<String> objects) {
            super(context, 0, objects);
            this.context = context;
            this.suggestions = objects;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_autocomplete_suggestion, parent, false);
            }

            TextView autocompleteTextView = convertView.findViewById(R.id.autocomplete_text);
            if (autocompleteTextView != null) {
                autocompleteTextView.setText(suggestions.get(position));
            }

            return convertView;
        }

        @Override
        public int getCount() {
            return suggestions.size();
        }

        @Nullable
        @Override
        public String getItem(int position) {
            return suggestions.get(position);
        }
    }

    public void release() {
        if (placesClient != null) {
            placesClient = null;
            autocompleteSessionToken = null;
        }
    }
}