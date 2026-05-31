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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Calendar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.niunantime.databinding.FragmentTodoBinding;
import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.Todo;

import java.util.List;
import java.util.concurrent.Executors;

public class TodoFragment extends Fragment {

    private FragmentTodoBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTodoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTodos();
        loadBackground();
    }

    public void refreshList() {
        loadTodos();
    }

    public void reloadBackground() {
        loadBackground();
    }

    private void loadBackground() {
        try {
            SharedPreferences prefs = requireActivity()
                    .getSharedPreferences("app_prefs", getContext().MODE_PRIVATE);
            String uriStr = prefs.getString("bg_todo", null);
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

    private long getTodayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void loadTodos() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            long todayStart = getTodayStart();
            List<Todo> all = db.todoDao().getAllTodos();

            // 筛选：未完成的 或 今天完成的
            java.util.ArrayList<Todo> filtered = new java.util.ArrayList<>();
            for (Todo t : all) {
                if (!t.completed || (t.completedTime != null && t.completedTime >= todayStart)) {
                    filtered.add(t);
                }
            }

            requireActivity().runOnUiThread(() -> displayTodos(filtered));
        }).start();
    }

    private void displayTodos(java.util.ArrayList<Todo> todos) {
        binding.todoListContainer.removeAllViews();

        if (todos.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        binding.tvEmpty.setVisibility(View.GONE);

        Calendar nowCal = Calendar.getInstance();
        int todayDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR);
        int todayYear = nowCal.get(Calendar.YEAR);

        // 按时间降序排列
        java.util.Collections.sort(todos, (a, b) -> Long.compare(b.timestamp, a.timestamp));

        for (Todo todo : todos) {
            String tag = null;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(todo.timestamp);
            if (c.get(Calendar.YEAR) != todayYear || c.get(Calendar.DAY_OF_YEAR) != todayDayOfYear) {
                int daysAgo = todayDayOfYear - c.get(Calendar.DAY_OF_YEAR);
                if (todayYear != c.get(Calendar.YEAR)) {
                    daysAgo += (todayYear - c.get(Calendar.YEAR)) * 365;
                }
                tag = daysAgo <= 1 ? "昨日待办" : daysAgo + "日前待办";
            }
            addTodoItem(todo, tag);
        }
    }

    private void addTodoItem(Todo todo, String tag) {
        View itemView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_todo, binding.todoListContainer, false);

        View contentLayout = itemView.findViewById(R.id.layout_todo_content);
        TextView tvName = itemView.findViewById(R.id.tv_todo_name);
        TextView tvTag = itemView.findViewById(R.id.tv_todo_tag);
        CheckBox cbCompleted = itemView.findViewById(R.id.cb_completed);
        Button btnReminder = itemView.findViewById(R.id.btn_set_reminder);

        tvName.setText(todo.name);
        cbCompleted.setChecked(todo.completed);

        if (tag != null && !tag.isEmpty()) {
            tvTag.setText(tag);
            tvTag.setVisibility(View.VISIBLE);
        } else {
            tvTag.setVisibility(View.GONE);
        }

        if (todo.completed) {
            tvName.setTextColor(0xFF999999);
            tvName.setPaintFlags(tvName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tvName.setTextColor(0xFF333333);
            tvName.setPaintFlags(tvName.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }

        cbCompleted.setOnCheckedChangeListener(null);
        cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            todo.completed = isChecked;
            todo.completedTime = isChecked ? System.currentTimeMillis() : null;
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase.getInstance(requireContext()).todoDao().update(todo);
                requireActivity().runOnUiThread(() -> loadTodos());
            });
        });

        // 点击"设置提醒"按钮进入提醒设置
        btnReminder.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showReminderFragment(todo.id, todo.name);
            }
        });

        itemView.setOnLongClickListener(v -> {
            showDeleteDialog(todo);
            return true;
        });

        binding.todoListContainer.addView(itemView);
    }

    private void showDeleteDialog(Todo todo) {
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
    }

    public void showClearAllDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("清空所有待办")
                .setMessage("确定要删除所有待办事项吗？此操作不可恢复。")
                .setPositiveButton("确定", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getInstance(requireContext()).todoDao().deleteAll();
                        requireActivity().runOnUiThread(() -> loadTodos());
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
