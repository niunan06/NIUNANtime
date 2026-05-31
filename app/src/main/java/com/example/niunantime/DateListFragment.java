package com.example.niunantime;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.TimeEvent;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class DateListFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_date_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        getParentFragmentManager().popBackStack();
                    }
                }
        );

        loadDates(view);
    }

    private void loadDates(View view) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<TimeEvent> allEvents = db.timeEventDao().getAllEvents();

            // 提取有事件的日期
            HashSet<String> dateSet = new HashSet<>();
            java.util.ArrayList<Long> dateTimestamps = new java.util.ArrayList<>();

            for (TimeEvent e : allEvents) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(e.timestamp);
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);
                int day = c.get(Calendar.DAY_OF_MONTH);

                String key = year + "-" + month + "-" + day;
                if (!dateSet.contains(key)) {
                    dateSet.add(key);

                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    dateTimestamps.add(c.getTimeInMillis());
                }
            }

            // 从新到旧排序
            java.util.Collections.sort(dateTimestamps, java.util.Collections.reverseOrder());

            requireActivity().runOnUiThread(() -> displayDates(view, dateTimestamps));
        }).start();
    }

    private void displayDates(View view, java.util.ArrayList<Long> dateTimestamps) {
        LinearLayout container = view.findViewById(R.id.date_list_container);
        TextView tvEmpty = view.findViewById(R.id.tv_empty);

        container.removeAllViews();

        if (dateTimestamps.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);

        for (final long dayStart : dateTimestamps) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(dayStart);
            String label = (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";

            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_date_entry, container, false);

            TextView tvLabel = itemView.findViewById(R.id.tv_date_label);
            tvLabel.setText(label);

            itemView.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showDayDetailFragment(dayStart, label);
                }
            });

            container.addView(itemView);
        }
    }
}
