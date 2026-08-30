package com.winlator.cmod.xserver.extensions;

import android.util.SparseArray;

import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xserver.Pixmap;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.XResource;
import com.winlator.cmod.xserver.XResourceManager;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.errors.BadFence;
import com.winlator.cmod.xserver.errors.BadIdChoice;
import com.winlator.cmod.xserver.errors.BadImplementation;
import com.winlator.cmod.xserver.errors.BadMatch;
import com.winlator.cmod.xserver.errors.XRequestError;

import java.io.IOException;

public class SyncExtension implements Extension, XResourceManager.OnResourceLifecycleListener {

    public static final byte MAJOR_OPCODE = -104;

    private final Object fenceLock = new Object();
    private final SparseArray<SyncFence> fences = new SparseArray<>();

    private XServer xServer;

    private static abstract class ClientOpcodes {
        private static final byte CREATE_FENCE  = 14;
        private static final byte TRIGGER_FENCE = 15;
        private static final byte RESET_FENCE   = 16;
        private static final byte DESTROY_FENCE = 17;
        private static final byte AWAIT_FENCE   = 19;
    }

    private class SyncFence {
        int     fenceId;
        int     drawableId;
        boolean triggered;
    }

    public SyncExtension(XServer xServer) {
        this.xServer = xServer;
        this.xServer.pixmapManager.addOnResourceLifecycleListener(this);
        this.xServer.windowManager.addOnResourceLifecycleListener(this);
    }

    @Override public String getName()       { return "SYNC"; }
    @Override public byte getMajorOpcode()  { return MAJOR_OPCODE; }
    @Override public byte getFirstErrorId() { return Byte.MIN_VALUE; }
    @Override public byte getFirstEventId() { return 0; }

    public void setTriggered(int id) {
        synchronized (fenceLock) {
            if (fences.indexOfKey(id) >= 0) {
                fences.get(id).triggered = true;
                fenceLock.notifyAll();
            }
        }
    }

    private void createFence(XClient client, XInputStream in,
                             XOutputStream out) throws IOException, XRequestError {
        synchronized (fenceLock) {
            int drawableId         = in.readInt();
            int id                 = in.readInt();
            if (fences.indexOfKey(id) >= 0) throw new BadIdChoice(id);
            boolean initiallyTriggered = in.readByte() == 1;
            in.skip(3);
            SyncFence fence  = new SyncFence();
            fence.fenceId    = id;
            fence.drawableId = drawableId;
            fence.triggered  = initiallyTriggered;
            fences.put(id, fence);
        }
    }

    private void triggerFence(XClient client, XInputStream in,
                              XOutputStream out) throws IOException, XRequestError {
        synchronized (fenceLock) {
            int id = in.readInt();
            if (fences.indexOfKey(id) < 0) throw new BadFence(id);
            fences.get(id).triggered = true;
            fenceLock.notifyAll();
        }
    }

    private void resetFence(XClient client, XInputStream in,
                            XOutputStream out) throws IOException, XRequestError {
        synchronized (fenceLock) {
            int id = in.readInt();
            if (fences.indexOfKey(id) < 0) throw new BadFence(id);
            SyncFence fence = fences.get(id);
            if (!fence.triggered) throw new BadMatch();
            fence.triggered = false;
        }
    }

    private void destroyFence(XClient client, XInputStream in,
                              XOutputStream out) throws IOException, XRequestError {
        synchronized (fenceLock) {
            int id = in.readInt();
            if (fences.indexOfKey(id) < 0) throw new BadFence(id);
            fences.delete(id);
        }
    }

    private void awaitFence(XClient client, XInputStream in,
                            XOutputStream out) throws IOException, XRequestError {
        int remainingBytes = client.getRemainingRequestLength();
        if (remainingBytes < 0) remainingBytes = 0;
        int   numIds = remainingBytes / 4;
        int[] ids    = new int[numIds];
        for (int i = 0; i < numIds; i++) ids[i] = in.readInt();
        int leftover = remainingBytes - (numIds * 4);
        if (leftover > 0) in.skip(leftover);
        if (ids.length == 0) return;

        synchronized (fenceLock) {
            while (true) {
                boolean anyTriggered = false;
                for (int id : ids) {
                    if (fences.indexOfKey(id) < 0) throw new BadFence(id);
                    if (fences.get(id).triggered) { anyTriggered = true; break; }
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
    public void onFreeResource(XResource resource) {
        synchronized (fenceLock) {
            int targetId = -1;
            if      (resource instanceof Pixmap) targetId = ((Pixmap) resource).id;
            else if (resource instanceof Window) targetId = ((Window) resource).id;
            if (targetId < 0) return;
            for (int i = fences.size() - 1; i >= 0; i--) {
                SyncFence fence = fences.valueAt(i);
                if (fence.drawableId == targetId)
                    fences.remove(fence.fenceId);
            }
        }
    }

    @Override
    public void handleRequest(XClient client, XInputStream in,
                              XOutputStream out) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        switch (opcode) {
            case ClientOpcodes.CREATE_FENCE:
                createFence(client, in, out);
                break;
            case ClientOpcodes.TRIGGER_FENCE:
                triggerFence(client, in, out);
                break;
            case ClientOpcodes.RESET_FENCE:
                resetFence(client, in, out);
                break;
            case ClientOpcodes.DESTROY_FENCE:
                destroyFence(client, in, out);
                break;
            case ClientOpcodes.AWAIT_FENCE:
                awaitFence(client, in, out);
                break;
            default:
                throw new BadImplementation();
        }
    }
}
