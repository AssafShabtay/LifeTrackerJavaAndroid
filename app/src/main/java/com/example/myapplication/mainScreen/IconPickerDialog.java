package com.example.myapplication.mainScreen;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.Arrays;
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

    private String selectedIcon;
    private int selectedColor;
    private OnIconSelectedListener listener;

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

        setupColorRecycler();
        setupIconRecycler();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Make the dialog wider
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showIcons() {
        recyclerIcons.setAdapter(new IconAdapter(getIconList(), icon -> {
            selectedIcon = icon.name;
            if (listener != null) listener.onIconSelected(selectedIcon, selectedColor);
            dismiss();
        }));
    }

    private void setupColorRecycler() {
        recyclerColors.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerColors.setAdapter(new ColorAdapter(getColorList(), color -> {
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

    private List<Integer> getColorList() {
        return Arrays.asList(
                0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
                0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
                0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
                0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
                0xFF795548, 0xFF9E9E9E, 0xFF607D8B
        );
    }

    private List<IconItem> getIconList() {
        return Arrays.asList(
                new IconItem("Home", R.drawable.ic_home),
                new IconItem("Work", R.drawable.ic_work),
                new IconItem("Gym", R.drawable.ic_gym),
                new IconItem("School", R.drawable.ic_school),
                new IconItem("Restaurant", R.drawable.ic_restaurant),
                new IconItem("Coffee", R.drawable.ic_coffee),
                new IconItem("Car", R.drawable.ic_car),
                new IconItem("Bike", R.drawable.ic_bike),
                new IconItem("Walk", R.drawable.ic_walk),
                new IconItem("Still", R.drawable.ic_still)
        );
    }

    static class IconItem {
        String name;
        int resId;
        IconItem(String name, int resId) {
            this.name = name;
            this.resId = resId;
        }
    }

    class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final List<Integer> colors;
        private final OnColorSelectedListener colorListener;

        ColorAdapter(List<Integer> colors, OnColorSelectedListener colorListener) {
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
            int color = colors.get(position);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            holder.view.setBackground(gd);

            if (color == selectedColor) {
                gd.setStroke((int)(2 * holder.view.getContext().getResources().getDisplayMetrics().density), Color.BLACK);
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
            return colors.size();
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
        private final List<IconItem> icons;
        private final OnIconClickListener iconListener;

        IconAdapter(List<IconItem> icons, OnIconClickListener iconListener) {
            this.icons = icons;
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
