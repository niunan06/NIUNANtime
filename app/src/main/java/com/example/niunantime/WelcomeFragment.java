package com.example.niunantime;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.niunantime.databinding.FragmentWelcomeBinding;

public class WelcomeFragment extends Fragment {

    private FragmentWelcomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnWelcome.setOnClickListener(v -> {
            // 标记已看过欢迎页
            SharedPreferences prefs = requireActivity()
                    .getSharedPreferences("app_prefs", getContext().MODE_PRIVATE);
            prefs.edit().putBoolean("has_seen_welcome", true).apply();

            // 关闭欢迎页
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .remove(this)
                    .commit();

            // 恢复底部导航栏的正常界面
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onWelcomeDismissed();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
