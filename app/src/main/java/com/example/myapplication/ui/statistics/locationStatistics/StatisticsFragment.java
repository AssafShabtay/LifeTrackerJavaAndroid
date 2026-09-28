package com.example.myapplication.ui.statistics.locationStatistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.myapplication.R;
import com.example.myapplication.ui.statistics.efficiency.EfficiencyFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StatisticsFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);

        StatisticsPagerAdapter adapter = new StatisticsPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Statistics");
            } else {
                tab.setText("Efficiency");
            }
        }).attach();
    }

    private static class StatisticsPagerAdapter extends FragmentStateAdapter {

        public StatisticsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new GeneralStatisticsFragment();
            } else {
                return new EfficiencyFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
