package com.music.musiczip.helper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class PermissionHelper {
    private final Activity activity;
    public static final int PERMISSION_REQUEST_CODE = 1000;
    public static final int MANAGE_STORAGE_REQUEST_CODE = 2000;

    // 定义一个权限结果回调接口
    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }

    public PermissionHelper(Activity activity) {
        this.activity = activity;
    }

    /**
     * 检查并请求权限
     * @param callback 权限请求结果的回调
     * @return 如果已经拥有权限返回 true，否则返回 false 并开始请求权限
     */
    public boolean checkAndRequestPermissions(PermissionCallback callback) {
        // Android 11 (API 30) 及以上版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                if (callback != null) callback.onGranted();
                return true;
            } else {
                // 显示权限说明对话框
                new AlertDialog.Builder(activity)
                        .setTitle("需要权限")
                        .setMessage("本应用需要访问所有文件的权限来处理音频文件。请在接下来的系统设置中授予权限。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                activity.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                            }
                        })
                        .setNegativeButton("取消", (dialog, which) -> {
                            Toast.makeText(activity, "没有权限将无法使用应用", Toast.LENGTH_LONG).show();
                            if (callback != null) callback.onDenied();
                        })
                        .setCancelable(false)
                        .show();
                return false;
            }
        }
        // Android 10 及以下版本
        else {
            String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            ArrayList<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                activity.requestPermissions(permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                return false;
            } else {
                if (callback != null) callback.onGranted();
                return true;
            }
        }
    }

    /**
     * 在 Activity 的 onRequestPermissionsResult 中调用此方法
     */
    public void handleRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, PermissionCallback callback) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                if (callback != null) callback.onGranted();
            } else {
                Toast.makeText(activity, "没有权限将无法使用应用", Toast.LENGTH_LONG).show();
                if (callback != null) callback.onDenied();
            }
        }
    }

    /**
     * 在 Activity 的 onActivityResult 中调用此方法（用于处理 Android 11+ 从系统设置返回的情况）
     */
    public void handleActivityResult(int requestCode, int resultCode, PermissionCallback callback) {
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    if (callback != null) callback.onGranted();
                } else {
                    Toast.makeText(activity, "没有权限将无法使用应用", Toast.LENGTH_LONG).show();
                    if (callback != null) callback.onDenied();
                }
            }
        }
    }
}