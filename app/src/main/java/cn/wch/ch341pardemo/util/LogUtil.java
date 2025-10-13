package cn.wch.ch341pardemo.util;

import android.util.Log;

public class LogUtil {

    private static final String Tag = "CH341ParDemo";

    public static void d(String message){
        Log.d(Tag,message);
    }
    public static void e(String message) {
        Log.e(Tag, message);
    }
    public static void e(String message, Throwable throwable) {
        Log.e(Tag, message, throwable);
    }
}
