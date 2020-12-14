package cn.zhengshang.config;


import android.os.Build;
import android.util.ArrayMap;

final class Whitelist4k {

    private static final ArrayMap<String, String> WHITELIST = new ArrayMap<>(120);

    static {
        WHITELIST.put("SM-G930F", "Galaxy S7");
        WHITELIST.put("SM-G930FD", "Galaxy S7");
        WHITELIST.put("SM-G930W8", "Galaxy S7");
        WHITELIST.put("SM-G930S", "Galaxy S7");
        WHITELIST.put("SM-G930K", "Galaxy S7");
        WHITELIST.put("SM-G930L", "Galaxy S7");
        WHITELIST.put("SM-G9300", "Galaxy S7");
        WHITELIST.put("SM-G930V", "Galaxy S7");
        WHITELIST.put("SM-G930A", "Galaxy S7");
        WHITELIST.put("SM-G930AZ", "Galaxy S7");
        WHITELIST.put("SM-G930P", "Galaxy S7");
        WHITELIST.put("SM-G930T", "Galaxy S7");
        WHITELIST.put("SM-G930T1", "Galaxy S7");
        WHITELIST.put("SM-G930R4", "Galaxy S7");
        WHITELIST.put("SM-G930R6", "Galaxy S7");
        WHITELIST.put("SM-G9308", "Galaxy S7");
        WHITELIST.put("SM-G930U", "Galaxy S7");
        WHITELIST.put("SM-G930VL", "Galaxy S7");

        WHITELIST.put("SM-G935D", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935F", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935FD", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935W8", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935S", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935K", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935L", "Galaxy S7 Edge");
        WHITELIST.put("SM-G9350", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935V", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935A", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935P", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935T", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935U", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935R4", "Galaxy S7 Edge");
        WHITELIST.put("SM-G935X", "Galaxy S7 Edge");
        WHITELIST.put("SC-02H", "Galaxy S7 Edge");
        WHITELIST.put("SCV33", "Galaxy S7 Edge");

        WHITELIST.put("SM-N9300", "Galaxy Note 7");
        WHITELIST.put("SM-N930F", "Galaxy Note 7");
        WHITELIST.put("SM-N930K", "Galaxy Note 7");
        WHITELIST.put("SM-N930P", "Galaxy Note 7");
        WHITELIST.put("SM-N930T", "Galaxy Note 7");
        WHITELIST.put("SM-N930V", "Galaxy Note 7");
        WHITELIST.put("SM-N930W8", "Galaxy Note 7");

        WHITELIST.put("SM-N935", "Galaxy Note 7R");
        WHITELIST.put("SM-N935K", "Galaxy Note 7R");
        WHITELIST.put("SM-N935L", "Galaxy Note 7R");
        WHITELIST.put("SM-N935S", "Galaxy Note 7R");
        WHITELIST.put("SM-N935F", "Galaxy Note FE");

        WHITELIST.put("SM-G950", "Galaxy S8");
        WHITELIST.put("SM-G9500", "Galaxy S8");
        WHITELIST.put("SM-G9508", "Galaxy S8");
        WHITELIST.put("SM-G950A", "Galaxy S8");
        WHITELIST.put("SM-G950F", "Galaxy S8");
        WHITELIST.put("SM-G950FD", "Galaxy S8");
        WHITELIST.put("SM-G950J", "Galaxy S8");
        WHITELIST.put("SM-G950N", "Galaxy S8");
        WHITELIST.put("SM-G950P", "Galaxy S8");
        WHITELIST.put("SM-G950S", "Galaxy S8");
        WHITELIST.put("SM-G950T", "Galaxy S8");
        WHITELIST.put("SM-G950U", "Galaxy S8");
        WHITELIST.put("SM-G950U1", "Galaxy S8");
        WHITELIST.put("SM-G950V", "Galaxy S8");
        WHITELIST.put("SM-G950W", "Galaxy S8");
        WHITELIST.put("SM-G950X", "Galaxy S8");
        WHITELIST.put("SCV36", "Galaxy S8");
        WHITELIST.put("SC-02J", "Galaxy S8");

        WHITELIST.put("SM-G9550", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955A", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955D", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955F", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955FD", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955J", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955N", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955T", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955U", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955U1", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955W", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955X", "Galaxy S8 Plus");
        WHITELIST.put("SM-G955XC", "Galaxy S8 Plus");
        WHITELIST.put("SCV35", "Galaxy S8 Plus");
        WHITELIST.put("SC-03J", "Galaxy S8 Plus");

        WHITELIST.put("SM-N950", "Galaxy Note 8");
        WHITELIST.put("SM-N9500", "Galaxy Note 8");
        WHITELIST.put("SM-N9508", "Galaxy Note 8");
        WHITELIST.put("SM-N950A", "Galaxy Note 8");
        WHITELIST.put("SM-N950F", "Galaxy Note 8");
        WHITELIST.put("SM-N950K", "Galaxy Note 8");
        WHITELIST.put("SM-N950L", "Galaxy Note 8");
        WHITELIST.put("SM-N950N", "Galaxy Note 8");
        WHITELIST.put("SM-N950P", "Galaxy Note 8");
        WHITELIST.put("SM-N950S", "Galaxy Note 8");
        WHITELIST.put("SM-N950T", "Galaxy Note 8");
        WHITELIST.put("SM-N950U", "Galaxy Note 8");
        WHITELIST.put("SM-N950U1", "Galaxy Note 8");
        WHITELIST.put("SM-N950V", "Galaxy Note 8");
        WHITELIST.put("SM-N950W", "Galaxy Note 8");
        WHITELIST.put("SCV37", "Galaxy Note 8");
        WHITELIST.put("SC-01K", "Galaxy Note 8");
    }

    public static boolean isWhitelist() {
        return WHITELIST.containsKey(Build.MODEL.toUpperCase());
    }

}
