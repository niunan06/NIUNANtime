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
import com.example.niunantime.db.Todo;

import android.app.AlertDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class DayDetailFragment extends Fragment {

    private static final String ARG_DAY_START = "day_start";
    private static final String ARG_DAY_LABEL = "day_label";

    private long dayStart;
    private String dayLabel;
    private View view;

    public static DayDetailFragment newInstance(long dayStart, String dayLabel) {
        Bundle args = new Bundle();
        args.putLong(ARG_DAY_START, dayStart);
        args.putString(ARG_DAY_LABEL, dayLabel);
        DayDetailFragment f = new DayDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            dayStart = getArguments().getLong(ARG_DAY_START);
            dayLabel = getArguments().getString(ARG_DAY_LABEL, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_day_detail, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        com.google.android.material.appbar.MaterialToolbar toolbar = v.findViewById(R.id.toolbar);
        toolbar.setTitle(dayLabel);
        toolbar.setNavigationOnClickListener(v2 -> getParentFragmentManager().popBackStack());

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        getParentFragmentManager().popBackStack();
                    }
                }
        );

        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                v.findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_day_events) {
                v.findViewById(R.id.scroll_events).setVisibility(View.VISIBLE);
                v.findViewById(R.id.scroll_todos).setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_day_todos) {
                v.findViewById(R.id.scroll_events).setVisibility(View.GONE);
                v.findViewById(R.id.scroll_todos).setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });

        loadEvents();
        loadTodos();
    }

    private void loadEvents() {
        new Thread(() -> {
            long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
            List<TimeEvent> events = AppDatabase.getInstance(requireContext())
                    .timeEventDao().getEventsSince(dayStart);

            // 过滤出当天的（dayStart ≤ timestamp < dayEnd）
            List<TimeEvent> dayEvents = new ArrayList<>();
            for (TimeEvent e : events) {
                if (e.timestamp >= dayStart && e.timestamp < dayEnd) {
                    dayEvents.add(e);
                }
            }

            requireActivity().runOnUiThread(() -> displayEvents(dayEvents));
        }).start();
    }

    private void displayEvents(List<TimeEvent> events) {
        if (view == null) return;
        LinearLayout container = view.findViewById(R.id.event_list_container);
        container.removeAllViews();

        if (events.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("当天无事件记录");
            empty.setTextColor(0xFF999999);
            empty.setTextSize(15);
            empty.setPadding(0, 40, 0, 0);
            empty.setGravity(View.TEXT_ALIGNMENT_CENTER);
            container.addView(empty);
            return;
        }

        // 按名称分组求和
        Map<String, Long> grouped = new HashMap<>();
        for (TimeEvent e : events) {
            Long sum = grouped.get(e.eventName);
            grouped.put(e.eventName, (sum == null ? 0 : sum) + e.durationSeconds);
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(grouped.entrySet());
        Collections.sort(sorted, (a, b) -> Long.compare(b.getValue(), a.getValue()));

        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;

        for (Map.Entry<String, Long> entry : sorted) {
            String eventName = entry.getKey();

            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_event, container, false);

            TextView tvName = itemView.findViewById(R.id.tv_event_name);
            TextView tvDuration = itemView.findViewById(R.id.tv_event_duration);
            TextView tvLastTime = itemView.findViewById(R.id.tv_last_time);

            tvName.setText(eventName);
            tvDuration.setText(formatDuration(entry.getValue()));
            tvLastTime.setVisibility(View.GONE);

            // 长按删除当天该事件的所有记录
            itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除事件")
                        .setMessage("确定要删除当天所有「" + eventName + "」记录吗？")
                        .setPositiveButton("确定", (d, w) -> {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                AppDatabase db = AppDatabase.getInstance(requireContext());
                                List<TimeEvent> toDelete = db.timeEventDao().getEventsByNameSince(eventName, dayStart);
                                for (TimeEvent e : toDelete) {
                                    if (e.timestamp >= dayStart && e.timestamp < dayEnd) {
                                        db.timeEventDao().delete(e);
                                    }
                                }
                                requireActivity().runOnUiThread(() -> loadEvents());
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });

            container.addView(itemView);
        }
    }

    private void loadTodos() {
        new Thread(() -> {
            long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
            List<Todo> todos = AppDatabase.getInstance(requireContext())
                    .todoDao().getTodosCompletedBetween(dayStart, dayEnd);

            requireActivity().runOnUiThread(() -> displayTodos(todos));
        }).start();
    }

    private void displayTodos(List<Todo> todos) {
        if (view == null) return;
        LinearLayout container = view.findViewById(R.id.todo_list_container);
        container.removeAllViews();

        if (todos.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("当天无已完成待办");
            empty.setTextColor(0xFF999999);
            empty.setTextSize(15);
            empty.setPadding(0, 40, 0, 0);
            empty.setGravity(View.TEXT_ALIGNMENT_CENTER);
            container.addView(empty);
            return;
        }

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (Todo todo : todos) {
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_completed_todo, container, false);

            TextView tvName = itemView.findViewById(R.id.tv_todo_name);
            TextView tvTime = itemView.findViewById(R.id.tv_completed_time);

            tvName.setText(todo.name);
            if (todo.completedTime != null) {
                tvTime.setText("完成于 " + timeFmt.format(new Date(todo.completedTime)));
            }

            // 长按删除
            itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除待办")
                        .setMessage("确定要删除「" + todo.name + "」吗？")
                        .setPositiveButton("确定", (d, w) -> {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                AppDatabase.getInstance(requireContext()).todoDao().delete(todo);
                                requireActivity().runOnUiThread(() -> loadTodos());
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });

            container.addView(itemView);
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
}
