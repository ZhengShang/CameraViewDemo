package com.zhiyun.android.config;

import com.zhiyun.android.base.Constants;

public class ManualConfig {
    private int ae;
    private long sec;
    private int iso;
    private int wb;
    private float af;
    private float wt;
    private boolean manual;

    ManualConfig() {
        restore();
    }

    public int getAe() {
        return ae;
    }

    public void setAe(int ae) {
        this.ae = ae;
    }

    public long getSec() {
        return sec;
    }

    public void setSec(long sec) {
        this.sec = sec;
    }

    public int getIso() {
        return iso;
    }

    public void setIso(int iso) {
        this.iso = iso;
    }

    public int getWb() {
        return wb;
    }

    public void setWb(int wb) {
        this.wb = wb;
    }

    public float getAf() {
        return af;
    }

    public void setAf(float af) {
        this.af = af;
    }

    public float getWt() {
        return wt;
    }

    public void setWt(float wt) {
        this.wt = wt;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    /**
     * 恢复到默认状态
     */
    public void restore() {
        ae = Constants.DEF_MANUAL_AE;
        sec = Constants.DEF_MANUAL_SEC;
        iso = Constants.DEF_MANUAL_ISO;
        wb = Constants.DEF_MANUAL_WB;
        af = Constants.DEF_MANUAL_AF;
        wt = Constants.DEF_MANUAL_WT;
    }
}
