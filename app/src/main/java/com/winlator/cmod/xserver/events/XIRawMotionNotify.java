package com.winlator.cmod.xserver.events;

import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;

import java.io.IOException;

public class XIRawMotionNotify extends Event {
    private static final int GENERIC_EVENT_CODE = 35;
    private static final short XI_RAW_MOTION = 17;

    private final byte extensionOpcode;
    private final int deviceId;
    private final double deltaX;
    private final double deltaY;

    public XIRawMotionNotify(int deviceId, byte extensionOpcode, double deltaX, double deltaY) {
        super(GENERIC_EVENT_CODE);
        this.deviceId = deviceId;
        this.extensionOpcode = extensionOpcode;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    @Override
    public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(code);
            outputStream.writeByte(extensionOpcode);
            outputStream.writeShort(sequenceNumber);
            outputStream.writeInt(9);
            outputStream.writeShort(XI_RAW_MOTION);
            outputStream.writeShort((short)deviceId);
            outputStream.writeInt((int)System.currentTimeMillis());
            outputStream.writeInt(0);
            outputStream.writeShort((short)deviceId);
            outputStream.writeShort((short)1);
            outputStream.writeInt(0);
            outputStream.writePad(4);

            outputStream.writeInt(0x03);
            outputStream.writeFP3232(deltaX);
            outputStream.writeFP3232(deltaY);
            outputStream.writeFP3232(deltaX);
            outputStream.writeFP3232(deltaY);
        }
    }
}
