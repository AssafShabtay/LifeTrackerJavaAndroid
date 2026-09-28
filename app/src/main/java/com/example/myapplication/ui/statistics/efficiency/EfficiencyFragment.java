package com.example.myapplication.ui.statistics.efficiency;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;

public class EfficiencyFragment extends Fragment {

    private EfficiencyStatsManager efficiencyStatsManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_efficiency, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        efficiencyStatsManager = new EfficiencyStatsManager(requireContext(), mainHandler, view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (efficiencyStatsManager != null) {
            efficiencyStatsManager.loadEfficiencyStats();
        }
    }
}
