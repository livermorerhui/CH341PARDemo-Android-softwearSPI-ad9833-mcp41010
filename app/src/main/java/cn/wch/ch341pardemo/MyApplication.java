package cn.wch.ch341pardemo;

import android.app.Application;
import android.content.Context;

import cn.wch.ch341lib.CH341Manager;

public class MyApplication extends Application {
    private static Application application;
    @Override
    public void onCreate() {
        super.onCreate();
        application=this;
        CH341Manager.getInstance().init(this);
    }

    public static Context getContext(){
        return application;
    }
}
