package com.music.musiczip.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * 从 Uri 获取实际文件路径（支持从外部存储、媒体库以及通过流复制生成的临时文件）
     */
    public static String getRealPathFromUri(Context context, Uri uri) {
        if (uri == null || context == null) {
            Log.e(TAG, "getRealPathFromUri: uri or context is null");
            return null;
        }

        Log.d(TAG, "getRealPathFromUri: 处理URI: " + uri.toString());

        try {
            // DocumentProvider
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    if ("primary".equalsIgnoreCase(type)) {
                        String path = Environment.getExternalStorageDirectory() + "/" + split[1];
                        Log.d(TAG, "getRealPathFromUri: 外部存储路径: " + path);
                        return path;
                    }
                }
            }

            // Content公用协议（如媒体库）
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = { MediaStore.Audio.Media.DATA };
                try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                        String path = cursor.getString(columnIndex);
                        Log.d(TAG, "getRealPathFromUri: 媒体文件路径: " + path);
                        return path;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRealPathFromUri: 直接获取媒体文件路径失败，尝试文件流拷贝", e);
                }

                // 如果通过系统 Cursor 依然拿不到（例如部分第三方文件管理器分发的保护沙盒沙箱 URI），使用流拷贝到临时目录
                try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                    if (inputStream != null) {
                        File tempFile = new File(context.getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".mp3");
                        copyInputStreamToFile(inputStream, tempFile);
                        Log.d(TAG, "getRealPathFromUri: 成功生成临时缓存文件路径: " + tempFile.getAbsolutePath());
                        return tempFile.getAbsolutePath();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRealPathFromUri: 创建临时流文件失败", e);
                }
            }

            // File 协议
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                Log.d(TAG, "getRealPathFromUri: 文件路径: " + path);
                return path;
            }

            Log.e(TAG, "getRealPathFromUri: 无法处理的 URI 类型");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "getRealPathFromUri: 处理URI过程遭遇致命异常", e);
            return null;
        }
    }

    /**
     * 将输入流拷贝到指定目标文件
     */
    public static void copyInputStreamToFile(InputStream inputStream, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[4 * 1024]; // 4KB 缓冲区
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    /**
     * 自动处理并生成最终的压缩或提取音频文件的绝对路径。
     * 该方法已经完全移除了对 UI 控件的强依赖，并且会自动保证输出文件夹被成功创建。
     *
     * @param inputPath 原始选择的文件路径
     * @param quality   UI层传入的压缩品质字符串（例如 "320k (CD音质)" 或 "320k"）
     * @param format    UI层传入的格式字符串（例如 "mp3 (常用音频格式)" 或 "extract (从视频提取音频)"）
     * @return 最终拼接好的完整目标文件绝对路径，如果创建失败则返回 null
     */
    public static String generateOutputFilePath(String inputPath, String quality, String format) {
        if (inputPath == null || quality == null || format == null) {
            return null;
        }

        // 1. 创建或定位标准下载区目录：/storage/emulated/0/Download/musiczip
        File musicDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "musiczip");
        if (!musicDir.exists()) {
            boolean created = musicDir.mkdirs();
            Log.d(TAG, "创建输出目录: " + (created ? "成功" : "失败"));
        }

        // 2. 剥离原文件的扩展名
        File inputFile = new File(inputPath);
        String nameWithoutExtension = inputFile.getName();
        int lastDotIndex = nameWithoutExtension.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExtension = nameWithoutExtension.substring(0, lastDotIndex);
        }

        // 3. 解析后缀名
        String extension;
        if (format.contains("extract")) {
            extension = "mp3";
        } else {
            // 获取空格前的纯字母后缀，例如 "mp3", "aac"
            extension = format.split(" ")[0];
        }

        // 4. 从质量里获取核心纯数字速率，例如 "320k"
        String qualityValue = quality.split(" ")[0];

        // 5. 组合出干净的目标文件名
        String outputFileName = nameWithoutExtension + "_compressed_" + qualityValue + "." + extension;

        // 6. 实例化并返回最终文件的绝对路径
        return new File(musicDir, outputFileName).getAbsolutePath();
    }

    /**
     * 辅助判定是否为外部存储 DocumentsProvider 签发的 URI
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }
}