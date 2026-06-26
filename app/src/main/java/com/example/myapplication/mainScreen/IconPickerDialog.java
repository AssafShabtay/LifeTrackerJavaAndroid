package com.example.myapplication.mainScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getIconList;
import static com.example.myapplication.helpers.ColorAndIcons.DEFAULT_COLORS;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class IconPickerDialog extends DialogFragment {

    public interface OnIconSelectedListener {
        void onIconSelected(String iconName, int color);
    }

    interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    interface OnIconClickListener {
        void onIconClick(IconItem icon);
    }

    private RecyclerView recyclerColors;
    private RecyclerView recyclerIcons;
    private EditText searchEditText;

    private String selectedIcon;
    private int selectedColor;
    private OnIconSelectedListener listener;

    private List<IconItem> allIcons;
    private IconAdapter iconAdapter;

    public static IconPickerDialog newInstance(String currentIcon, int currentColor) {
        IconPickerDialog fragment = new IconPickerDialog();
        fragment.selectedIcon = currentIcon;
        fragment.selectedColor = currentColor;
        return fragment;
    }

    public void setOnIconSelectedListener(OnIconSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_icon_picker, container, false);

        recyclerColors = view.findViewById(R.id.recyclerColors);
        recyclerIcons = view.findViewById(R.id.recyclerIcons);
        searchEditText = view.findViewById(R.id.searchEditText);

        setupColorRecycler();
        setupIconRecycler();
        setupSearchBar();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            android.view.Window window = getDialog().getWindow();

            // 1. Make the dialog window background transparent so the card corners and shadow show
            window.setBackgroundDrawableResource(android.R.color.transparent);

            // 2. Remove the dimming effect on the background
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // Example: Align to the top with a margin
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 70; // Offset from the top in pixels
            window.setAttributes(params);

            // 3. Set the layout to wrap content so it floats like a popover
            // Fix: Changed width to MATCH_PARENT for better dialog container sizing
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, // Changed from WRAP_CONTENT
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void setupSearchBar() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterIcons(s.toString());
            }
        });
    }

    private void filterIcons(String query) {
        List<IconItem> filteredList = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase(); // Convert query to lower case once
        for (IconItem item : allIcons) {
            if (item.name.toLowerCase().contains(lowerCaseQuery) ||
                (item.keywords != null && containsKeyword(item.keywords, lowerCaseQuery))) { // Check keywords
                filteredList.add(item);
            }
        }
        iconAdapter.updateList(filteredList);
    }

    private boolean containsKeyword(List<String> keywords, String query) {
        for (String keyword : keywords) {
            if (keyword.toLowerCase().contains(query)) {
                return true;
            }
        }
        return false;
    }

    private void showIcons() {
        allIcons = getIconList();
        iconAdapter = new IconAdapter(allIcons, icon -> {
            selectedIcon = icon.name;
            if (listener != null) listener.onIconSelected(selectedIcon, selectedColor);
            dismiss();
        });
        recyclerIcons.setAdapter(iconAdapter);
    }

    private void setupColorRecycler() {
        recyclerColors.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerColors.setAdapter(new ColorAdapter(getColorArray(), color -> {
            selectedColor = color;
            if (recyclerColors.getAdapter() != null) {
                recyclerColors.getAdapter().notifyDataSetChanged();
            }
            if (recyclerIcons.getAdapter() != null) {
                recyclerIcons.getAdapter().notifyDataSetChanged();
            }
        }));
    }

    private void setupIconRecycler() {
        recyclerIcons.setLayoutManager(new GridLayoutManager(getContext(), 7));
        showIcons();
    }

    private int[] getColorArray() {
        if (getContext() == null) {
            return new int[0];
        }
        int[] colors = new int[DEFAULT_COLORS.length];
        for (int i = 0; i < DEFAULT_COLORS.length; i++) {
            colors[i] = ContextCompat.getColor(getContext(), DEFAULT_COLORS[i]);
        }
        return colors;
    }

    public static class IconItem {
        String name;
        int resId;
        List<String> keywords; // Add this line
        public IconItem(String name, int resId) {
            this.name = name;
            this.resId = resId;
            this.keywords = new ArrayList<>(); // Initialize the list
        }

        public IconItem(String name, int resId, List<String> keywords) { // Add new constructor
            this.name = name;
            this.resId = resId;
            this.keywords = keywords;
        }
    }

    class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final int[] colors;
        private final OnColorSelectedListener colorListener;

        ColorAdapter(int[] colors, OnColorSelectedListener colorListener) {
            this.colors = colors;
            this.colorListener = colorListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = new View(parent.getContext());
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            int size = (int) (32 * density);
            int margin = (int) (8 * density);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            v.setLayoutParams(lp);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int color = colors[position];
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            // Set the color with 50% opacity (0x80 is for 50% alpha)
            gd.setColor(color & 0x80FFFFFF); // Apply 50% alpha to the color
            holder.view.setBackground(gd);

            if (color == selectedColor) {
                gd.setStroke((int)(2 * holder.view.getContext().getResources().getDisplayMetrics().density), Color.GRAY);
            } else {
                gd.setStroke(0, Color.TRANSPARENT);
            }

            holder.view.setOnClickListener(v -> {
                colorListener.onColorSelected(color);
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return colors.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View view;
            ViewHolder(View v) {
                super(v);
                view = v;
            }
        }
    }

    class IconAdapter extends RecyclerView.Adapter<IconAdapter.ViewHolder> {
        private List<IconItem> icons; // Removed 'final' so we can update it
        private final OnIconClickListener iconListener;

        IconAdapter(List<IconItem> icons, OnIconClickListener iconListener) {
            this.icons = icons;
            this.iconListener = iconListener;
        }

        // Added method to update list when searching
        public void updateList(List<IconItem> newIcons) {
            this.icons = newIcons;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_picker, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            IconItem item = icons.get(position);
            holder.iconView.setImageResource(item.resId);
            holder.iconView.setColorFilter(selectedColor);

            if (item.name.equalsIgnoreCase(selectedIcon)) {
                holder.background.setVisibility(View.VISIBLE);
            } else {
                holder.background.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> iconListener.onIconClick(item));
        }

        @Override
        public int getItemCount() {
            return icons.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView iconView;
            View background;
            ViewHolder(View v) {
                super(v);
                iconView = v.findViewById(R.id.iconView);
                background = v.findViewById(R.id.iconBackground);
            }
        }
    }
}