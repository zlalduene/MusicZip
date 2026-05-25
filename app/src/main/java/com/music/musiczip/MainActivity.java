package com.music.musiczip;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.music.musiczip.helper.PermissionHelper;
import com.music.musiczip.manager.AudioCompressManager;
import com.music.musiczip.utils.FileUtils;

import java.io.File;
import java.util.Locale;

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {
    private static final int PICK_AUDIO_REQUEST = 1;
    private static final String TAG = "MainActivity";

    private TextView inputPathText;
    private Spinner qualitySpinner;
    private Spinner formatSpinner;
    private Spinner sampleRateSpinner;
    private Spinner channelsSpinner;
    private Spinner volumeSpinner;
    private TextView statusText;
    private Button selectFileButton;
    private Button compressButton;
    private Button openOutputFolderButton;
    private Button gotoTrimButton;
    private String inputFilePath;
    private TextView speed_value_text;
    private SeekBar speed_seekbar;

    // 引入解耦后的 Helper 和 Manager 实例
    private PermissionHelper permissionHelper;
    private AudioCompressManager compressManager;

    // 统一处理权限状态的回调接口
    private final PermissionHelper.PermissionCallback permissionCallback = new PermissionHelper.PermissionCallback() {
        @Override
        public void onGranted() {
            Log.d(TAG, "权限已授予，初始化UI组件");
            setupUI();
            selectFileButton.setEnabled(true);
            compressButton.setEnabled(true);
        }

        @Override
        public void onDenied() {
            Log.w(TAG, "权限被拒绝，禁用核心功能");
            statusText.setText("请先授予存储权限");
            selectFileButton.setEnabled(false);
            compressButton.setEnabled(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: 开始");
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            // 1. 初始化所有视图组件
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

            // 2. 初始化核心逻辑构件
            permissionHelper = new PermissionHelper(this);
            compressManager = new AudioCompressManager();

            // 3. 触发权限架构挡板检查
            permissionHelper.checkAndRequestPermissions(permissionCallback);

        } catch (Exception e) {
            Log.e(TAG, "onCreate 发生错误: ", e);
        }
    }

    /**
     * 转发旧版运行时权限的回调请求给 Helper
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults, permissionCallback);
    }

    /**
     * 处理外部调用的返回结果（选择文件 / 所有文件管理授权页面返回）
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        // 如果是音频或视频文件回传
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Log.d(TAG, "onActivityResult: 文件URI: " + uri.toString());
                // 调用外部纯净工具类解析物理绝对路径
                inputFilePath = FileUtils.getRealPathFromUri(this, uri);

                if (inputFilePath != null) {
                    inputPathText.setText(inputFilePath);
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType != null && mimeType.startsWith("video/")) {
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
        } else {
            // 将属于存储管理的ActivityResult转发给Helper处理
            permissionHelper.handleActivityResult(requestCode, resultCode, permissionCallback);
        }
    }

    private void setupUI() {
        Log.d(TAG, "setupUI: 开始设置UI组件");
        selectFileButton.setOnClickListener(v -> selectMediaFile());
        compressButton.setOnClickListener(v -> compressAudio());
        openOutputFolderButton.setOnClickListener(v -> openOutputFolder());
        setupSpinners();
        Log.d(TAG, "setupUI: UI组件设置完成");
    }

    private void selectMediaFile() {
        Log.d(TAG, "selectMediaFile: 开始选择音频或视频文件");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        String[] mimeTypes = {"audio/*", "video/*"};
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择音频或视频文件"), PICK_AUDIO_REQUEST);
    }

    /**
     * 核心解耦：提取界面参数并通知 Manager 执行后台 FFmpeg 指令
     */
    private void compressAudio() {
        if (inputFilePath == null) {
            Log.w(TAG, "compressAudio: 未选择文件");
            Toast.makeText(this, "请先选择音频文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "compressAudio: 开始准备压缩上下文");

        try {
            // 从 UI 层捕获用户设定的原始状态数据
            String rawQuality = qualitySpinner.getSelectedItem().toString();
            String rawFormat = formatSpinner.getSelectedItem().toString();
            int sampleRate = getSelectedSampleRate();
            int channels = channelsSpinner.getSelectedItem().toString().equals("单声道") ? 1 : 2;
            String volume = getSelectedVolume();
            float speed = (float) speed_seekbar.getProgress() / 100f;

            // 调用纯净的 FileUtils 计算并确保创建独立的保存绝对路径
            String outputPath = FileUtils.generateOutputFilePath(inputFilePath, rawQuality, rawFormat);

            if (outputPath == null) {
                Toast.makeText(this, "生成输出文件路径失败", Toast.LENGTH_SHORT).show();
                return;
            }

            // 封装数据到静态配置对象中，断开与 UI 组件的任何强耦合关联
            AudioCompressManager.CompressConfig config = new AudioCompressManager.CompressConfig(
                    inputFilePath,
                    outputPath,
                    rawQuality.split(" ")[0],
                    rawFormat,
                    String.valueOf(sampleRate),
                    String.valueOf(channels),
                    volume,
                    speed
            );

            // 调用音频压缩治理器执行具体的异步 FFmpeg 处理流程
            compressManager.compressAudio(config, new AudioCompressManager.CompressCallback() {
                @Override
                public void onStart() {
                    statusText.setText("正在执行媒体处理，输出路径：\n" + outputPath);
                    compressButton.setEnabled(false); // 防止重复点击
                }

                @Override
                public void onSuccess(String finalPath, String resultMessage) {
                    compressButton.setEnabled(true);
                    statusText.setText(resultMessage);

                    // 通知底层媒体扫描库去探测并收录这个新生成的文件
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(finalPath))));
                    Toast.makeText(MainActivity.this, "音频处理成功！", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String errorMessage) {
                    compressButton.setEnabled(true);
                    statusText.setText(errorMessage);
                    Toast.makeText(MainActivity.this, "任务处理遇到技术异常", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "压缩前置阶段配置失败: ", e);
            Toast.makeText(this, "前置参数解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int getSelectedSampleRate() {
        String selected = sampleRateSpinner.getSelectedItem().toString();
        String rateStr = selected.split(" ")[0];
        try {
            return Integer.parseInt(rateStr);
        } catch (NumberFormatException e) {
            return 44100;
        }
    }

    private String getSelectedVolume() {
        String volumeStr = volumeSpinner.getSelectedItem().toString();
        String dBValue = volumeStr.split(" ")[0];
        if ("0".equals(dBValue)) return "1.0";
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

    private void setupSpinners() {
        Log.d(TAG, "setupSpinners: 开始设置所有下拉选项");
        String[] qualityOptions = {"960k (超高音质)", "320k (CD音质)", "256k (高品质)", "192k (中等品质)", "128k (标准品质)", "96k (低品质)", "64k (超低品质)", "32k (极低品质)", "16k (最低品质)"};
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, qualityOptions);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        qualitySpinner.setSelection(1);

        String[] channelsOptions = {"立体声", "单声道"};
        ArrayAdapter<String> channelsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, channelsOptions);
        channelsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        channelsSpinner.setAdapter(channelsAdapter);
        channelsSpinner.setSelection(0);

        String[] formatOptions = {"mp3 (常用音频格式)", "aac (苹果设备优选)", "m4a (苹果设备优选)", "ogg (开源格式)", "wav (无损格式)", "extract (从视频提取音频)"};
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, formatOptions);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(formatAdapter);
        formatSpinner.setSelection(0);

        String[] sampleRateOptions = {"48000 Hz (高音质)", "44100 Hz (CD质量)", "32000 Hz (高质量)", "22050 Hz (中等质量)", "16000 Hz (语音质量)", "11025 Hz (低质量)", "8000 Hz (电话质量)"};
        ArrayAdapter<String> sampleRateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sampleRateOptions);
        sampleRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleRateSpinner.setAdapter(sampleRateAdapter);
        sampleRateSpinner.setSelection(1);

        String[] volumeOptions = {"+50 dB (超超级增强)", "+30 dB (超级增强)", "+20 dB (极限增强)", "+15 dB (大幅增强)", "+10 dB (中度增强)", "+5 dB (轻微增强)", "0 dB (保持原样)", "-5 dB (轻微降低)", "-10 dB (中度降低)", "-15 dB (大幅降低)", "-20 dB (极限降低)"};
        ArrayAdapter<String> volumeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, volumeOptions);
        volumeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        volumeSpinner.setAdapter(volumeAdapter);
        volumeSpinner.setSelection(6);

        qualitySpinner.setOnItemSelectedListener(this);
        formatSpinner.setOnItemSelectedListener(this);
        sampleRateSpinner.setOnItemSelectedListener(this);
        channelsSpinner.setOnItemSelectedListener(this);
        volumeSpinner.setOnItemSelectedListener(this);
        Log.d(TAG, "setupSpinners: 所有下拉选项设置完成");
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