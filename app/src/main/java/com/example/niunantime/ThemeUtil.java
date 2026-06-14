package com.example.niunantime;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class ThemeUtil {
    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String themeColor = prefs.getString("theme_color", "purple");
        switch (themeColor) {
            case "blue": activity.setTheme(R.style.Theme_NIUNANtime_Blue); break;
            case "green": activity.setTheme(R.style.Theme_NIUNANtime_Green); break;
            case "red": activity.setTheme(R.style.Theme_NIUNANtime_Red); break;
            case "orange": activity.setTheme(R.style.Theme_NIUNANtime_Orange); break;
            case "teal": activity.setTheme(R.style.Theme_NIUNANtime_Teal); break;
            case "pink": activity.setTheme(R.style.Theme_NIUNANtime_Pink); break;
            case "indigo": activity.setTheme(R.style.Theme_NIUNANtime_Indigo); break;
            case "cyan": activity.setTheme(R.style.Theme_NIUNANtime_Cyan); break;
            case "lime": activity.setTheme(R.style.Theme_NIUNANtime_Lime); break;
            case "deep_orange": activity.setTheme(R.style.Theme_NIUNANtime_DeepOrange); break;
            case "deep_purple": activity.setTheme(R.style.Theme_NIUNANtime_DeepPurple); break;
            case "brown": activity.setTheme(R.style.Theme_NIUNANtime_Brown); break;
            case "blue_grey": activity.setTheme(R.style.Theme_NIUNANtime_BlueGrey); break;
            case "light_green": activity.setTheme(R.style.Theme_NIUNANtime_LightGreen); break;
            case "white": activity.setTheme(R.style.Theme_NIUNANtime_White); break;
            case "black": activity.setTheme(R.style.Theme_NIUNANtime_Black); break;
        }
    }
}
