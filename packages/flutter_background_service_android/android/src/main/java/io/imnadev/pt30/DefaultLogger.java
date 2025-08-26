package io.imnadev.pt30;


import android.util.Log;

import com.pt.sdk.Logger;

public class DefaultLogger implements Logger {
    @Override
    public void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    @Override
    public void e(String tag, String msg, Throwable throwable) {
        Log.e(tag, msg, throwable);

    }

    @Override
    public void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    @Override
    public void w(String tag, String msg, Throwable throwable) {
        Log.w(tag, msg, throwable);

    }

    @Override
    public void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    @Override
    public void i(String tag, String msg, Throwable throwable) {
        Log.i(tag, msg, throwable);
    }

    @Override
    public void d(String tag, String msg) {
        Log.d(tag, msg);
    }

    @Override
    public void d(String tag, String msg, Throwable throwable) {
        Log.d(tag, msg, throwable);
    }

    @Override
    public void v(String tag, String msg) {
        Log.v(tag, msg);
    }

    @Override
    public void v(String tag, String msg, Throwable throwable) {
        Log.v(tag, msg, throwable);
    }
}
