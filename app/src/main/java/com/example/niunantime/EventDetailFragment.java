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

import com.example.niunantime.databinding.FragmentEventDetailBinding;
import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.TimeEvent;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventDetailFragment extends Fragment {

    private static final String ARG_EVENT_NAME = "event_name";
    private static final String ARG_START_TIME = "start_time";

    private FragmentEventDetailBinding binding;
    private boolean showDate; // true = 显示日期（周视图），false = 只显示时间（日视图）

    public static EventDetailFragment newInstance(String eventName, long startTime) {
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_NAME, eventName);
        args.putLong(ARG_START_TIME, startTime);
        EventDetailFragment f = new EventDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentEventDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        String eventName = args.getString(ARG_EVENT_NAME, "");
        long startTime = args.getLong(ARG_START_TIME, 0);

        // 判断是否显示日期：如果 startTime 是今天的0点，就是日视图
        Calendar todayMidnight = Calendar.getInstance();
        todayMidnight.set(Calendar.HOUR_OF_DAY, 0);
        todayMidnight.set(Calendar.MINUTE, 0);
        todayMidnight.set(Calendar.SECOND, 0);
        todayMidnight.set(Calendar.MILLISECOND, 0);
        showDate = startTime != todayMidnight.getTimeInMillis();

        // 工具栏
        binding.toolbar.setTitle(eventName);
        binding.toolbar.setNavigationOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        getParentFragmentManager().popBackStack();
                    }
                }
        );

        // 更新表头
        if (showDate) {
            binding.tvColumnTime.setText("日期");
        }

        // 加载数据
        loadSessions(eventName, startTime);
    }

    private void loadSessions(String eventName, long startTime) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<TimeEvent> sessions = db.timeEventDao()
                    .getEventsByNameSince(eventName, startTime);

            requireActivity().runOnUiThread(() -> displaySessions(sessions));
        }).start();
    }

    private void displaySessions(List<TimeEvent> sessions) {
        binding.sessionListContainer.removeAllViews();

        if (sessions == null || sessions.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("暂无详细记录");
            empty.setTextColor(0xFF999999);
            empty.setTextSize(15);
            empty.setPadding(0, 40, 0, 0);
            empty.setGravity(View.TEXT_ALIGNMENT_CENTER);
            binding.sessionListContainer.addView(empty);
            return;
        }

        // 计算总时长
        long totalDuration = 0;
        for (TimeEvent s : sessions) {
            totalDuration += s.durationSeconds;
        }

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateTimeFmt = new SimpleDateFormat("M/d HH:mm", Locale.getDefault());

        for (TimeEvent session : sessions) {
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_event_session, binding.sessionListContainer, false);

            TextView tvTime = itemView.findViewById(R.id.tv_session_time);
            TextView tvDuration = itemView.findViewById(R.id.tv_session_duration);
            TextView tvPercent = itemView.findViewById(R.id.tv_session_percent);

            Date date = new Date(session.timestamp);
            if (showDate) {
                tvTime.setText(dateTimeFmt.format(date));
            } else {
                tvTime.setText(timeFmt.format(date));
            }
            tvDuration.setText(formatDuration(session.durationSeconds));

            if (totalDuration > 0) {
                float percent = (float) session.durationSeconds / totalDuration * 100f;
                tvPercent.setText(String.format(Locale.getDefault(), "%.1f%%", percent));
            } else {
                tvPercent.setText("-");
            }

            binding.sessionListContainer.addView(itemView);
        }
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d时%02d分%02d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%d分%02d秒", minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%d秒", secs);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
