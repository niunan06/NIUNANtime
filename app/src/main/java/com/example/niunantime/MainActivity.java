package com.example.niunantime;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

import com.example.niunantime.databinding.ActivityMainBinding;
import com.example.niunantime.db.AppDatabase;
import com.example.niunantime.db.Todo;

import com.yalantis.ucrop.UCrop;

import java.util.concurrent.Executors;
import java.io.File;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private Fragment timerFragment;
    private Fragment todoFragment;
    private Fragment activeFragment;

    private DrawerLayout drawerLayout;

    private String pendingBgTarget = ""; // "timer" or "todo"

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && !pendingBgTarget.isEmpty()) {
                    // 持久化授权（部分图库不支持，忽略异常）
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {}
                    // 选完图片后进入裁剪
                    startCrop(uri);
                } else {
                    drawerLayout.closeDrawer(GravityCompat.END);
                }
            });

    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        prefs.edit().putString("bg_" + pendingBgTarget, resultUri.toString()).apply();
                        String target = pendingBgTarget;
                        pendingBgTarget = "";

                        if ("timer".equals(target) && activeFragment instanceof TimerFragment) {
                            ((TimerFragment) activeFragment).reloadBackground();
                        } else if ("todo".equals(target) && activeFragment instanceof TodoFragment) {
                            ((TodoFragment) activeFragment).reloadBackground();
                        }
                        updateBgLabel();
                    } else {
                        pendingBgTarget = "";
                    }
                } else {
                    pendingBgTarget = "";
                }
                drawerLayout.closeDrawer(GravityCompat.END);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        drawerLayout = binding.drawerLayout;

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        setSupportActionBar(binding.toolbar);

        // 初始化底部导航的两个Fragment
        FragmentManager fm = getSupportFragmentManager();

        // 先检查是否已有Fragment（Activity重建时FragmentManager会恢复）
        timerFragment = fm.findFragmentByTag("timer");
        todoFragment = fm.findFragmentByTag("todo");

        if (timerFragment == null) {
            timerFragment = new TimerFragment();
            fm.beginTransaction()
                    .add(R.id.fragment_container, timerFragment, "timer")
                    .commit();
        }
        if (todoFragment == null) {
            todoFragment = new TodoFragment();
            fm.beginTransaction()
                    .add(R.id.fragment_container, todoFragment, "todo")
                    .hide(todoFragment)
                    .commit();
        }

        // 确保timer可见、todo隐藏（重建后activeFragment丢失，默认显示timer）
        if (timerFragment != null && todoFragment != null) {
            fm.beginTransaction()
                    .show(timerFragment)
                    .hide(todoFragment)
                    .commit();
        }

        activeFragment = timerFragment;

        // 默认显示时间界面，显示FAB
        binding.fabAdd.show();

        // 设置底部导航栏点击切换
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_timer) {
                if (activeFragment == timerFragment) return true;
                fm.beginTransaction().hide(activeFragment).show(timerFragment).commit();
                activeFragment = timerFragment;
                binding.fabAdd.show();
                binding.todoButtons.setVisibility(View.GONE);
                return true;
            } else if (itemId == R.id.nav_todo) {
                if (activeFragment == todoFragment) return true;
                fm.beginTransaction().hide(activeFragment).show(todoFragment).commit();
                activeFragment = todoFragment;
                binding.fabAdd.hide();
                binding.todoButtons.setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });

        // FAB点击 —— 添加事件（有进行中计时时禁止）
        binding.fabAdd.setOnClickListener(v -> {
            if (TimerManager.getInstance().isActive()) {
                new AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("有一个事件正在进行中，请先完成后再添加新事件")
                        .setPositiveButton("确定", null)
                        .show();
            } else {
                showAddEventDialog();
            }
        });

        // 进行中按钮 —— 回到计时界面
        binding.btnOngoing.setOnClickListener(v -> {
            startActivity(TimerManager.getInstance().createResumeIntent(this));
        });

        // 添加待办按钮
        binding.btnAddTodo.setOnClickListener(v -> showAddTodoDialog());

        // 清空待办按钮
        binding.btnClearTodo.setOnClickListener(v -> {
            if (todoFragment instanceof TodoFragment) {
                ((TodoFragment) todoFragment).showClearAllDialog();
            }
        });

        // 设置侧栏 —— 选择背景图
        binding.optionBg.setOnClickListener(v -> pickBackgroundImage());

        // 设置侧栏 —— 主题颜色
        binding.optionThemeColor.setOnClickListener(v -> showThemeColorDialog());

        // 设置侧栏 —— 删除背景
        binding.optionDeleteBg.setOnClickListener(v -> showDeleteBgDialog());

        // 监听 Fragment 返回栈，ThemeColorFragment 弹出后恢复导航UI
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                restoreNavUI();
            }
        });

        // 更新已选背景显示
        updateBgLabel();

        // 检查是否是首次启动
        checkFirstLaunch();

        // 创建通知渠道
        createNotificationChannel();

        // 请求通知权限（Android 13+）
        requestNotificationPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_stats) {
            startActivity(new Intent(this, StatisticsActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            drawerLayout.openDrawer(GravityCompat.END);
            return true;
        } else if (id == R.id.action_view) {
            showDateListFragment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOngoingButton();
    }

    private void updateOngoingButton() {
        if (TimerManager.getInstance().isActive()) {
            binding.btnOngoing.setVisibility(View.VISIBLE);
            binding.btnOngoing.setText("进行中 · " + TimerManager.getInstance().getEventName());
        } else {
            binding.btnOngoing.setVisibility(View.GONE);
        }
    }

    private void updateBgLabel() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasTimerBg = prefs.getString("bg_timer", null) != null;
        boolean hasTodoBg = prefs.getString("bg_todo", null) != null;
        StringBuilder sb = new StringBuilder();
        if (hasTimerBg) sb.append("时间窗口已设置");
        if (hasTimerBg && hasTodoBg) sb.append(" | ");
        if (hasTodoBg) sb.append("待办窗口已设置");
        if (sb.length() == 0) sb.append("未设置背景图片");
        binding.tvSelectedBg.setText(sb.toString());
    }

    private void pickBackgroundImage() {
        new AlertDialog.Builder(this)
                .setTitle("选择放置窗口")
                .setMessage("将背景图片放在哪个窗口？")
                .setNeutralButton("时间窗口", (d, w) -> {
                    pendingBgTarget = "timer";
                    imagePickerLauncher.launch("image/*");
                })
                .setNegativeButton("待办窗口", (d, w) -> {
                    pendingBgTarget = "todo";
                    imagePickerLauncher.launch("image/*");
                })
                .setPositiveButton("取消", null)
                .show();
    }

    // ---- 导航栏 Fragment ----

    private void checkFirstLaunch() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("has_seen_welcome", false)) {
            binding.fullscreenContainer.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fullscreen_container, new WelcomeFragment())
                    .commit();
        }
    }

    public void onWelcomeDismissed() {
        binding.fullscreenContainer.setVisibility(View.GONE);
    }

    // ---- 添加事件 / 继续事件 ----

    private void showAddEventDialog() {
        showTimerDialog(-1, null, "添加事件");
    }

    public void showContinueEventDialog(int eventId, String eventName) {
        showTimerDialog(eventId, eventName, "继续进行此事件");
    }

    private void showTimerDialog(int existingEventId, String existingName, String title) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_event, null);
        TextInputEditText etEventName = dialogView.findViewById(R.id.et_event_name);
        TextInputEditText etDuration = dialogView.findViewById(R.id.et_duration);
        RadioGroup rgTimerType = dialogView.findViewById(R.id.rg_timer_type);
        View layoutDuration = dialogView.findViewById(R.id.layout_duration);

        if (existingName != null) {
            etEventName.setText(existingName);
        }

        rgTimerType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_countdown) {
                layoutDuration.setVisibility(View.VISIBLE);
            } else {
                layoutDuration.setVisibility(View.GONE);
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String eventName = etEventName.getText().toString().trim();
                if (eventName.isEmpty()) {
                    etEventName.setError("请输入事件名称");
                    return;
                }

                boolean isCountdown = rgTimerType.getCheckedRadioButtonId() == R.id.rb_countdown;
                int durationMinutes = 0;

                if (isCountdown) {
                    String durStr = etDuration.getText().toString().trim();
                    if (durStr.isEmpty()) {
                        return;
                    }
                    durationMinutes = Integer.parseInt(durStr);
                    if (durationMinutes <= 0) {
                        return;
                    }
                }

                dialog.dismiss();

                Intent intent = new Intent(this, TimerActivity.class);
                intent.putExtra(TimerActivity.EXTRA_EVENT_NAME, eventName);
                intent.putExtra(TimerActivity.EXTRA_TIMER_TYPE,
                        isCountdown ? "countdown" : "stopwatch");
                intent.putExtra(TimerActivity.EXTRA_DURATION_MINUTES, durationMinutes);
                if (existingEventId != -1) {
                    intent.putExtra(TimerActivity.EXTRA_EVENT_ID, existingEventId);
                }
                startActivity(intent);
            });
        });

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "确定", (d, w) -> {});
        dialog.show();
    }

    // ---- 添加待办 ----

    private void showAddTodoDialog() {
        EditText input = new EditText(this);
        input.setHint("请输入待办名称");
        input.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
                .setTitle("添加待办")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;

                    Todo todo = new Todo();
                    todo.name = name;
                    todo.completed = false;
                    todo.timestamp = System.currentTimeMillis();

                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getInstance(this).todoDao().insert(todo);
                        runOnUiThread(() -> {
                            if (todoFragment instanceof TodoFragment) {
                                ((TodoFragment) todoFragment).refreshList();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---- 主题切换 ----

    private void applyTheme() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String themeColor = prefs.getString("theme_color", "purple");
        switch (themeColor) {
            case "blue": setTheme(R.style.Theme_NIUNANtime_Blue); break;
            case "green": setTheme(R.style.Theme_NIUNANtime_Green); break;
            case "red": setTheme(R.style.Theme_NIUNANtime_Red); break;
            case "orange": setTheme(R.style.Theme_NIUNANtime_Orange); break;
            case "teal": setTheme(R.style.Theme_NIUNANtime_Teal); break;
            case "pink": setTheme(R.style.Theme_NIUNANtime_Pink); break;
            case "indigo": setTheme(R.style.Theme_NIUNANtime_Indigo); break;
            case "cyan": setTheme(R.style.Theme_NIUNANtime_Cyan); break;
            case "lime": setTheme(R.style.Theme_NIUNANtime_Lime); break;
            case "deep_orange": setTheme(R.style.Theme_NIUNANtime_DeepOrange); break;
            case "deep_purple": setTheme(R.style.Theme_NIUNANtime_DeepPurple); break;
            case "brown": setTheme(R.style.Theme_NIUNANtime_Brown); break;
            case "blue_grey": setTheme(R.style.Theme_NIUNANtime_BlueGrey); break;
            case "light_green": setTheme(R.style.Theme_NIUNANtime_LightGreen); break;
        }
    }

    private void restoreNavUI() {
        binding.bottomNavigation.setVisibility(View.VISIBLE);
        if (activeFragment == timerFragment) {
            binding.fabAdd.show();
        }
        binding.todoButtons.setVisibility(activeFragment == todoFragment ?
                View.VISIBLE : View.GONE);
    }

    private void showThemeColorDialog() {
        // 先关闭侧滑菜单
        drawerLayout.closeDrawer(GravityCompat.END);
        // 隐藏底部导航栏
        binding.bottomNavigation.setVisibility(View.GONE);
        // 隐藏FAB
        binding.fabAdd.hide();
        binding.todoButtons.setVisibility(View.GONE);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .add(R.id.fragment_container, new ThemeColorFragment())
                .addToBackStack("theme_color")
                .commit();
    }

    // ---- 裁剪图片（使用 uCrop 提供 QQ 式裁剪界面） ----

    private void startCrop(Uri sourceUri) {
        try {
            String filename = "bg_" + pendingBgTarget + ".jpg";
            File outputFile = new File(getFilesDir(), "bg/" + filename);
            File bgDir = outputFile.getParentFile();
            if (bgDir != null && !bgDir.exists()) bgDir.mkdirs();

            Uri destinationUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", outputFile);

            UCrop uCrop = UCrop.of(sourceUri, destinationUri);

            Intent cropIntent = uCrop.getIntent(this);
            cropLauncher.launch(cropIntent);
        } catch (Exception e) {
            // uCrop 不可用时回退到直接保存
            saveBgDirect(sourceUri);
        }
    }

    /** 直接保存原图（不裁剪时的回退方案） */
    private void saveBgDirect(Uri uri) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putString("bg_" + pendingBgTarget, uri.toString()).apply();
        pendingBgTarget = "";
        if (activeFragment instanceof TimerFragment) {
            ((TimerFragment) activeFragment).reloadBackground();
        } else if (activeFragment instanceof TodoFragment) {
            ((TodoFragment) activeFragment).reloadBackground();
        }
        updateBgLabel();
        drawerLayout.closeDrawer(GravityCompat.END);
    }

    // ---- 删除背景 ----

    private void showDeleteBgDialog() {
        new AlertDialog.Builder(this)
                .setTitle("删除背景图片")
                .setMessage("确定要删除已设置的背景图片吗？")
                .setPositiveButton("确定", (d, w) -> {
                    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                    prefs.edit()
                            .remove("bg_timer")
                            .remove("bg_todo")
                            .apply();
                    updateBgLabel();
                    if (activeFragment instanceof TimerFragment) {
                        ((TimerFragment) activeFragment).reloadBackground();
                    } else if (activeFragment instanceof TodoFragment) {
                        ((TodoFragment) activeFragment).reloadBackground();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---- 通知 ----

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "todo_reminder",
                    "待办提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("待办事项提醒通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    // ---- 提醒设置Fragment ----

    public void showDateListFragment() {
        binding.bottomNavigation.setVisibility(View.GONE);
        binding.fabAdd.hide();
        binding.todoButtons.setVisibility(View.GONE);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .add(R.id.fragment_container, new DateListFragment())
                .addToBackStack("date_list")
                .commit();
    }

    public void showDayDetailFragment(long dayStart, String dayLabel) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .add(R.id.fragment_container,
                        DayDetailFragment.newInstance(dayStart, dayLabel))
                .addToBackStack("day_detail")
                .commit();
    }

    public void showReminderFragment(int todoId, String todoName) {
        binding.bottomNavigation.setVisibility(View.GONE);
        binding.fabAdd.hide();
        binding.todoButtons.setVisibility(View.GONE);

        ReminderFragment fragment = ReminderFragment.newInstance(todoId, todoName);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .add(R.id.fragment_container, fragment)
                .addToBackStack("reminder")
                .commit();
    }
}
