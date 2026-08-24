package com.example.myapplication.mainScreen.homeScreen;

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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.example.myapplication.R;

import com.example.myapplication.helpers.ColorAndIcons.IconItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IconPickerDialog extends DialogFragment {


    interface OnIconClickListener {
        void onIconClick(IconItem icon);
    }

    interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private RecyclerView recyclerColors;
    private RecyclerView recyclerIcons;
    private EditText searchEditText;

    private String selectedIcon;
    private int selectedColor;

    private List<IconItem> allIcons;
    private IconAdapter iconAdapter;

    public static IconPickerDialog newInstance(String currentIcon, int currentColor) {
        IconPickerDialog fragment = new IconPickerDialog();
        fragment.selectedIcon = currentIcon;
        fragment.selectedColor = currentColor;
        return fragment;
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
            // transparent so drop shadow works
            window.setBackgroundDrawableResource(android.R.color.transparent);
            //Remove the dimming effect on the background
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // position the dialog
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 70;
            window.setAttributes(params);

            // layout is wrap_content so it floats like a popover
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void setupSearchBar() {
        // listens to text changes in the search bar
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            // Filter icons when the text changes
            @Override
            public void afterTextChanged(Editable text) {
                filterIcons(text.toString());
            }
        });
    }

    private void filterIcons(String query) {
        // filters the list based on the keywords
        List<IconItem> filteredList = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase();
        for (IconItem item : allIcons) {
            if (item.getName().toLowerCase().contains(lowerCaseQuery) ||
                (item.getKeywords() != null && containsKeyword(item.getKeywords(), lowerCaseQuery))) { // Check keywords
                filteredList.add(item);
            }
        }
        iconAdapter.submitList(filteredList); // submit the filtered list to recycle view
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
        // initialize the adapter and the icon list
        allIcons = getIconList();
        iconAdapter = new IconAdapter(icon -> { // implements onIconClick
            selectedIcon = icon.getName();

            // Set the result before dismissing
            Bundle result = new Bundle();
            result.putString("selectedIcon", selectedIcon);
            result.putInt("selectedColor", selectedColor);
            getParentFragmentManager().setFragmentResult("icon_picker_request", result);

            dismiss();
        });
        recyclerIcons.setAdapter(iconAdapter);
        iconAdapter.submitList(allIcons);
    }



    private void setupIconRecycler() {
        recyclerIcons.setLayoutManager(new GridLayoutManager(getContext(), 7));
        showIcons();
    }


    private void setupColorRecycler() {
        recyclerColors.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerColors.setAdapter(new ColorAdapter(getColorArray(), color -> { //  implements onColorSelected
            selectedColor = color;
            if (iconAdapter != null) {
                iconAdapter.notifyItemRangeChanged(0, iconAdapter.getItemCount());
            }
        }));
    }

    private int[] getColorArray() {
        // extract colors from ColorsAndIcons to a list containing the colors in the correct format
        if (getContext() == null) {
            return new int[0];
        }
        int[] colors = new int[DEFAULT_COLORS.length];
        for (int i = 0; i < DEFAULT_COLORS.length; i++) {
            colors[i] = ContextCompat.getColor(getContext(), DEFAULT_COLORS[i]);
        }
        return colors;
    }


    class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final int[] colors;
        private final OnColorSelectedListener colorListener;
        private int selectedPosition = -1;

        ColorAdapter(int[] colors, OnColorSelectedListener colorListener) {
            this.colors = colors;
            this.colorListener = colorListener;

            for (int i = 0; i < colors.length; i++) {
                if (colors[i] == selectedColor) {
                    selectedPosition = i;
                    break;
                }
            }
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
            GradientDrawable gradient = new GradientDrawable();
            gradient.setShape(GradientDrawable.OVAL);
            gradient.setColor(color & 0x80FFFFFF); // 50% opacity
            holder.view.setBackground(gradient);

            // check which color is selected and add to it a stroke to highlight it
            if (position == selectedPosition) {
                gradient.setStroke((int)(2 * holder.view.getContext().getResources().getDisplayMetrics().density), Color.GRAY);
            } else {
                gradient.setStroke(0, Color.TRANSPARENT);
            }

            holder.view.setOnClickListener(v -> {
                int currentPos = holder.getBindingAdapterPosition();

                // check if the position is invalid, or the color already selected
                if (currentPos == RecyclerView.NO_POSITION || currentPos == selectedPosition) {
                    return;
                }

                int previousPosition = selectedPosition;
                selectedPosition = currentPos;

                // unhighlight the old selection
                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition);
                }
                notifyItemChanged(selectedPosition);

                colorListener.onColorSelected(colors[selectedPosition]); // select color
            });
        }

        @Override
        public int getItemCount() {
            return colors.length;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View view;
            ViewHolder(View v) {
                super(v);
                view = v;
            }
        }
    }

    static class IconItemDiffCallback extends DiffUtil.ItemCallback<IconItem> {
        // handles icon filtering in an optimized way, instead of rebuilding the entire list, remove items that don't match the query
        @Override
        public boolean areItemsTheSame(@NonNull IconItem oldItem, @NonNull IconItem newItem) {
            return oldItem.getName().equals(newItem.getName());
        }

        @Override
        public boolean areContentsTheSame(@NonNull IconItem oldItem, @NonNull IconItem newItem) {
            return oldItem.getName().equals(newItem.getName()) &&
                   oldItem.getResId() == newItem.getResId() &&
                   Objects.equals(oldItem.getKeywords(), newItem.getKeywords());
        }
    }

    class IconAdapter extends ListAdapter<IconItem, IconAdapter.ViewHolder> {
        private final OnIconClickListener iconListener;

        IconAdapter(OnIconClickListener iconListener) {
            super(new IconItemDiffCallback());
            this.iconListener = iconListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_picker, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            IconItem item = getItem(position);
            holder.iconView.setImageResource(item.getResId());
            holder.iconView.setColorFilter(selectedColor);

            if (item.getName().equalsIgnoreCase(selectedIcon)) {
                holder.background.setVisibility(View.VISIBLE);
            } else {
                holder.background.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> iconListener.onIconClick(item));
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
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