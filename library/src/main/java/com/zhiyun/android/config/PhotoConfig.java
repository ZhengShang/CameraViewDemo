package com.zhiyun.android.config;

import android.graphics.ImageFormat;

import com.zhiyun.android.base.Constants;
import com.zhiyun.android.base.Size;

public class PhotoConfig {
    private boolean hdr = false;
    private Size size = Constants.DEF_PIC_SIZE;
    private int format = ImageFormat.JPEG;
    private boolean stabilization = false;

    public boolean isHdr() {
        return hdr;
    }

    public PhotoConfig setHdr(boolean hdr) {
        this.hdr = hdr;
        return this;
    }

    public Size getSize() {
        return size;
    }

    public PhotoConfig setSize(Size size) {
        this.size = size;
        return this;
    }

    public int getFormat() {
        return format;
    }

    public PhotoConfig setFormat(int format) {
        this.format = format;
        return this;
    }

    public boolean isStabilization() {
        return stabilization;
    }

    public PhotoConfig setStabilization(boolean stabilization) {
        this.stabilization = stabilization;
        return this;
    }
}
