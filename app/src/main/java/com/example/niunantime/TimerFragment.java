package com.example.niunantime;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.niunantime.databinding.FragmentTimerBinding;
import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.TimeEvent;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TimerFragment extends Fragment {

    private FragmentTimerBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTimerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
        loadBackground();
    }

    public void reloadBackground() {
        loadBackground();
    }

    private void loadBackground() {
        try {
            SharedPreferences prefs = requireActivity()
                    .getSharedPreferences("app_prefs", getContext().MODE_PRIVATE);
            String uriStr = prefs.getString("bg_timer", null);
            if (uriStr != null) {
                Uri uri = Uri.parse(uriStr);
                ContentResolver cr = requireContext().getContentResolver();
                InputStream is = cr.openInputStream(uri);
                if (is != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bitmap != null) {
                        binding.ivBackground.setImageBitmap(bitmap);
                        binding.ivBackground.setVisibility(View.VISIBLE);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        binding.ivBackground.setImageDrawable(null);
        binding.ivBackground.setVisibility(View.GONE);
    }

    private void loadEvents() {
        new Thread(() -> {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            List<TimeEvent> events = AppDatabase.getInstance(requireContext())
                    .timeEventDao().getTodayEvents(todayStart);
            requireActivity().runOnUiThread(() -> displayEvents(events));
        }).start();
    }

    private void displayEvents(List<TimeEvent> events) {
        binding.eventListContainer.removeAllViews();

        if (events == null || events.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        binding.tvEmpty.setVisibility(View.GONE);

        // 按名称分组：总时长、最近一次时间戳
        Map<String, long[]> grouped = new HashMap<>();
        for (TimeEvent e : events) {
            long[] data = grouped.get(e.eventName);
            if (data == null) {
                grouped.put(e.eventName, new long[]{e.durationSeconds, e.timestamp});
            } else {
                data[0] += e.durationSeconds;
                if (e.timestamp > data[1]) data[1] = e.timestamp;
            }
        }

        // 按总时长降序排列
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(grouped.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (Map.Entry<String, long[]> entry : sorted) {
            String eventName = entry.getKey();
            long totalDuration = entry.getValue()[0];
            long lastTimestamp = entry.getValue()[1];

            View itemView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_event, binding.eventListContainer, false);

            TextView tvName = itemView.findViewById(R.id.tv_event_name);
            TextView tvDuration = itemView.findViewById(R.id.tv_event_duration);
            TextView tvLastTime = itemView.findViewById(R.id.tv_last_time);

            tvName.setText(eventName);
            tvDuration.setText(formatDuration(totalDuration));
            tvLastTime.setText("上次进行: " + timeFmt.format(new Date(lastTimestamp)));

            // 点击继续此事件
            itemView.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showContinueEventDialog(0, eventName);
                }
            });

            // 长按删除今天该事件的所有记录
            String nameForDelete = eventName;
            itemView.setOnLongClickListener(v -> {
                showDeleteDialog(nameForDelete);
                return true;
            });

            binding.eventListContainer.addView(itemView);
        }
    }

    private void showDeleteDialog(String eventName) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除事件")
                .setMessage("确定要删除今天所有「" + eventName + "」记录吗？")
                .setPositiveButton("确定", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        AppDatabase.getInstance(requireContext())
                                .timeEventDao().deleteByNameSince(eventName, cal.getTimeInMillis());
                        requireActivity().runOnUiThread(() -> loadEvents());
                    });
                })
                .setNegativeButton("取消", null)
                .show();
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
