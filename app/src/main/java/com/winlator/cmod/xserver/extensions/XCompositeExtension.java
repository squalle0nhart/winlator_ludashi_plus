package com.winlator.cmod.xserver.extensions;

import static com.winlator.cmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.XResource;
import com.winlator.cmod.xserver.XResourceManager;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.errors.BadAccess;
import com.winlator.cmod.xserver.errors.BadImplementation;
import com.winlator.cmod.xserver.errors.BadMatch;
import com.winlator.cmod.xserver.errors.BadValue;
import com.winlator.cmod.xserver.errors.BadWindow;
import com.winlator.cmod.xserver.errors.XRequestError;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XCompositeExtension implements Extension, XResourceManager.OnResourceLifecycleListener {
    public static final byte MAJOR_OPCODE = -108;

    private static final int UPDATE_AUTOMATIC = 0;
    private static final int UPDATE_MANUAL = 1;

    private static abstract class ClientOpcodes {
        private static final byte QUERY_VERSION = 0;
        private static final byte REDIRECT_WINDOW = 1;
        private static final byte UNREDIRECT_WINDOW = 3;
    }

    private static class Redirect {
        final XClient client;
        final Window window;
        final int updateType;

        Redirect(XClient client, Window window, int updateType) {
            this.client = client;
            this.window = window;
            this.updateType = updateType;
        }
    }

    private final XServer xServer;
    private final List<Redirect> redirects = new CopyOnWriteArrayList<>();

    public XCompositeExtension(XServer xServer) {
        this.xServer = xServer;
        xServer.windowManager.addOnResourceLifecycleListener(this);
    }

    @Override
    public String getName() {
        return "Composite";
    }

    @Override
    public byte getMajorOpcode() {
        return MAJOR_OPCODE;
    }

    @Override
    public byte getFirstErrorId() {
        return 0;
    }

    @Override
    public byte getFirstEventId() {
        return 0;
    }

    private static void queryVersion(XClient client, XInputStream inputStream,
                                     XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(0);
            outputStream.writeInt(1);
            outputStream.writePad(16);
        }
    }

    private void redirectWindow(XClient client, XInputStream inputStream)
            throws XRequestError {
        int windowId = inputStream.readInt();
        int updateType = inputStream.readUnsignedByte();
        inputStream.skip(3);

        if (updateType != UPDATE_AUTOMATIC && updateType != UPDATE_MANUAL) {
            throw new BadValue(updateType);
        }

        Window window = xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);
        if (window == xServer.windowManager.rootWindow || !window.isInputOutput()) {
            throw new BadMatch();
        }

        for (Redirect redirect : redirects) {
            if (redirect.client == client
                    && redirect.window == window
                    && redirect.updateType == updateType) {
                return;
            }

            if (updateType == UPDATE_MANUAL
                    && redirect.window == window
                    && redirect.updateType == UPDATE_MANUAL) {
                throw new BadAccess();
            }
        }

        redirects.add(new Redirect(client, window, updateType));
    }

    private void unredirectWindow(XClient client, XInputStream inputStream)
            throws XRequestError {
        int windowId = inputStream.readInt();
        int updateType = inputStream.readUnsignedByte();
        inputStream.skip(3);

        if (updateType != UPDATE_AUTOMATIC && updateType != UPDATE_MANUAL) {
            throw new BadValue(updateType);
        }

        Window window = xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        boolean removed = redirects.removeIf(redirect ->
                redirect.client == client
                        && redirect.window == window
                        && redirect.updateType == updateType);
        if (!removed) throw new BadValue(windowId);
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream,
                              XOutputStream outputStream)
            throws IOException, XRequestError {
        switch (client.getRequestData()) {
            case ClientOpcodes.QUERY_VERSION:
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.REDIRECT_WINDOW:
                redirectWindow(client, inputStream);
                break;
            case ClientOpcodes.UNREDIRECT_WINDOW:
                unredirectWindow(client, inputStream);
                break;
            default:
                throw new BadImplementation();
        }
    }

    public void freeClientResources(XClient client) {
        redirects.removeIf(redirect -> redirect.client == client);
    }

    @Override
    public void onFreeResource(XResource resource) {
        if (resource instanceof Window) {
            redirects.removeIf(redirect -> redirect.window == resource);
        }
    }
}
