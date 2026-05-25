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

                // ==================== 核心修改：使用 asetrate 实现变速又变调（声音变粗且不撕裂） ====================
                float speed = config.speed;

                // 1. 假设标准的原始采样率是输入或输出设定的采样率（这里以 config.sampleRate 为基准）
                int baseSampleRate = Integer.parseInt(config.sampleRate);

                // 2. 计算出欺骗播放器的新采样率。比如 44100 * 0.5 = 22050
                int targetSampleRate = Math.round(baseSampleRate * speed);

                // 3. 约束安全边界：FFmpeg 采样率不能低于 512Hz
                if (targetSampleRate < 512) {
                    targetSampleRate = 512;
                }

                // 4. 组装滤镜链：先改变读取速率（同时改变速度和音调），再强制重采样回用户选定的标准采样率
                // 这样既能实现不撕裂的变粗放慢，最终输出的音频文件格式又完全标准。
                String speedFilter = String.format(Locale.US, "asetrate=%d,aresample=%s", targetSampleRate, config.sampleRate);
                // =============================================================================================

                String command;
                // 判定是提取音频还是常规压缩
                if (config.format != null && config.format.contains("extract")) {
                    // 从视频提取音频
                    command = String.format(Locale.getDefault(),
                            "-y -i \"%s\" -vn -af \"%s\" -c:a libmp3lame -q:a 0 -ar %s -ac %s \"%s\"",
                            config.inputPath, speedFilter, config.sampleRate, config.channels, config.outputPath);
                } else {
                    // 普通音频压缩，加入重构后的速度滤镜和音量
                    command = String.format(Locale.getDefault(),
                            "-y -i \"%s\" -af \"%s,volume=%s\" -b:a %s -ar %s -ac %s \"%s\"",
                            config.inputPath, speedFilter, config.volume, config.quality, config.sampleRate, config.channels, config.outputPath);
                }

                Log.d(TAG, "执行 FFmpeg 命令: " + command);

                // 执行 FFmpeg 同步阻塞命令
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
                                "处理完成!\n原始大小: %.2f MB\n处理后: %.2f MB\n文件大小变化: %.1f%%",
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
                String errorMsg = "处理失败: " + (compressionException != null ? compressionException.getMessage() : "未知错误");
                callback.onError(errorMsg);
            }
        }
    }
}