package com.winlator.cmod.winhandler;
import com.winlator.cmod.core.ProcessInfo;
public interface OnGetProcessInfoListener {
    void onGetProcessInfo(int index, int count, ProcessInfo processInfo);
}
