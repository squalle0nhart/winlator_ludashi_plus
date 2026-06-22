package com.winlator.cmod.xserver.extensions;

import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.errors.XRequestError;

import java.io.IOException;

public interface Extension {
    String getName();

    byte getMajorOpcode();

    byte getFirstErrorId();

    byte getFirstEventId();

    void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError;

    default int getNumEvents() { return 0; }

    default int getNumErrors() { return 0; }

    default void setFirstEventId(byte id) {}

    default void setFirstErrorId(byte id) {}

    default void onClientDisconnected(XClient client) {}
}
