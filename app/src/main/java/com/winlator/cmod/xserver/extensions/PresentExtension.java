package com.winlator.cmod.xserver.extensions;

import static com.winlator.cmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.util.SparseArray;

import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.Texture;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.renderer.ASurfaceRenderer;
import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pixmap;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.errors.BadImplementation;
import com.winlator.cmod.xserver.errors.BadMatch;
import com.winlator.cmod.xserver.errors.BadPixmap;
import com.winlator.cmod.xserver.errors.BadWindow;
import com.winlator.cmod.xserver.errors.XRequestError;
import com.winlator.cmod.xserver.events.PresentCompleteNotify;
import com.winlator.cmod.xserver.events.PresentIdleNotify;

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PresentExtension implements Extension {
    public static final byte MAJOR_OPCODE = -103;
    public enum Kind { PIXMAP, MSC_NOTIFY }
    public enum Mode { COPY, FLIP, SKIP }

    private final SparseArray<Event> events = new SparseArray<>();
    private SyncExtension syncExtension;

    private static final long FIRE_EARLY_NS = 700_000L;
    private final ScheduledThreadPoolExecutor idleScheduler = createIdleScheduler();

    private static ScheduledThreadPoolExecutor createIdleScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "PresentPacer-Timer");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setKeepAliveTime(1, TimeUnit.SECONDS);
        scheduler.allowCoreThreadTimeOut(true);
        return scheduler;
    }

    private static class WindowTiming { long nextIdleNs = 0; }
    private final java.util.concurrent.ConcurrentHashMap<Integer, WindowTiming> windowTimings =
        new java.util.concurrent.ConcurrentHashMap<>();

    private void scheduleIdleNotify(Window window, Pixmap pixmap, int serial,
                                     int idleFence, int targetFps) {
        if (targetFps <= 0) {
            sendIdleNotify(window, pixmap, serial, idleFence);
            return;
        }

        final long frameNs = 1_000_000_000L / targetFps;
        long now = System.nanoTime();

        WindowTiming wt = windowTimings.computeIfAbsent(window.id, k -> new WindowTiming());
        final long fireTime;
        synchronized (wt) {
            if (wt.nextIdleNs <= now - frameNs) {
                wt.nextIdleNs = now + frameNs;
            } else {
                wt.nextIdleNs += frameNs;
            }
            fireTime = wt.nextIdleNs - FIRE_EARLY_NS;
        }

        long delayNs = Math.max(0L, fireTime - System.nanoTime());
        idleScheduler.schedule(
                () -> sendIdleNotify(window, pixmap, serial, idleFence),
                delayNs,
                TimeUnit.NANOSECONDS);
    }

    private static abstract class ClientOpcodes {
        static final byte QUERY_VERSION = 0;
        static final byte PRESENT_PIXMAP = 1;
        static final byte SELECT_INPUT = 3;
    }

    private static class Event {
        Window window;
        XClient client;
        int id;
        Bitmask mask;
    }

    @Override public String getName() { return "Present"; }
    @Override public byte getMajorOpcode() { return MAJOR_OPCODE; }
    @Override public byte getFirstErrorId() { return 0; }
    @Override public byte getFirstEventId() { return 0; }

    private void sendIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence) {
        if (idleFence != 0 && syncExtension != null)
            syncExtension.setTriggered(idleFence);
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event e = events.valueAt(i);
                if (e.window == window && e.mask.isSet(PresentIdleNotify.getEventMask()))
                    e.client.sendEvent(new PresentIdleNotify(e.id, window, pixmap, serial, idleFence));
            }
        }
    }

    private void sendCompleteNotify(Window window, int serial, Kind kind, Mode mode, long ust, long msc) {
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event e = events.valueAt(i);
                if (e.window == window && e.mask.isSet(PresentCompleteNotify.getEventMask()))
                    e.client.sendEvent(new PresentCompleteNotify(e.id, window, serial, kind, mode, ust, msc));
            }
        }
    }

    private static void queryVersion(XClient client, XInputStream in, XOutputStream out) throws IOException {
        in.skip(8);
        try (XStreamLock lock = out.lock()) {
            out.writeByte(RESPONSE_CODE_SUCCESS);
            out.writeByte((byte)0);
            out.writeShort(client.getSequenceNumber());
            out.writeInt(0);
            out.writeInt(1);
            out.writeInt(0);
            out.writePad(16);
        }
    }

    private void presentPixmap(XClient client, XInputStream in, XOutputStream out)
            throws IOException, XRequestError {
        int windowId = in.readInt();
        int pixmapId = in.readInt();
        int serial   = in.readInt();
        in.skip(8);
        short xOff = in.readShort();
        short yOff = in.readShort();
        in.skip(8);
        int idleFence = in.readInt();
        in.skip(client.getRemainingRequestLength());

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
        if (pixmap == null) throw new BadPixmap(pixmapId);

        Drawable content = window.getContent();
        int contentDepth = content.visual.depth;
        int pixmapDepth = pixmap.drawable.visual.depth;
        boolean depthCompat = (contentDepth == pixmapDepth) ||
            ((contentDepth == 24 || contentDepth == 32) && (pixmapDepth == 24 || pixmapDepth == 32));
        if (!depthCompat) throw new BadMatch();

        HostRenderer xr = client.xServer.getRenderer();
        VulkanRenderer renderer = xr instanceof VulkanRenderer ? (VulkanRenderer) xr : null;
        int targetFps = xr != null ? xr.getFpsLimit() : 0;

        long ust = System.nanoTime() / 1000;
        long msc = ust / (targetFps > 0 ? (1_000_000L / targetFps) : (1_000_000L / 60));

        synchronized (content.renderLock) {
            if (xr instanceof ASurfaceRenderer) {
                ASurfaceRenderer asr = (ASurfaceRenderer) xr;
                if (window.attributes.isMapped()
                        && pixmap.drawable.getTexture() instanceof GPUImage
                        && ((GPUImage) pixmap.drawable.getTexture()).getHardwareBufferPtr() != 0) {
                    content.setTexture(pixmap.drawable.getTexture());
                    content.setDirectScanout(true);
                    sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.FLIP, ust, msc);
                    asr.presentWindow(window, content);
                } else {
                    content.copyArea((short) 0, (short) 0, xOff, yOff,
                        pixmap.drawable.width, pixmap.drawable.height, pixmap.drawable);
                    sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                }
                scheduleIdleNotify(window, pixmap, serial, idleFence, targetFps);
            } else if (renderer != null && window.attributes.isMapped()
                    && pixmap.drawable.getTexture() instanceof GPUImage
                    && ((GPUImage) pixmap.drawable.getTexture()).getHardwareBufferPtr() != 0) {
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                renderer.onUpdateWindowContentDirect(window, pixmap.drawable, xOff, yOff);
                scheduleIdleNotify(window, pixmap, serial, idleFence, targetFps);
            } else if (xr instanceof GLRenderer
                    && ((GLRenderer) xr).isNativeMode()
                    && pixmap.drawable.getTexture() instanceof GPUImage
                    && ((GPUImage) pixmap.drawable.getTexture()).getHardwareBufferPtr() != 0) {
                content.setTexture(pixmap.drawable.getTexture());
                content.setDirectScanout(true);
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.FLIP, ust, msc);
                ((GLRenderer) xr).presentScanout(window, content);
                scheduleIdleNotify(window, pixmap, serial, idleFence, targetFps);
            } else {
                content.copyArea((short)0, (short)0, xOff, yOff,
                    pixmap.drawable.width, pixmap.drawable.height, pixmap.drawable);
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                scheduleIdleNotify(window, pixmap, serial, idleFence, targetFps);
            }
        }
    }

    private void selectInput(XClient client, XInputStream in, XOutputStream out)
            throws IOException, XRequestError {
        int eventId  = in.readInt();
        int windowId = in.readInt();
        Bitmask mask = new Bitmask(in.readInt());

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        if (GPUImage.isSupported() && !mask.isEmpty()) {
            Drawable content = window.getContent();
            final Texture oldTexture = content.getTexture();
            if (oldTexture != null && !(oldTexture instanceof GPUImage)) {
                HostRenderer r = client.xServer.getRenderer();
                if (r != null) r.getXServerView().queueEvent(oldTexture::destroy);
            }
            if (!(content.getTexture() instanceof GPUImage))
                content.setTexture(new GPUImage(content.width, content.height));
        }

        synchronized (events) {
            Event event = events.get(eventId);
            if (event != null) {
                if (event.window != window || event.client != client) throw new BadMatch();
                if (!mask.isEmpty()) event.mask = mask;
                else events.remove(eventId);
            } else {
                event = new Event();
                event.id     = eventId;
                event.window = window;
                event.client = client;
                event.mask   = mask;
                events.put(eventId, event);
            }
        }
    }

    @Override
    public void handleRequest(XClient client, XInputStream in, XOutputStream out)
            throws IOException, XRequestError {
        int opcode = client.getRequestData();
        if (syncExtension == null)
            syncExtension = client.xServer.getExtension(SyncExtension.MAJOR_OPCODE);

        switch (opcode) {
            case ClientOpcodes.QUERY_VERSION:
                queryVersion(client, in, out);
                break;
            case ClientOpcodes.PRESENT_PIXMAP:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    presentPixmap(client, in, out);
                }
                break;
            case ClientOpcodes.SELECT_INPUT:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    selectInput(client, in, out);
                }
                break;
            default:
                throw new BadImplementation();
        }
    }
}
