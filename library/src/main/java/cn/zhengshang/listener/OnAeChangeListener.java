package cn.zhengshang.listener;

public interface OnAeChangeListener {

    /**
     * 实时返回当前的ae数值
     * @param ae ae的值,外部如果需要,可自行根据aeStep转换成EV的值.
     */
    void onAeChanged(int ae);
}
