package com.music.musiczip;

import android.app.Application;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        // 设置默认的夜间模式为跟随系统
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setupExceptionHandler();
    }

    private void setupExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "未捕获的异常: ", throwable);
        });
    }
} 