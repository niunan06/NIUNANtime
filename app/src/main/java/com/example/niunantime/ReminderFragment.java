package com.example.niunantime;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;

import com.google.android.material.appbar.MaterialToolbar;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.Todo;
import com.example.niunantime.db.TodoDao;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

public class ReminderFragment extends Fragment {

    private static final String ARG_TODO_ID = "todo_id";
    private static final String ARG_TODO_NAME = "todo_name";

    private int todoId;
    private String todoName;

    private RadioGroup rgStatus;
    private RadioButton rbRemind;
    private RadioButton rbDnd;
    private LinearLayout layoutReminderOptions;
    private RadioGroup rgType;
    private RadioButton rbTimed;
    private RadioButton rbRandom;
    private LinearLayout layoutTimed;
    private TextView tvDate;
    private TextView tvTime;
    private TextView tvRandomInfo;
    private Button btnSave;
    private View rowDate;
    private View rowTime;

    private int selectedYear;
    private int selectedMonth;
    private int selectedDay;
    private int selectedHour;
    private int selectedMinute;

    public static ReminderFragment newInstance(int todoId, String todoName) {
        ReminderFragment fragment = new ReminderFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TODO_ID, todoId);
        args.putString(ARG_TODO_NAME, todoName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            todoId = getArguments().getInt(ARG_TODO_ID, 0);
            todoName = getArguments().getString(ARG_TODO_NAME, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reminder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rgStatus = view.findViewById(R.id.rg_status);
        rbRemind = view.findViewById(R.id.rb_remind);
        rbDnd = view.findViewById(R.id.rb_dnd);
        layoutReminderOptions = view.findViewById(R.id.layout_reminder_options);
        rgType = view.findViewById(R.id.rg_type);
        rbTimed = view.findViewById(R.id.rb_timed);
        rbRandom = view.findViewById(R.id.rb_random);
        layoutTimed = view.findViewById(R.id.layout_timed);
        tvDate = view.findViewById(R.id.tv_date);
        tvTime = view.findViewById(R.id.tv_time);
        tvRandomInfo = view.findViewById(R.id.tv_random_info);
        btnSave = view.findViewById(R.id.btn_save);
        rowDate = view.findViewById(R.id.row_date);
        rowTime = view.findViewById(R.id.row_time);

        // Initialize with current date/time
        Calendar now = Calendar.getInstance();
        selectedYear = now.get(Calendar.YEAR);
        selectedMonth = now.get(Calendar.MONTH);
        selectedDay = now.get(Calendar.DAY_OF_MONTH);
        selectedHour = now.get(Calendar.HOUR_OF_DAY);
        selectedMinute = now.get(Calendar.MINUTE);

        updateDateDisplay();
        updateTimeDisplay();

        // Load existing reminder settings if any
        loadExistingSettings();

        // Toolbar back
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        // 提醒/勿扰 toggle
        rgStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_remind) {
                layoutReminderOptions.setVisibility(View.VISIBLE);
            } else {
                layoutReminderOptions.setVisibility(View.GONE);
            }
        });

        // 定时/不定时 toggle
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_timed) {
                layoutTimed.setVisibility(View.VISIBLE);
                tvRandomInfo.setVisibility(View.GONE);
            } else {
                layoutTimed.setVisibility(View.GONE);
                tvRandomInfo.setVisibility(View.VISIBLE);
            }
        });

        // Date picker
        rowDate.setOnClickListener(v -> showDatePicker());
        tvDate.setOnClickListener(v -> showDatePicker());

        // Time picker
        rowTime.setOnClickListener(v -> showTimePicker());
        tvTime.setOnClickListener(v -> showTimePicker());

        // Save
        btnSave.setOnClickListener(v -> saveReminder());
    }

    private void loadExistingSettings() {
        Executors.newSingleThreadExecutor().execute(() -> {
            TodoDao dao = AppDatabase.getInstance(requireContext()).todoDao();
            Todo todo = dao.getById(todoId);
            if (todo == null) return;

            Long existingReminderTime = todo.reminderTime;
            requireActivity().runOnUiThread(() -> {
                if (existingReminderTime != null && existingReminderTime > 0 && !todo.reminded) {
                    // 已有提醒设置，恢复日期和时间
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(existingReminderTime);
                    selectedYear = c.get(Calendar.YEAR);
                    selectedMonth = c.get(Calendar.MONTH);
                    selectedDay = c.get(Calendar.DAY_OF_MONTH);
                    selectedHour = c.get(Calendar.HOUR_OF_DAY);
                    selectedMinute = c.get(Calendar.MINUTE);
                    updateDateDisplay();
                    updateTimeDisplay();

                    rbRemind.setChecked(true);
                    rbTimed.setChecked(true);
                } else {
                    // 未设置提醒 或 已被设为勿扰 → 默认勿扰
                    rbDnd.setChecked(true);
                    layoutReminderOptions.setVisibility(View.GONE);
                }
            });
        });
    }

    private void showDatePicker() {
        DatePickerDialog picker = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedYear = year;
                    selectedMonth = month;
                    selectedDay = dayOfMonth;
                    updateDateDisplay();
                },
                selectedYear, selectedMonth, selectedDay);
        picker.show();
    }

    private void showTimePicker() {
        TimePickerDialog picker = new TimePickerDialog(requireContext(),
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    updateTimeDisplay();
                },
                selectedHour, selectedMinute, true);
        picker.show();
    }

    private void updateDateDisplay() {
        tvDate.setText(String.format(Locale.CHINA, "%d月%d日", selectedMonth + 1, selectedDay));
    }

    private void updateTimeDisplay() {
        tvTime.setText(String.format(Locale.CHINA, "%d时%d分", selectedHour, selectedMinute));
    }

    private void saveReminder() {
        boolean isRemind = rbRemind.isChecked();
        long reminderTime;

        if (isRemind) {
            if (rbTimed.isChecked()) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.YEAR, selectedYear);
                c.set(Calendar.MONTH, selectedMonth);
                c.set(Calendar.DAY_OF_MONTH, selectedDay);
                c.set(Calendar.HOUR_OF_DAY, selectedHour);
                c.set(Calendar.MINUTE, selectedMinute);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                reminderTime = c.getTimeInMillis();

                // Warn if the time is in the past
                if (reminderTime <= System.currentTimeMillis()) {
                    Toast.makeText(requireContext(), "提醒时间必须晚于当前时间", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // 不定时提醒: random 4-6 hours from now
                Random r = new Random();
                int delayHours = 4 + r.nextInt(3); // 4, 5, or 6
                reminderTime = System.currentTimeMillis() + (long) delayHours * 3600 * 1000L;
            }
        } else {
            // 勿扰: reminderTime = 0 means disabled
            reminderTime = 0;
        }

        btnSave.setEnabled(false);

        Executors.newSingleThreadExecutor().execute(() -> {
            TodoDao dao = AppDatabase.getInstance(requireContext()).todoDao();
            Todo todo = dao.getById(todoId);
            if (todo == null) {
                requireActivity().runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(requireContext(), "待办不存在", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            todo.reminderTime = reminderTime;
            todo.reminded = false;
            dao.update(todo);

            // Schedule or cancel alarm
            if (reminderTime > 0) {
                scheduleAlarm(todoId, todoName, reminderTime);
            } else {
                cancelAlarm(todoId);
            }

            requireActivity().runOnUiThread(() -> {
                btnSave.setEnabled(true);
                Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show();
                getParentFragmentManager().popBackStack();
            });
        });
    }

    private void scheduleAlarm(int todoId, String todoName, long triggerTime) {
        Context context = requireContext().getApplicationContext();
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("todo_id", todoId);
        intent.putExtra("todo_name", todoName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, todoId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    private void cancelAlarm(int todoId) {
        Context context = requireContext().getApplicationContext();
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, todoId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
