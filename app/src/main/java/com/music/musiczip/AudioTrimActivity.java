package com.music.musiczip;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.File;
import java.util.Locale;
import android.database.Cursor;

public class AudioTrimActivity extends AppCompatActivity {
    private static final String TAG = "AudioTrimActivity";
    private static final int PICK_AUDIO_REQUEST = 1;
    
    private Uri selectedFileUri;
    private String inputFilePath;
    private TextView inputPathText;
    private TextView startTimeEdit;
    private TextView endTimeEdit;
    private TextView statusText;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private SeekBar audioSeekBar;
    private MaterialButton playPauseButton;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Runnable updateSeekBarRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Log.d(TAG, "开始初始化AudioTrimActivity");
            setContentView(R.layout.activity_audio_trim);
            Log.d(TAG, "布局设置完成");

            initializeViews();
            setupListeners();
            
            Log.d(TAG, "AudioTrimActivity初始化完成");
            
        } catch (Exception e) {
            String errorMsg = String.format("初始化失败: %s - %s", e.getClass().getSimpleName(), e.getMessage());
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            
            new Handler().postDelayed(() -> {
                finish();
                Log.d(TAG, "Activity已结束");
            }, 3500);
        }
    }

    private void initializeViews() {
        Log.d(TAG, "开始初始化视图组件");
        
        // 设置Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        Log.d(TAG, "Toolbar设置完成");

        // 初始化基本视图
        inputPathText = findViewById(R.id.input_path_text_trim);
        startTimeEdit = findViewById(R.id.start_time_edit);
        endTimeEdit = findViewById(R.id.end_time_edit);
        statusText = findViewById(R.id.status_text_trim);
        
        // 初始化播放控制组件
        audioSeekBar = findViewById(R.id.audio_seekbar);
        currentTimeText = findViewById(R.id.current_time);
        totalTimeText = findViewById(R.id.total_time);
        playPauseButton = findViewById(R.id.play_pause_button);
        
        // 初始化播放控件状态
        audioSeekBar.setEnabled(false);
        playPauseButton.setEnabled(false);
        
        Log.d(TAG, "所有视图组件初始化完成");
    }

    private void setupListeners() {
        Log.d(TAG, "开始设置监听器");
        
        // 设置基本按钮监听器
        findViewById(R.id.select_file_button_trim).setOnClickListener(v -> selectAudioFile());
        findViewById(R.id.clear_file_button).setOnClickListener(v -> clearCurrentFile());
        findViewById(R.id.trim_button).setOnClickListener(v -> trimAudio());
        findViewById(R.id.open_output_folder_button_trim).setOnClickListener(v -> openOutputFolder());

        // 设置播放控制监听器
        setupPlaybackControls();
        
        Log.d(TAG, "所有监听器设置完成");
    }

    private void selectAudioFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            Log.d(TAG, "选择的文件 URI: " + selectedFile);
            
            if (selectedFile != null) {
                try {
                    selectedFileUri = selectedFile;  // 保存URI
                    
                    // 获取真实文件路径（如果可能的话）
                    String[] projection = {MediaStore.Audio.Media.DATA};
                    Cursor cursor = getContentResolver().query(selectedFile, projection, null, null, null);
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                        inputFilePath = cursor.getString(column_index);
                        cursor.close();
                        Log.d(TAG, "通过MediaStore获取的文件路径: " + inputFilePath);
                    } else {
                        // 如果无法通过MediaStore获取路径，使用URI字符串
                        inputFilePath = null;
                        Log.d(TAG, "无法获取文件路径，将使用URI: " + selectedFileUri);
                    }

                    // 释放旧的MediaPlayer
                    if (mediaPlayer != null) {
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                    
                    // 创建新的MediaPlayer
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(this, selectedFile);
                    mediaPlayer.prepare();
                    
                    // 更新UI显示文件名
                    String fileName = getFileName(selectedFile);
                    Log.d(TAG, "文件名: " + fileName);
                    inputPathText.setText(fileName);
                    
                    // 设置进度条
                    int duration = mediaPlayer.getDuration();
                    audioSeekBar.setMax(duration);
                    audioSeekBar.setProgress(0);
                    audioSeekBar.setEnabled(true);
                    
                    // 设置时间显示
                    currentTimeText.setText("00:00.000");
                    totalTimeText.setText(formatTime(duration));
                    
                    // 启用播放按钮
                    playPauseButton.setEnabled(true);
                    playPauseButton.setIcon(getDrawable(android.R.drawable.ic_media_play));
                    
                    // 设置播放完成监听器
                    mediaPlayer.setOnCompletionListener(mp -> {
                        playPauseButton.setIcon(getDrawable(android.R.drawable.ic_media_play));
                        if (updateSeekBarRunnable != null) {
                            handler.removeCallbacks(updateSeekBarRunnable);
                        }
                    });
                    
                    Log.d(TAG, "音频文件加载成功");
                    
                } catch (Exception e) {
                    String errorMsg = "加载音频文件失败: " + e.getMessage();
                    Log.e(TAG, errorMsg, e);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                        Log.d(TAG, "从MediaStore获取到的文件名: " + result);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取文件名失败: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
            Log.d(TAG, "从URI路径获取到的文件名: " + result);
        }
        return result;
    }

    private void setupPlaybackControls() {
        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    playPauseButton.setIcon(getDrawable(android.R.drawable.ic_media_play));
                    if (updateSeekBarRunnable != null) {
                        handler.removeCallbacks(updateSeekBarRunnable);
                    }
                } else {
                    mediaPlayer.start();
                    playPauseButton.setIcon(getDrawable(android.R.drawable.ic_media_pause));
                    startSeekBarUpdate();
                }
            }
        });

        audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    currentTimeText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    handler.removeCallbacks(updateSeekBarRunnable);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    startSeekBarUpdate();
                }
            }
        });
    }

    private void startSeekBarUpdate() {
        if (updateSeekBarRunnable == null) {
            updateSeekBarRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        audioSeekBar.setProgress(currentPosition);
                        currentTimeText.setText(formatTime(currentPosition));
                        handler.postDelayed(this, 50); // 每50毫秒更新一次
                    }
                }
            };
        }
        handler.post(updateSeekBarRunnable);
    }

    private void clearCurrentFile() {
        // 停止播放
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        
        // 重置UI
        selectedFileUri = null;
        inputFilePath = null;
        inputPathText.setText("未选择文件");
        startTimeEdit.setText("");
        endTimeEdit.setText("");
        currentTimeText.setText("00:00.000");
        totalTimeText.setText("00:00.000");
        audioSeekBar.setProgress(0);
        audioSeekBar.setEnabled(false);
        playPauseButton.setEnabled(false);
        playPauseButton.setIcon(getDrawable(android.R.drawable.ic_media_play));
        
        // 取消定时器
        if (updateSeekBarRunnable != null) {
            handler.removeCallbacks(updateSeekBarRunnable);
            updateSeekBarRunnable = null;
        }
        
        Toast.makeText(this, "已清除当前文件", Toast.LENGTH_SHORT).show();
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        int ms = milliseconds % 1000;
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, ms);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (updateSeekBarRunnable != null) {
                handler.removeCallbacks(updateSeekBarRunnable);
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void trimAudio() {
        Log.d(TAG, "开始音频剪切，检查文件URI: " + selectedFileUri);
        
        if (selectedFileUri == null) {
            Log.d(TAG, "未选择文件");
            Toast.makeText(this, "请先选择音频文件", Toast.LENGTH_SHORT).show();
            return;
        }

        String startTimeStr = startTimeEdit.getText().toString();
        String endTimeStr = endTimeEdit.getText().toString();

        if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            Toast.makeText(this, "请输入开始和结束时间", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 将输入的秒数转换为毫秒
            float startTimeSeconds = Float.parseFloat(startTimeStr);
            float endTimeSeconds = Float.parseFloat(endTimeStr);
            
            // 检查时间范围
            if (startTimeSeconds >= endTimeSeconds) {
                Toast.makeText(this, "结束时间必须大于开始时间", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mediaPlayer != null && (endTimeSeconds * 1000) > mediaPlayer.getDuration()) {
                Toast.makeText(this, "结束时间不能超过音频总长度", Toast.LENGTH_SHORT).show();
                return;
            }

            // 生成输出文件名
            String outputPath = getOutputFileName(getFileName(selectedFileUri), startTimeSeconds, endTimeSeconds);
            Log.d(TAG, "输出文件路径: " + outputPath);
            
            // 开始剪切任务
            statusText.setText("正在处理...");
            
            // 将输入文件复制到缓存目录
            File tempFile = new File(getCacheDir(), "temp_input.mp3");
            try (java.io.InputStream in = getContentResolver().openInputStream(selectedFileUri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            
            // 构建FFmpeg命令
            String command = String.format("-y -i \"%s\" -ss %.3f -to %.3f -acodec copy \"%s\"",
                tempFile.getAbsolutePath(), startTimeSeconds, endTimeSeconds, outputPath);
            
            Log.d(TAG, "执行FFmpeg命令: " + command);
            
            // 执行FFmpeg命令
            FFmpegKit.executeAsync(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    // 剪切成功
                    runOnUiThread(() -> {
                        statusText.setText("音频截取完成！");
                        Toast.makeText(this, "音频截取成功：" + new File(outputPath).getName(), 
                            Toast.LENGTH_LONG).show();
                    });
                } else {
                    Log.e(TAG, "FFmpeg执行失败: " + session.getOutput());
                    // 如果直接复制失败，尝试重新编码
                    String fallbackCommand = String.format(
                        "-y -i \"%s\" -ss %.3f -to %.3f -acodec libmp3lame -q:a 0 \"%s\"",
                        tempFile.getAbsolutePath(), startTimeSeconds, endTimeSeconds, outputPath
                    );
                    
                    Log.d(TAG, "尝试使用重新编码方式: " + fallbackCommand);
                    
                    FFmpegKit.executeAsync(fallbackCommand, fallbackSession -> {
                        String resultMessage;
                        if (ReturnCode.isSuccess(fallbackSession.getReturnCode())) {
                            resultMessage = "音频截取完成！";
                        } else {
                            Log.e(TAG, "重新编码也失败了: " + fallbackSession.getOutput());
                            resultMessage = "截取失败: " + fallbackSession.getOutput();
                        }
                        
                        final String finalMessage = resultMessage;
                        runOnUiThread(() -> {
                            statusText.setText(finalMessage);
                            Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                        });
                    });
                }
                
                // 删除临时文件
                tempFile.delete();
            });
            
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的时间格式", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败: " + e.getMessage(), e);
            Toast.makeText(this, "处理文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getOutputFileName(String inputName, float startTime, float endTime) {
        String nameWithoutExt = inputName;
        String extension = ".mp3";
        
        int lastDot = inputName.lastIndexOf('.');
        if (lastDot > 0) {
            nameWithoutExt = inputName.substring(0, lastDot);
            extension = inputName.substring(lastDot);
        }
        
        // 使用3位小数格式化时间戳
        String timeStamp = String.format(Locale.US, "%.3f-%.3f", startTime, endTime);

        File outputDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        return new File(outputDir, String.format("%s_%s%s", nameWithoutExt, timeStamp, extension)).getAbsolutePath();
    }

    private void openOutputFolder() {
        try {
            File outputDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "");
            if (!outputDir.exists()) {
                Toast.makeText(this, "输出文件夹还未创建", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(outputDir.getAbsolutePath());
            intent.setDataAndType(uri, "resource/folder");

            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开文件夹：" + outputDir.getAbsolutePath(), 
                    Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开文件夹失败: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
}
