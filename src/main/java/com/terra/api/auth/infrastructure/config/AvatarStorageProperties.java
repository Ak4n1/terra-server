package com.terra.api.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage.avatar")
public class AvatarStorageProperties {

    private String dir = "uploads/avatars";
    private long maxBytes = 5 * 1024 * 1024;
    private int outputSize = 512;
    private float webpQuality = 0.9f;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(int outputSize) {
        this.outputSize = outputSize;
    }

    public float getWebpQuality() {
        return webpQuality;
    }

    public void setWebpQuality(float webpQuality) {
        this.webpQuality = webpQuality;
    }
}

