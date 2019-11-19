package cn.zhengshang.config;

import android.graphics.ImageFormat;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.Size;

public class PhotoConfig implements Cloneable {
    private boolean hdr = false;
    private Size size = Constants.DEF_PIC_SIZE;
    private int format = ImageFormat.JPEG;
    private boolean stabilization = false;
    /**
     * 连拍
     */
    private boolean burst;

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

    public boolean isBurst() {
        return burst;
    }

    public PhotoConfig setBurst(boolean burst) {
        this.burst = burst;
        return this;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        PhotoConfig photoConfig = (PhotoConfig) super.clone();
        photoConfig.size = (Size) photoConfig.size.clone();
        return photoConfig;
    }
}
