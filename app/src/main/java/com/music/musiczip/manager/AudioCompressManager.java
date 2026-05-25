package com.music.musiczip.manager;

import android.os.AsyncTask;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

public class AudioCompressManager {
    private static final String TAG = "AudioCompressManager";

    // 定义回调接口，用于将处理结果安全地传回 UI 层
    public interface CompressCallback {
        void onStart();
        void onSuccess(String outputPath, String resultMessage);
        void onError(String errorMessage);
    }

    /**
     * 静态配置内部类，用于承载所有压缩所需的参数
     */
    public static class CompressConfig {
        public String inputPath;
        public String outputPath;
        public String quality;
        public String format;
        public String sampleRate;
        public String channels;
        public String volume;
        public float speed;

        public CompressConfig(String inputPath, String outputPath, String quality, String format,
                              String sampleRate, String channels, String volume, float speed) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.quality = quality;
            this.format = format;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.volume = volume;
            this.speed = speed;
        }
    }

    /**
     * 执行音频压缩/提取的主入口
     *
     * @param config   压缩配置参数
     * @param callback 结果回调
     */
    public void compressAudio(CompressConfig config, CompressCallback callback) {
        if (config == null || config.inputPath == null || config.outputPath == null) {
            if (callback != null) {
                callback.onError("输入或输出路径不能为空");
            }
            return;
        }

        if (callback != null) {
            callback.onStart();
        }

        // 启动异步任务
        new AudioCompressionTask(config, callback).execute();
    }

    /**
     * 使用静态内部类避免持有外部类强引用导致内存泄漏
     */
    private static class AudioCompressionTask extends AsyncTask<Void, Void, Boolean> {
        private final CompressConfig config;
        private final WeakReference<CompressCallback> callbackRef;
        private Exception compressionException;
        private String finalResultMessage;

        AudioCompressionTask(CompressConfig config, CompressCallback callback) {
            this.config = config;
            this.callbackRef = new WeakReference<>(callback);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                Log.d(TAG, "FFmpeg 异步任务开始执行...");

                String command;
                // 彻底脱离对 Spinner 的依赖，改为直接判断传入的业务参数
                if (config.format != null && config.format.contains("extract")) {
                    // 从视频提取音频并调整速度 (注意：原逻辑中提取未带音量参数，此处保持原样)
                    command = String.format(Locale.getDefault(),
                            "-y -i \"%s\" -vn -af \"atempo=%.2f\" -c:a libmp3lame -q:a 0 -ar %s -ac %s \"%s\"",
                            config.inputPath, config.speed, config.sampleRate, config.channels, config.outputPath);
                } else {
                    // 普通音频压缩、调速、调音量
                    command = String.format(Locale.getDefault(),
                            "-y -i \"%s\" -af \"atempo=%.2f,volume=%s\" -b:a %s -ar %s -ac %s \"%s\"",
                            config.inputPath, config.speed, config.volume, config.quality, config.sampleRate, config.channels, config.outputPath);
                }

                Log.d(TAG, "执行 FFmpeg 命令: " + command);

                // 执行 FFmpeg 同步阻塞命令（因为当前处于子线程后台，可以安全阻塞）
                FFmpegSession session = FFmpegKit.execute(command);

                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    Log.d(TAG, "FFmpeg 执行成功");

                    // 计算压缩率
                    File inputFile = new File(config.inputPath);
                    File outputFile = new File(config.outputPath);

                    if (outputFile.exists()) {
                        long inputSize = inputFile.length();
                        long outputSize = outputFile.length();
                        float compressionRatio = inputSize > 0 ? (100f * (inputSize - outputSize) / inputSize) : 0;

                        finalResultMessage = String.format(Locale.getDefault(),
                                "压缩完成!\n原始大小: %.2f MB\n压缩后: %.2f MB\n压缩率: %.1f%%",
                                inputSize / 1024f / 1024f,
                                outputSize / 1024f / 1024f,
                                compressionRatio
                        );
                        return true;
                    } else {
                        throw new Exception("FFmpeg 报告成功，但未找到输出文件。");
                    }
                } else {
                    String failMsg = String.format(Locale.getDefault(), "FFmpeg 失败，退出码: %s, 错误日志: %s",
                            session.getReturnCode(), session.getFailStackTrace());
                    throw new Exception(failMsg);
                }

            } catch (Exception e) {
                Log.e(TAG, "FFmpeg 执行期发生异常: ", e);
                compressionException = e;
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            CompressCallback callback = callbackRef.get();
            if (callback == null) {
                Log.w(TAG, "onPostExecute: 回调上下文已销毁，放弃通知。");
                return;
            }

            if (success) {
                callback.onSuccess(config.outputPath, finalResultMessage);
            } else {
                String errorMsg = "压缩失败: " + (compressionException != null ? compressionException.getMessage() : "未知错误");
                callback.onError(errorMsg);
            }
        }
    }
}