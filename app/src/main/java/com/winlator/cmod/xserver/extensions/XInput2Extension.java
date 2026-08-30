package com.winlator.cmod.xserver.extensions;

import static com.winlator.cmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.errors.BadImplementation;
import com.winlator.cmod.xserver.errors.BadValue;
import com.winlator.cmod.xserver.errors.BadWindow;
import com.winlator.cmod.xserver.errors.XRequestError;
import com.winlator.cmod.xserver.events.XIRawButtonPressNotify;
import com.winlator.cmod.xserver.events.XIRawButtonReleaseNotify;
import com.winlator.cmod.xserver.events.XIRawMotionNotify;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XInput2Extension implements Extension {
    public static final byte MAJOR_OPCODE = -107;
    private static final byte FIRST_EVENT_ID = 66;
    private static final byte FIRST_ERROR_ID = (byte)144;

    private static final int XI_MAJOR = 2;
    private static final int XI_MINOR = 2;
    private static final int XI_ALL_DEVICES = 0;
    private static final int XI_ALL_MASTER_DEVICES = 1;
    private static final int MASTER_POINTER_ID = 2;
    private static final int MASTER_KEYBOARD_ID = 3;
    private static final int XI_BUTTON_CLASS = 1;
    private static final int XI_VALUATOR_CLASS = 2;
    private static final long XI_RAW_BUTTON_PRESS_MASK = 1L << 15;
    private static final long XI_RAW_BUTTON_RELEASE_MASK = 1L << 16;
    private static final long XI_RAW_MOTION_MASK = 1L << 17;

    private final List<Selection> selections = new CopyOnWriteArrayList<>();

    private static abstract class ClientOpcodes {
        private static final byte GET_EXTENSION_VERSION = 1;
        private static final byte GET_CLIENT_POINTER = 45;
        private static final byte SELECT_EVENTS = 46;
        private static final byte QUERY_VERSION = 47;
        private static final byte QUERY_DEVICE = 48;
    }

    private static class Selection {
        Window window;
        XClient client;
        int windowId;
        long mask;
        int deviceId;
    }

    @Override
    public String getName() {
        return "XInputExtension";
    }

    @Override
    public byte getMajorOpcode() {
        return MAJOR_OPCODE;
    }

    @Override
    public byte getFirstErrorId() {
        return FIRST_ERROR_ID;
    }

    @Override
    public byte getFirstEventId() {
        return FIRST_EVENT_ID;
    }

    private static boolean isMasterDevice(int deviceId) {
        return deviceId == MASTER_POINTER_ID || deviceId == MASTER_KEYBOARD_ID;
    }

    private static boolean matchesSelection(Selection selection, int deviceId) {
        return selection.deviceId == XI_ALL_DEVICES
                || (selection.deviceId == XI_ALL_MASTER_DEVICES && isMasterDevice(deviceId))
                || selection.deviceId == deviceId;
    }

    private static void getExtensionVersion(XClient client, XInputStream inputStream,
                                            XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeShort((short)XI_MAJOR);
            outputStream.writeShort((short)0);
            outputStream.writeByte((byte)1);
            outputStream.writePad(19);
        }
    }

    private static void getClientPointer(XClient client, XInputStream inputStream,
                                         XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeByte((byte)1);
            outputStream.writeByte((byte)0);
            outputStream.writeShort((short)MASTER_POINTER_ID);
            outputStream.writePad(20);
        }
    }

    private static void queryVersion(XClient client, XInputStream inputStream,
                                     XOutputStream outputStream) throws IOException {
        int clientMajor = inputStream.readUnsignedShort();
        int clientMinor = inputStream.readUnsignedShort();
        inputStream.skip(client.getRemainingRequestLength());

        int negotiatedMajor = XI_MAJOR;
        int negotiatedMinor = XI_MINOR;
        if (clientMajor < XI_MAJOR || (clientMajor == XI_MAJOR && clientMinor < XI_MINOR)) {
            negotiatedMajor = clientMajor;
            negotiatedMinor = clientMinor;
        }

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeShort((short)negotiatedMajor);
            outputStream.writeShort((short)negotiatedMinor);
            outputStream.writePad(20);
        }
    }

    private static void writeButtonClass(XOutputStream outputStream) {
        int numButtons = Pointer.MAX_BUTTONS;
        int stateBytes = Math.max(4, ((numButtons + 31) / 32) * 4);
        int totalBytes = 8 + stateBytes + numButtons * 4;

        outputStream.writeShort((short)XI_BUTTON_CLASS);
        outputStream.writeShort((short)(totalBytes / 4));
        outputStream.writeShort((short)MASTER_POINTER_ID);
        outputStream.writeShort((short)numButtons);
        outputStream.writeInt(0);
        if (stateBytes > 4) outputStream.writePad(stateBytes - 4);
        for (int i = 0; i < numButtons; i++) outputStream.writeInt(0);
    }

    private static void writeValuatorClass(XOutputStream outputStream, int axisNumber) {
        outputStream.writeShort((short)XI_VALUATOR_CLASS);
        outputStream.writeShort((short)11);
        outputStream.writeShort((short)MASTER_POINTER_ID);
        outputStream.writeShort((short)axisNumber);
        outputStream.writeInt(0);
        outputStream.writeFP3232(0);
        outputStream.writeFP3232(0);
        outputStream.writeFP3232(0);
        outputStream.writeInt(0);
        outputStream.writeByte((byte)0);
        outputStream.writePad(3);
    }

    private static void queryDevice(XClient client, XInputStream inputStream,
                                    XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());

        byte[] name = "Virtual Core Pointer".getBytes(XServer.LATIN1_CHARSET);
        int namePad = (name.length + 3) & ~3;
        int numButtons = Pointer.MAX_BUTTONS;
        int buttonStateBytes = Math.max(4, ((numButtons + 31) / 32) * 4);
        int buttonClassBytes = 8 + buttonStateBytes + numButtons * 4;
        int deviceInfoSize = 12 + namePad + buttonClassBytes + 88;

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(deviceInfoSize / 4);
            outputStream.writeShort((short)1);
            outputStream.writePad(22);

            outputStream.writeShort((short)MASTER_POINTER_ID);
            outputStream.writeShort((short)1);
            outputStream.writeShort((short)MASTER_KEYBOARD_ID);
            outputStream.writeShort((short)3);
            outputStream.writeShort((short)name.length);
            outputStream.writeByte((byte)1);
            outputStream.writeByte((byte)0);
            outputStream.write(name);
            outputStream.writePad(namePad - name.length);

            writeButtonClass(outputStream);
            writeValuatorClass(outputStream, 0);
            writeValuatorClass(outputStream, 1);
        }
    }

    private void selectEvents(XClient client, XInputStream inputStream)
            throws XRequestError {
        int windowId = inputStream.readInt();
        int numMasks = inputStream.readUnsignedShort();
        inputStream.skip(2);

        if (numMasks == 0) throw new BadValue(numMasks);

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        for (int i = 0; i < numMasks; i++) {
            int deviceId = inputStream.readUnsignedShort();
            int maskLen = inputStream.readUnsignedShort();
            long mask = 0;

            for (int word = 0; word < maskLen; word++) {
                long value = inputStream.readUnsignedInt();
                if (word < 2) mask |= value << (word * 32);
            }

            final int selectedDeviceId = deviceId;
            selections.removeIf(old -> old.client == client
                    && old.windowId == windowId
                    && old.deviceId == selectedDeviceId);

            if (mask != 0) {
                Selection selection = new Selection();
                selection.client = client;
                selection.window = window;
                selection.windowId = windowId;
                selection.deviceId = deviceId;
                selection.mask = mask;
                selections.add(selection);
            }
        }

        inputStream.skip(client.getRemainingRequestLength());
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream,
                              XOutputStream outputStream)
            throws IOException, XRequestError {
        switch (client.getRequestData()) {
            case ClientOpcodes.GET_EXTENSION_VERSION:
                getExtensionVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.GET_CLIENT_POINTER:
                getClientPointer(client, inputStream, outputStream);
                break;
            case ClientOpcodes.SELECT_EVENTS:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    selectEvents(client, inputStream);
                }
                break;
            case ClientOpcodes.QUERY_VERSION:
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.QUERY_DEVICE:
                queryDevice(client, inputStream, outputStream);
                break;
            default:
                throw new BadImplementation();
        }
    }

    public void onClientDisconnected(XClient client) {
        selections.removeIf(selection -> selection.client == client);
    }

    public void emitRawMotion(int deviceId, double deltaX, double deltaY) {
        for (Selection selection : selections) {
            if (!matchesSelection(selection, deviceId)) continue;
            if ((selection.mask & XI_RAW_MOTION_MASK) == 0) continue;
            selection.client.sendEvent(
                    new XIRawMotionNotify(deviceId, MAJOR_OPCODE, deltaX, deltaY));
        }
    }

    public void emitRawButton(int deviceId, int buttonNumber, boolean pressed) {
        long eventMask = pressed ? XI_RAW_BUTTON_PRESS_MASK : XI_RAW_BUTTON_RELEASE_MASK;

        for (Selection selection : selections) {
            if (!matchesSelection(selection, deviceId)) continue;
            if ((selection.mask & eventMask) == 0) continue;

            if (pressed) {
                selection.client.sendEvent(
                        new XIRawButtonPressNotify(deviceId, MAJOR_OPCODE, buttonNumber));
            }
            else {
                selection.client.sendEvent(
                        new XIRawButtonReleaseNotify(deviceId, MAJOR_OPCODE, buttonNumber));
            }
        }
    }
}
