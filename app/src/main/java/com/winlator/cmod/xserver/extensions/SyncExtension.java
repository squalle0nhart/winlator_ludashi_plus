package com.winlator.cmod.xserver.extensions;

import android.util.SparseBooleanArray;

import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.errors.BadFence;
import com.winlator.cmod.xserver.errors.BadIdChoice;
import com.winlator.cmod.xserver.errors.BadImplementation;
import com.winlator.cmod.xserver.errors.XRequestError;

import java.io.IOException;

public class SyncExtension implements Extension {
    public static final byte MAJOR_OPCODE = -104;
    private final SparseBooleanArray fences = new SparseBooleanArray();
    private final Object fenceLock = new Object();

    private static abstract class ClientOpcodes {
        private static final byte CREATE_FENCE = 14;
        private static final byte TRIGGER_FENCE = 15;
        private static final byte RESET_FENCE = 16;
        private static final byte DESTROY_FENCE = 17;
        private static final byte AWAIT_FENCE = 19;
    }

    @Override public String getName() { return "SYNC"; }
    @Override public byte getMajorOpcode() { return MAJOR_OPCODE; }
    @Override public byte getFirstErrorId() { return 0; }
    @Override public byte getFirstEventId() { return 0; }

    public void setTriggered(int id) {
        synchronized (fenceLock) {
            if (fences.indexOfKey(id) >= 0) {
                fences.put(id, true);
                fenceLock.notifyAll();
            }
        }
    }

    private void createFence(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int drawableId = inputStream.readInt();
        int id = inputStream.readInt();
        boolean initiallyTriggered = inputStream.readByte() == 1;
        inputStream.skip(3);
        synchronized (fenceLock) {
            if (fences.indexOfKey(id) >= 0) throw new BadIdChoice(id);
            fences.put(id, initiallyTriggered);
        }
    }

    private void triggerFence(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int id = inputStream.readInt();
        synchronized (fenceLock) {
            if (fences.indexOfKey(id) < 0) throw new BadFence(id);
            fences.put(id, true);
            fenceLock.notifyAll();
        }
    }

    private void resetFence(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int id = inputStream.readInt();
        synchronized (fenceLock) {
            if (fences.indexOfKey(id) < 0) throw new BadFence(id);
            fences.put(id, false);
        }
    }

    private void destroyFence(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int id = inputStream.readInt();
        synchronized (fenceLock) {
            if (fences.indexOfKey(id) < 0) throw new BadFence(id);
            fences.delete(id);
        }
    }

    private void awaitFence(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int remainingBytes = client.getRemainingRequestLength();
        if (remainingBytes < 0) remainingBytes = 0;

        int numIds = remainingBytes / 4;
        int[] ids = new int[numIds];
        for (int i = 0; i < numIds; i++) ids[i] = inputStream.readInt();

        int leftover = remainingBytes - (numIds * 4);
        if (leftover > 0) inputStream.skip(leftover);

        if (ids.length == 0) return;

        synchronized (fenceLock) {
            while (true) {
                boolean anyTriggered = false;
                for (int id : ids) {
                    if (fences.indexOfKey(id) < 0) throw new BadFence(id);
                    if (fences.get(id)) { anyTriggered = true; break; }
                }
                if (anyTriggered) break;
                try {
                    fenceLock.wait(8);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        switch (opcode) {
            case ClientOpcodes.CREATE_FENCE:
                createFence(client, inputStream, outputStream);
                break;
            case ClientOpcodes.TRIGGER_FENCE:
                triggerFence(client, inputStream, outputStream);
                break;
            case ClientOpcodes.RESET_FENCE:
                resetFence(client, inputStream, outputStream);
                break;
            case ClientOpcodes.DESTROY_FENCE:
                destroyFence(client, inputStream, outputStream);
                break;
            case ClientOpcodes.AWAIT_FENCE:
                awaitFence(client, inputStream, outputStream);
                break;
            default:
                throw new BadImplementation();
        }
    }
}
