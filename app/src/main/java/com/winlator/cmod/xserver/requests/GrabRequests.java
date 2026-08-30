package com.winlator.cmod.xserver.requests;

import static com.winlator.cmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.graphics.Rect;

import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.errors.BadWindow;
import com.winlator.cmod.xserver.errors.XRequestError;

import java.io.IOException;

public abstract class GrabRequests {
    private enum Status {SUCCESS, ALREADY_GRABBED, INVALID_TIME, NOT_VIEWABLE, FROZEN}

    public static void grabPointer(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        if (client.xServer.isRelativeMouseMovement()) {
            client.skipRequest();
            try (XStreamLock lock = outputStream.lock()) {
                outputStream.writeByte(RESPONSE_CODE_SUCCESS);
                outputStream.writeByte((byte)Status.ALREADY_GRABBED.ordinal());
                outputStream.writeShort(client.getSequenceNumber());
                outputStream.writeInt(0);
                outputStream.writePad(24);
            }
            return;
        }

        boolean ownerEvents = client.getRequestData() == 1;
        int windowId = inputStream.readInt();
        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        Bitmask eventMask = new Bitmask(inputStream.readShort());
        inputStream.skip(2);
        int confineToWindowId = inputStream.readInt();
        inputStream.skip(8);

        Window confineToWindow = null;
        if (confineToWindowId != 0) {
            confineToWindow = client.xServer.windowManager.getWindow(confineToWindowId);
            if (confineToWindow == null) throw new BadWindow(confineToWindowId);
        }

        boolean confinementViewable = true;
        if (confineToWindow != null) {
            Rect bounds = confineToWindow.getAbsoluteBounds();
            Rect screen = new Rect(0, 0, client.xServer.screenInfo.width, client.xServer.screenInfo.height);
            confinementViewable = confineToWindow.getMapState() == Window.MapState.VIEWABLE
                    && Rect.intersects(bounds, screen);
        }

        Status status;
        if (client.xServer.grabManager.getWindow() != null && client.xServer.grabManager.getClient() != client) {
            status = Status.ALREADY_GRABBED;
        }
        else if (window.getMapState() != Window.MapState.VIEWABLE || !confinementViewable) {
            status = Status.NOT_VIEWABLE;
        }
        else {
            status = Status.SUCCESS;
            client.xServer.grabManager.activatePointerGrab(
                    window, ownerEvents, eventMask, client, confineToWindow);
        }

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)status.ordinal());
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writePad(24);
        }
    }

    public static void ungrabPointer(XClient client, XInputStream inputStream, XOutputStream outputStream) {
        inputStream.skip(4);
        client.xServer.grabManager.deactivatePointerGrab();
    }
}
