package com.music.musiczip;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import android.Manifest;
import androidx.annotation.NonNull;

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {
    private static final int PICK_AUDIO_REQUEST = 1;
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1000;

    private TextView inputPathText;
    private Spinner qualitySpinner;
    private Spinner formatSpinner;
    private Spinner sampleRateSpinner;
    private Spinner channelsSpinner;
    private Spinner volumeSpinner;
    private TextView statusText;
    private Uri selectedFileUri;
    private Button selectFileButton;
    private Button compressButton;
    private Button openOutputFolderButton;
    private Button gotoTrimButton;
    private String inputFilePath;
    private TextView speed_value_text;
    private SeekBar  speed_seekbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: 开始");
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            // 初始化视图
            inputPathText = findViewById(R.id.input_path_text);
            selectFileButton = findViewById(R.id.select_file_button);
            qualitySpinner = findViewById(R.id.quality_spinner);
            formatSpinner = findViewById(R.id.format_spinner);
            sampleRateSpinner = findViewById(R.id.sample_rate_spinner);
            channelsSpinner = findViewById(R.id.channels_spinner);
            volumeSpinner = findViewById(R.id.volume_spinner);
            compressButton = findViewById(R.id.compress_button);
            statusText = findViewById(R.id.status_text);
            openOutputFolderButton = findViewById(R.id.open_output_folder_button);
            gotoTrimButton = findViewById(R.id.goto_trim_button);
            gotoTrimButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, AudioTrimActivity.class);
                startActivity(intent);
            });
            speed_value_text = findViewById(R.id.speed_value_text);
            speed_seekbar = findViewById(R.id.speed_seekbar);

            speed_seekbar.setMax(400);
            speed_seekbar.setProgress(100);

            speed_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float speed = (float) progress / 100f;
                    speed_value_text.setText(String.format(Locale.getDefault(), "%.2f", speed));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });


            // 检查权限
            if (!checkPermissions()) {
                statusText.setText("请先授予存储权限");
                selectFileButton.setEnabled(false);
                compressButton.setEnabled(false);
                return;
            }

            setupUI();
        } catch (Exception e) {
            Log.e(TAG, "onCreate 发生错误: ", e);
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 显示权限说明对话框
                new AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("本应用需要访问所有文件的权限来处理音频文件。请在接下来的系统设置中授予权限。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 2000);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(intent, 2000);
                        }
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        Toast.makeText(this, "没有权限将无法使用应用", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
                return false;
            }
        } else {
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            ArrayList<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                requestPermissions(permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                setupUI();
                selectFileButton.setEnabled(true);
                compressButton.setEnabled(true);
            } else {
                Toast.makeText(this, "没有权限将无法使用应用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Log.d(TAG, "onActivityResult: 文件URI: " + uri.toString());
                inputFilePath = getRealPathFromUri(uri);
                if (inputFilePath != null) {
                    inputPathText.setText(inputFilePath);
                    // 根据文件类型自动选择合适的处理方式
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType != null && mimeType.startsWith("video/")) {
                        // 如果是视频文件，自动选择extract选项
                        int extractPosition = -1;
                        for (int i = 0; i < formatSpinner.getCount(); i++) {
                            if (formatSpinner.getItemAtPosition(i).toString().contains("extract")) {
                                extractPosition = i;
                                break;
                            }
                        }
                        if (extractPosition != -1) {
                            formatSpinner.setSelection(extractPosition);
                        }
                    }
                } else {
                    Toast.makeText(this, "无法获取文件路径", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == 2000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    setupUI();
                    selectFileButton.setEnabled(true);
                    compressButton.setEnabled(true);
                } else {
                    Toast.makeText(this, "没有权限将无法使用应用", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    private void setupUI() {
        Log.d(TAG, "setupUI: 开始设置UI组件");
        // 设置选择文件按钮点击事件
        selectFileButton.setOnClickListener(v -> selectMediaFile());

        // 设置压缩按钮点击事件
        compressButton.setOnClickListener(v -> compressAudio());

        // 设置各个Spinner的适配器
        setupSpinners();

        openOutputFolderButton.setOnClickListener(v -> openOutputFolder());

        Log.d(TAG, "setupUI: UI组件设置完成");
    }

    private void setupSpinners() {
        Log.d(TAG, "setupSpinners: 开始设置所有下拉选项");

        // 设置质量选项
        String[] qualityOptions = {
            "960k (超高音质)", "320k (CD音质)", "256k (高品质)",
            "192k (中等品质)", "128k (标准品质)", "96k (低品质)",
            "64k (超低品质)", "32k (极低品质)", "16k (最低品质)"
        };
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, qualityOptions
        );
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        qualitySpinner.setSelection(1); // 默认选择 320k (CD音质)

        // 设置声道选项
        String[] channelsOptions = {
            "立体声",
            "单声道"
        };
        ArrayAdapter<String> channelsAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, channelsOptions
        );
        channelsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        channelsSpinner.setAdapter(channelsAdapter);
        channelsSpinner.setSelection(0); // 默认选择立体声

        // 设置格式选项
        String[] formatOptions = {
            "mp3 (常用音频格式)",
            "aac (苹果设备优选)",
            "m4a (苹果设备优选)",
            "ogg (开源格式)",
            "wav (无损格式)",
            "extract (从视频提取音频)"  // 添加提取音频选项
        };
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, formatOptions
        );
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(formatAdapter);
        formatSpinner.setSelection(0); // 默认选择 mp3

        // 设置采样率选项 (修改后的有效采样率)
        String[] sampleRateOptions = {
            "48000 Hz (高音质)",
            "44100 Hz (CD质量)",
            "32000 Hz (高质量)",
            "22050 Hz (中等质量)",
            "16000 Hz (语音质量)",
            "11025 Hz (低质量)",
            "8000 Hz (电话质量)"
        };
        ArrayAdapter<String> sampleRateAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, sampleRateOptions
        );
        sampleRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleRateSpinner.setAdapter(sampleRateAdapter);
        sampleRateSpinner.setSelection(1); // 默认选择 44100 Hz (CD质量)

        // 设置音量调整选项
        String[] volumeOptions = {
            "+50 dB (超超级增强)",
            "+30 dB (超级增强)",
            "+20 dB (极限增强)",
            "+15 dB (大幅增强)",
            "+10 dB (中度增强)",
            "+5 dB (轻微增强)",
            "0 dB (保持原样)",
            "-5 dB (轻微降低)",
            "-10 dB (中度降低)",
            "-15 dB (大幅降低)",
            "-20 dB (极限降低)"
        };
        ArrayAdapter<String> volumeAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, volumeOptions
        );
        volumeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        volumeSpinner.setAdapter(volumeAdapter);
        volumeSpinner.setSelection(6); // 默认选择 0 dB (保持原样)

        setupSpinnerListeners();
        Log.d(TAG, "setupSpinners: 所有下拉选项设置完成");
    }

    private void setupSpinnerListeners() {
        // 质量选项设置
        Log.d(TAG, "setupSpinners: 设置质量选项");
        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "质量选项被改变: " + selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "质量选项: 未选择任何选项");
            }
        });

        // 格式选项设置
        Log.d(TAG, "setupSpinners: 设置格式选项");
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "格式选项被改变: " + selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "格式选项: 未选择任何选项");
            }
        });

        // 采样率选项设置
        Log.d(TAG, "setupSpinners: 设置采样率选项");
        sampleRateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "采样率选项被改变: " + selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "采样率选项: 未选择任何选项");
            }
        });

        // 声道选项设置
        Log.d(TAG, "setupSpinners: 设置声道选项");
        channelsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "声道选项被改变: " + selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "声道选项: 未选择任何选项");
            }
        });

        // 音量选项设置
        Log.d(TAG, "setupSpinners: 设置音量选项");
        volumeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "音量选项被改变: " + selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "音量选项: 未选择任何选项");
            }
        });
    }

    private void selectMediaFile() {
        Log.d(TAG, "selectMediaFile: 开始选择音频或视频文件");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        String[] mimeTypes = {"audio/*", "video/*"};  // 支持音频和视频
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择音频或视频文件"), PICK_AUDIO_REQUEST);
    }

    private boolean isValidSampleRate(int sampleRate) {
        // MP3编码支持的采样率范围
        int[] validRates = {8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};
        for (int rate : validRates) {
            if (sampleRate == rate) {
                return true;
            }
        }
        return false;
    }

    private void compressAudio() {
        if (inputFilePath == null) {
            Log.w(TAG, "compressAudio: 未选择文件");
            Toast.makeText(this, "请先选择音频文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "compressAudio: 开始压缩音频");

        try {
            // 获取原始文件路径
            String inputPath = inputFilePath;
            Log.d(TAG, "原始文件路径: " + inputPath);

            // 创建输出目录
            File musicDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "musiczip");
            if (!musicDir.exists()) {
                boolean created = musicDir.mkdirs();
                Log.d(TAG, "创建输出目录: " + (created ? "成功" : "失败"));
            }

            // 获取压缩参数
            String quality = qualitySpinner.getSelectedItem().toString().split(" ")[0];
            String format = formatSpinner.getSelectedItem().toString();
            int sampleRate = getSelectedSampleRate();
            int channels = channelsSpinner.getSelectedItem().toString().equals("单声道") ? 1 : 2;
            String volume = getSelectedVolume();

            Log.d(TAG, "压缩参数: " +
                "\n质量: " + quality +
                "\n格式: " + format +
                "\n采样率: " + sampleRate +
                "\n声道: " + channels +
                "\n音量: " + volume + "dB"
            );

            // 开始压缩任务
            String outputFileName = getOutputFileName(inputPath, quality);
            File outputFile = new File(musicDir, outputFileName);
            String outputPath = outputFile.getAbsolutePath();
            Log.d(TAG, "输出文件路径: " + outputPath);

            // 更新状态文本
            statusText.setText("输出文件将保存到：\n" + outputPath);

            new AudioCompressionTask(this).execute(
                inputPath,
                outputPath,
                quality,
                format,
                String.valueOf(sampleRate),
                String.valueOf(channels),
                volume
            );

        } catch (Exception e) {
            Log.e(TAG, "压缩过程出错: ", e);
            Toast.makeText(this, "压缩失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 从Uri获取实际文件路径
    private String getRealPathFromUri(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "getRealPathFromUri: uri is null");
            return null;
        }

        Log.d(TAG, "getRealPathFromUri: 处理URI: " + uri.toString());

        try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
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

            // 如果是媒体文件
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = { MediaStore.Audio.Media.DATA };
                try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                        String path = cursor.getString(column_index);
                        Log.d(TAG, "getRealPathFromUri: 媒体文件路径: " + path);
                        return path;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRealPathFromUri: 获取媒体文件路径失败", e);
                }

                // 如果上面的方法失败，尝试使用 ContentResolver 获取输入流
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        // 创建临时文件
                        File tempFile = new File(getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".mp3");
                        copyInputStreamToFile(inputStream, tempFile);
                        inputStream.close();
                        Log.d(TAG, "getRealPathFromUri: 临时文件路径: " + tempFile.getAbsolutePath());
                        return tempFile.getAbsolutePath();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRealPathFromUri: 创建临时文件失败", e);
                }
            }

            // 如果是文件 URI
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                Log.d(TAG, "getRealPathFromUri: 文件路径: " + path);
                return path;
            }

            Log.e(TAG, "getRealPathFromUri: 无法处理的 URI 类型");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "getRealPathFromUri: 处理URI失败", e);
            return null;
        }
    }

    private void copyInputStreamToFile(InputStream inputStream, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[4 * 1024]; // 4KB buffer
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private String getOutputFileName(String inputPath, String quality) {
        File inputFile = new File(inputPath);
        String nameWithoutExtension = inputFile.getName();
        int lastDotIndex = nameWithoutExtension.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExtension = nameWithoutExtension.substring(0, lastDotIndex);
        }

        String selectedFormat = getSelectedFormat();
        String extension;
        
        // 如果是从视频提取音频，则使用mp3格式
        if (selectedFormat.contains("extract")) {
            extension = "mp3";
        } else {
            // 获取格式名称，去掉括号里的说明
            extension = selectedFormat.split(" ")[0];
        }
        
        // 从quality中提取数值部分
        String qualityValue = quality.split(" ")[0];
        
        return nameWithoutExtension + "_compressed_" + qualityValue + "." + extension;
    }

    private String getSelectedFormat() {
        return formatSpinner.getSelectedItem().toString();
    }

    // 异步压缩任务
    private class AudioCompressionTask extends AsyncTask<String, Void, Boolean> {
        private WeakReference<MainActivity> activityReference;
        private Exception compressionException;

        AudioCompressionTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String inputPath = params[0];
            String outputPath = params[1];
            String quality = params[2];
            String format = params[3];
            String sampleRate = params[4];
            String channels = params[5];
            String volume = params[6];

            try {
                Log.d(TAG, "执行压缩中...");

                // 获取用户设置的播放速度值（从 SeekBar）
                int progress = speed_seekbar.getProgress(); // 注意：需确保主线程调用或传参
                float speed = (float) progress / 100f;

                String command;
                if (format.contains("extract")) {
                    // 从视频提取音频并调整速度
                    command = String.format("-y -i \"%s\" -vn -af \"atempo=%.2f\" -c:a libmp3lame -q:a 0 -ar %s -ac %s \"%s\"",
                            inputPath, speed, sampleRate, channels, outputPath);
                } else {
                    // 普通音频压缩并调整速度
                    command = String.format("-y -i \"%s\" -af \"atempo=%.2f,volume=%s\" -b:a %s -ar %s -ac %s \"%s\"",
                            inputPath, speed, volume, quality.split(" ")[0], sampleRate, channels, outputPath);
                }

                Log.d(TAG, "FFmpeg command: " + command);
                FFmpegKit.execute(command);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "FFmpeg 执行失败: " + e.getMessage());
                compressionException = e;
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            MainActivity activity = activityReference.get();
            if (activity != null) {
                if (success) {
                    // 计算压缩率
                    File inputFile = new File(activity.inputFilePath);
                    File outputFile = new File(activity.inputFilePath);
                    long inputSize = inputFile.length();
                    long outputSize = outputFile.length();
                    float compressionRatio = 100f * (inputSize - outputSize) / inputSize;

                    String resultMessage = String.format(Locale.getDefault(),
                            "压缩完成!\n原始大小: %.2f MB\n压缩后: %.2f MB\n压缩率: %.1f%%",
                        inputSize / 1024f / 1024f,
                        outputSize / 1024f / 1024f,
                        compressionRatio
                    );

                    activity.statusText.setText(resultMessage);
                    Log.d(TAG, "压缩完成: " + resultMessage);

                    // 通知媒体库更新
                    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(outputFile)));

                    Toast.makeText(activity, "音频已保存到: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } else {
                    String errorMessage = "压缩失败: " + (compressionException != null ? compressionException.getMessage() : "未知错误");
                    activity.statusText.setText(errorMessage);
                    Log.e(TAG, errorMessage, compressionException);
                    Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private int getSelectedSampleRate() {
        String selected = sampleRateSpinner.getSelectedItem().toString();
        String rateStr = selected.split(" ")[0]; // 获取数字部分
        try {
            return Integer.parseInt(rateStr);
        } catch (NumberFormatException e) {
            return 44100; // 默认返回 CD 质量采样率
        }
    }

    // 获取选择的音量值的辅助方法
    private String getSelectedVolume() {
        String volumeStr = volumeSpinner.getSelectedItem().toString();
        String dBValue = volumeStr.split(" ")[0]; // 提取 "+5", "0", "-5"
        if ("0".equals(dBValue)) return "1.0"; // 直接用线性增益 1.0
        return dBValue.replace("+", "").replace("-", "") + "dB";
    }

    private void openOutputFolder() {
        File musicDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "musiczip");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(musicDir.getAbsolutePath());
        intent.setDataAndType(uri, "resource/folder");

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件夹：" + musicDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String selected = parent.getItemAtPosition(position).toString();
        Log.d(TAG, "选项被改变: " + selected);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "未选择任何选项");
    }
}
