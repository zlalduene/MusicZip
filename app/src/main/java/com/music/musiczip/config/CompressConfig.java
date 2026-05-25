package com.music.musiczip.config;

public class CompressConfig {
    public String inputPath;
    public String outputPath;
    public String quality;      // e.g., "320k"
    public String format;       // e.g., "mp3", "extract"
    public int sampleRate;      // e.g., 44100
    public int channels;        // 1 or 2
    public String volume;       // e.g., "1.0", "+5dB"
    public float speed;         // e.g., 1.2f
}
