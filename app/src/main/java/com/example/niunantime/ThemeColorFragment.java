package com.example.niunantime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.niunantime.databinding.FragmentThemeColorBinding;

public class ThemeColorFragment extends Fragment {

    private FragmentThemeColorBinding binding;

    private final String[] colorKeys = {
            "purple", "blue", "green", "red", "orange",
            "teal", "pink", "indigo", "cyan", "lime",
            "deep_orange", "deep_purple", "brown", "blue_grey", "light_green",
            "white", "black"
    };

    private final int[] colorValues = {
            R.color.theme_purple, R.color.theme_blue,
            R.color.theme_green, R.color.theme_red,
            R.color.theme_orange, R.color.theme_teal,
            R.color.theme_pink, R.color.theme_indigo,
            R.color.theme_cyan, R.color.theme_lime,
            R.color.theme_deep_orange, R.color.theme_deep_purple,
            R.color.theme_brown, R.color.theme_blue_grey,
            R.color.theme_light_green,R.color.white,
            R.color.black
    };

    private final String[] colorLabels = {
            "紫色", "蓝色", "绿色", "红色", "橙色",
            "青色", "粉色", "靛蓝", "蓝绿", "黄绿",
            "深橙", "深紫", "棕色", "蓝灰", "浅绿",
            "纯白", "纯黑"
    };

    private String selectedKey;
    private View[] circleViews;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentThemeColorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        selectedKey = prefs.getString("theme_color", "purple");

        // 工具栏返回按钮
        binding.toolbar.setNavigationOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        // 系统返回键
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        getParentFragmentManager().popBackStack();
                    }
                }
        );

        buildColorGrid();

        // 确定按钮：保存主题并重建Activity
        binding.btnConfirm.setOnClickListener(v -> {
            prefs.edit().putString("theme_color", selectedKey).apply();
            getParentFragmentManager().popBackStack();
            getParentFragmentManager().executePendingTransactions();
            requireActivity().recreate();
        });
    }

    private void buildColorGrid() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        int screenWidth = dm.widthPixels;
        int paddingPx = (int) (16 * density) * 2;
        int gapPx = (int) (8 * density);
        int cols = 3;
        int itemWidthPx = (screenWidth - paddingPx - gapPx * (cols - 1)) / cols;
        int circleSizePx = (int) (itemWidthPx * 0.7f);
        int strokePx = (int) (3 * density);

        circleViews = new View[colorKeys.length];

        for (int i = 0; i < colorKeys.length; i++) {
            int color = getResources().getColor(colorValues[i], null);

            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            GridLayout.LayoutParams itemLp = new GridLayout.LayoutParams();
            itemLp.width = itemWidthPx;
            itemLp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            item.setLayoutParams(itemLp);

            // 颜色圆圈
            View circle = new View(requireContext());
            circleViews[i] = circle;
            int padPx = (int) (8 * density);
            LinearLayout.LayoutParams circleLp =
                    new LinearLayout.LayoutParams(circleSizePx, circleSizePx);
            circleLp.setMargins(padPx, (int) (12 * density), padPx, (int) (4 * density));
            circle.setLayoutParams(circleLp);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            if (colorKeys[i].equals(selectedKey)) {
                drawable.setStroke(strokePx, 0xFF333333);
            }
            circle.setBackground(drawable);

            // 颜色名称
            TextView label = new TextView(requireContext());
            label.setText(colorLabels[i]);
            label.setTextSize(13);
            label.setTextColor(0xFF666666);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, 0, 0, (int) (12 * density));

            item.addView(circle);
            item.addView(label);

            final int index = i;
            item.setOnClickListener(v -> selectColor(index));

            binding.colorGrid.addView(item);
        }
    }

    private void selectColor(int index) {
        selectedKey = colorKeys[index];
        float density = getResources().getDisplayMetrics().density;
        int strokePx = (int) (3 * density);

        for (int i = 0; i < circleViews.length; i++) {
            int color = getResources().getColor(colorValues[i], null);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            if (i == index) {
                d.setStroke(strokePx, 0xFF333333);
            }
            circleViews[i].setBackground(d);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
