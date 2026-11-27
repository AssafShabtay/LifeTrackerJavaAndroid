package com.trace.ui.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.trace.R;
import com.trace.ui.statistics.askTimeline.AskTimelineBottomSheetFragment;
import com.trace.ui.statistics.screenTime.ScreenTimeFragment;

public class StatisticsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View askTimelineButton = view.findViewById(R.id.ask_timeline_button);
        if (askTimelineButton != null) {
            askTimelineButton.setOnClickListener(v -> {
                AskTimelineBottomSheetFragment bottomSheet =
                        AskTimelineBottomSheetFragment.newInstance();
                bottomSheet.show(getChildFragmentManager(), "AskTimelineBottomSheet");
            });
        }

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.statistics_container, new ScreenTimeFragment())
                    .commit();
        }
    }

    public void selectScreenTimeTab() {
        // Maintained for compatibility with MainActivity.navigateToScreenTimeStatistics()
    }
}
