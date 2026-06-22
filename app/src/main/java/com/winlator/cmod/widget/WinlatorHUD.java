package com.winlator.cmod.widget;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.winlator.cmod.core.CPUStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class WinlatorHUD extends View {
    private static final String PREFS    = "winlator_hud";
    private static final String KEY_X    = "hud_x";
    private static final String KEY_Y    = "hud_y";
    private static final String KEY_VIS  = "hud_vis";
    private static final String KEY_SHOW = "hud_show";
    private static final String KEY_SCALE= "hud_scale";
    private static final String KEY_ALPHA= "hud_alpha_int";
    private static final String KEY_VERT = "hud_vertical";

    public static final int SHOW_FPS      = 1;
    public static final int SHOW_GPU      = 1<<1;
    public static final int SHOW_CPU      = 1<<2;
    public static final int SHOW_BATT     = 1<<3;
    public static final int SHOW_RENDERER = 1<<5;
    public static final int SHOW_RAM      = 1<<6;
    private static final int SHOW_DEFAULT = 0x6F;

    private static final int C_BG   = Color.argb(180, 0,   0,   0  );
    private static final int C_WHITE= Color.WHITE;
    private static final int C_GPU  = Color.rgb(0xE0,0x40,0xFB);
    private static final int C_CPU  = Color.rgb(0x00,0xE5,0xFF);
    private static final int C_BATT = Color.rgb(0xFF,0x80,0x00);
    private static final int C_CHG  = Color.rgb(0x40,0xC4,0x40);
    private static final int C_TEMP = Color.rgb(0xEF,0x53,0x50);
    private static final int C_FPS  = Color.rgb(0x76,0xFF,0x03);
    private static final int C_REND = Color.rgb(0xFF,0xEA,0x00);
    private static final int C_RAM  = Color.rgb(0xB0,0xFF,0xB0);
    private static final int C_SEP  = Color.rgb(0x60,0x60,0x60);

    private float TS, TSR, PAD, CORNER;

    private static final int TEXT_FLAGS = Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG;
    private final Paint pBg      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVal     = new Paint(TEXT_FLAGS);
    private final Paint pGpu     = new Paint(TEXT_FLAGS);
    private final Paint pCpu     = new Paint(TEXT_FLAGS);
    private final Paint pBat     = new Paint(TEXT_FLAGS);
    private final Paint pTmp     = new Paint(TEXT_FLAGS);
    private final Paint pFps     = new Paint(TEXT_FLAGS);
    private final Paint pRend    = new Paint(TEXT_FLAGS);
    private final Paint pRam     = new Paint(TEXT_FLAGS);
    private final Paint pSep     = new Paint(TEXT_FLAGS);
    private final Paint pChg     = new Paint(TEXT_FLAGS);

    private final RectF bgRect = new RectF();

    private float wLabelGpu, wLabelCpu, wLabelRam, wLabelPwr, wLabelTmp, wLabelFps, wSep;
    private float wVal100pct, wValFps, wValWatt, wValTemp;

    private float cachedHorizWidth = -1;
    private boolean layoutDirty = true;

    private volatile String strGpu = "N/A", strCpu = "N/A", strRam = "N/A";
    private volatile String strPwr = "N/A", strTmp = "", strFps = "0";
    private volatile String strRend = "Vulkan";   
    private volatile boolean snapCharging = false;

    private volatile float wDynGpu, wDynCpu, wDynRam, wDynPwr, wDynTmp, wDynFps, wDynRend;

    private int lastBgAlpha = -1;

    private final SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final AtomicInteger frameAccum = new AtomicInteger(0);
    private long lastFpsNs = 0;
    private float snapFps = 0;

    private int snapGpu=-1, snapCpu=-1, snapMw=-1, snapTmp=-1, snapPct=-1, snapRam=-1;
    private volatile String rendererLabel = "Vulkan"; 
    private boolean isNative = false;

    private volatile int showMask = SHOW_DEFAULT;
    private float hudAlpha = 1f;
    private volatile boolean userEnabled = false;
    private boolean vertical = false;

    private float touchX, touchY, startX, startY;
    private boolean dragging = false;
    private static final float DRAG_THRESH = 10f;
    private long touchDownMs = 0;

    private boolean redrawScheduled = false;

    private HandlerThread statsThread = null;
    private Handler statsHandler = null;
    private static final long STATS_INTERVAL_MS = 1500L;

    private final Runnable statsRunnable = this::doStats;

    private void doStats() {
        try { readStats(); } catch (Exception ignored) {}
        if (userEnabled && statsHandler != null)
            statsHandler.postDelayed(statsRunnable, STATS_INTERVAL_MS);
        uiHandler.post(this::invalidate);
    }

    private final BatteryManager batteryManager;
    private static final long BATT_REGISTER_INTERVAL_NS = 5_000_000_000L;
    private final IntentFilter batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private Intent cachedBatteryIntent = null;
    private long lastBatteryRegisterNs = 0;
    private static final String[] GPU_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
        "/sys/class/misc/mali0/device/utilisation",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/misc/pvrsrvkm/device/utilisation",
        "/sys/class/devfreq/gpu/load",
    };
    private static final String GPU_BUSY = "/sys/class/kgsl/kgsl-3d0/gpubusy";
    private String gpuPath = null;
    private boolean gpuFailed = false;
    private long prevGpuBusy = 0, prevGpuTotal = 0;
    private boolean cpuFailed = false;
    private boolean battFailed = false;

    private final Runnable redrawRunnable = () -> {
        redrawScheduled = false;
        try {
            snapshot();
            invalidate();
        } catch (Exception ignored) {}
        if (getVisibility() == VISIBLE) scheduleRedraw();
    };

    public WinlatorHUD(Context context) { this(context, null); }

    public WinlatorHUD(Context context, AttributeSet attrs) {
        super(context, attrs);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        float d = context.getResources().getDisplayMetrics().density;
        TS     = 12f * d;
        TSR    = 11f * d;
        PAD    = 6f * d;
        CORNER = 5f * d;
        initPaints();
        detectGpuPathOnce();
        loadPrefs();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void detectGpuPathOnce() {
        for (String p : GPU_PATHS) {
            if (new File(p).canRead()) { gpuPath = p; return; }
        }

        if (new File(GPU_BUSY).canRead()) gpuPath = GPU_BUSY;

    }

    private void initPaints() {
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        pBg.setStyle(Paint.Style.FILL);
        pVal.setTextSize(TS);       pVal.setTypeface(mono);  pVal.setColor(C_WHITE);
        pGpu.setTextSize(TS);       pGpu.setTypeface(mono);  pGpu.setColor(C_GPU);
        pCpu.setTextSize(TS);       pCpu.setTypeface(mono);  pCpu.setColor(C_CPU);
        pBat.setTextSize(TS);       pBat.setTypeface(mono);  pBat.setColor(C_BATT);
        pTmp.setTextSize(TS);       pTmp.setTypeface(mono);  pTmp.setColor(C_TEMP);
        pFps.setTextSize(TS);
        pFps.setTypeface(mono);
        pFps.setColor(C_FPS);
        pRend.setTextSize(TSR);     pRend.setTypeface(mono); pRend.setColor(C_REND);
        pRam.setTextSize(TS);       pRam.setTypeface(mono);  pRam.setColor(C_RAM);
        pSep.setTextSize(TS);       pSep.setTypeface(mono);  pSep.setColor(C_SEP);
        pChg.setTextSize(TS);       pChg.setTypeface(mono);  pChg.setColor(C_CHG);

        wLabelGpu  = pGpu.measureText("GPU ");
        wLabelCpu  = pCpu.measureText("CPU ");
        wLabelRam  = pRam.measureText("RAM ");
        wLabelPwr  = pBat.measureText("PWR ");
        wLabelTmp  = pTmp.measureText("TMP ");
        wLabelFps  = pFps.measureText("FPS ");
        wSep       = pSep.measureText(" | ");
        wVal100pct = pVal.measureText("100%");
        wValFps    = pVal.measureText("999");   
        wValWatt   = pVal.measureText("9.9W");
        wValTemp   = pVal.measureText("99°C");

        wDynGpu  = pVal.measureText(strGpu);
        wDynCpu  = pVal.measureText(strCpu);
        wDynRam  = pVal.measureText(strRam);
        wDynPwr  = pVal.measureText(strPwr);
        wDynTmp  = pVal.measureText(strTmp);
        wDynFps  = pVal.measureText(strFps);   
        wDynRend = pRend.measureText(strRend);
    }

    public void onFrame() {

        if (!rendererActive && !userEnabled) return;
        frameAccum.incrementAndGet();
    }

    public void update() { onFrame(); }

    public void setIsNative(boolean n) {
        isNative = n;
        strRend = (isNative ? "+" : "") + rendererLabel;
        layoutDirty = true;
    }

    private void readStats() {
        if ((showMask & SHOW_GPU) != 0)  readGpu();
        if ((showMask & SHOW_CPU) != 0)  readCpu();
        if ((showMask & SHOW_RAM) != 0)  readRam();
        if ((showMask & SHOW_BATT) != 0) readBattery();
    }

    private void readGpu() {
        if (gpuFailed) return;
        int v = -1;

        if (gpuPath != null) {
            v = gpuPath.equals(GPU_BUSY) ? readGpuBusy() : readPercent(gpuPath);
            if (v < 0) {

                gpuFailed = true;
            }
        } else {
            gpuFailed = true; 
        }

        if (v != snapGpu) {
            snapGpu = v;
            strGpu = v >= 0 ? v + "%" : "N/A";
            wDynGpu = pVal.measureText(strGpu);
        }
    }

    private int readPercent(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l = r.readLine();
            if (l == null) return -1;
            return Math.min(100, Math.max(0, Integer.parseInt(l.trim().replaceAll("[^0-9]", ""))));
        } catch (Exception e) { return -1; }
    }

    private int readGpuBusy() {
        try (BufferedReader r = new BufferedReader(new FileReader(GPU_BUSY))) {
            String l = r.readLine();
            if (l == null) return -1;
            String[] p = l.trim().split("\\s+");
            if (p.length < 2) return -1;
            long busy = Long.parseLong(p[0]), total = Long.parseLong(p[1]);
            long dB = busy - prevGpuBusy, dT = total - prevGpuTotal;
            prevGpuBusy = busy; prevGpuTotal = total;
            return dT > 0 ? (int) Math.min(100, dB * 100L / dT) : 0;
        } catch (Exception e) { return -1; }
    }

    private void readCpu() {
        if (cpuFailed) return;
        try {
            short[] clocks = CPUStatus.getCurrentClockSpeeds();
            long cur = 0, max = 0;
            for (int i = 0; i < clocks.length; i++) {
                cur += clocks[i];
                max += CPUStatus.getMaxClockSpeed(i);
            }
            int v = max > 0 ? (int) Math.min(100, cur * 100L / max) : -1;
            if (v != snapCpu) { snapCpu = v; strCpu = v >= 0 ? v + "%" : "N/A"; wDynCpu = pVal.measureText(strCpu); }
        } catch (Exception e) {
            cpuFailed = true;
            Log.w("WinlatorHUD", "CPU read unavailable");
        }
    }

    private void readRam() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            long total = -1, avail = -1;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:"))     total = parseMeminfoKb(line);
                else if (line.startsWith("MemAvailable:")) { avail = parseMeminfoKb(line); break; }
            }
            int v = (total > 0 && avail >= 0) ? (int)(100L * (total - avail) / total) : -1;
            if (v != snapRam) { snapRam = v; strRam = v >= 0 ? v + "%" : "N/A"; wDynRam = pVal.measureText(strRam); }
        } catch (Exception e) {
            if (snapRam != -1) { snapRam = -1; strRam = "N/A"; }
        }
    }

    private long parseMeminfoKb(String line) {
        try { return Long.parseLong(line.trim().split("\\s+")[1]); }
        catch (Exception e) { return -1; }
    }

    private void readBattery() {
        if (battFailed) return;
        try {

            long now = System.nanoTime();
            if (cachedBatteryIntent == null || now - lastBatteryRegisterNs >= BATT_REGISTER_INTERVAL_NS) {
                cachedBatteryIntent = getContext().registerReceiver(null, batteryIntentFilter);
                lastBatteryRegisterNs = now;
            }
            Intent batt = cachedBatteryIntent;
            if (batt == null) return;

            int temp = batt.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            int tm = temp > 0 ? temp / 10 : -1;
            if (tm != snapTmp) { snapTmp = tm; strTmp = tm > 0 ? tm + "°C" : ""; wDynTmp = pVal.measureText(strTmp); }

            int pct = batt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            snapPct = pct;

            int status = batt.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;

            int mw;
            if (charging) {
                mw = -2;
            } else {
                int mv = batt.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                float amps = getBatteryCurrentAmps();
                mw = (mv > 0 && amps > 0) ? (int)((mv / 1000f) * amps * 1000) : -1;
            }

            if (mw != snapMw) {
                snapMw = mw;
                snapCharging = (mw == -2);
                if (snapCharging)        strPwr = "CHG";
                else if (mw > 0)         strPwr = String.format(Locale.US, "%.1fW", mw / 1000f);
                else                     strPwr = "N/A";
                wDynPwr = pVal.measureText(strPwr);
            }
        } catch (Exception e) {
            battFailed = true;
            Log.w("WinlatorHUD", "Battery read unavailable");
        }
    }

    private float getBatteryCurrentAmps() {
        long raw = 0;
        if (batteryManager != null)
            raw = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        if (raw == 0 || raw == Long.MIN_VALUE)
            raw = readSysFsLong("/sys/class/power_supply/battery/current_now");
        if (raw == 0 || raw == Long.MIN_VALUE)
            raw = readSysFsLong("/sys/class/power_supply/bms/current_now");
        if (raw == 0 || raw == Long.MIN_VALUE) return -1f;
        raw = Math.abs(raw);

        return raw < 20000 ? raw / 1000f : raw / 1000000f;
    }

    private long readSysFsLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l = r.readLine();
            return l != null ? Long.parseLong(l.trim()) : 0;
        } catch (Exception e) { return 0; }
    }

    private void snapshot() {
        long now = System.nanoTime();
        if (lastFpsNs == 0) lastFpsNs = now;
        long dt = now - lastFpsNs;
        if (dt >= 350_000_000L) {
            int f = frameAccum.getAndSet(0);
            snapFps = f * 1_000_000_000f / dt;
            lastFpsNs = now;
            String s = String.format(Locale.US, "%.0f", snapFps);
            if (!s.equals(strFps)) { strFps = s; wDynFps = pVal.measureText(strFps); }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (getVisibility() != VISIBLE) return;
        try {
            int targetAlpha = (int)(180 * hudAlpha);
            if (targetAlpha != lastBgAlpha) {
                pBg.setAlpha(targetAlpha);
                lastBgAlpha = targetAlpha;
            }
            if (vertical) drawVertical(c);
            else          drawHorizontal(c);
        } catch (Exception e) {}
    }

    private void drawHorizontal(Canvas c) {
        float x = PAD;
        float rowH = TS + PAD * 2;
        float baseline = PAD + TS;

        bgRect.set(0, 0, getCachedHorizWidth(), rowH);
        c.drawRoundRect(bgRect, CORNER, CORNER, pBg);

        if ((showMask & SHOW_RENDERER) != 0) {
            c.drawText(strRend, x, baseline, pRend);
            x += wDynRend;
            x += drawSep(c, x, baseline);
        }
        if ((showMask & SHOW_GPU) != 0) {
            c.drawText("GPU ", x, baseline, pGpu); x += wLabelGpu;
            c.drawText(strGpu, x, baseline, pVal); x += wDynGpu;
            x += drawSep(c, x, baseline);
        }
        if ((showMask & SHOW_CPU) != 0) {
            c.drawText("CPU ", x, baseline, pCpu); x += wLabelCpu;
            c.drawText(strCpu, x, baseline, pVal); x += wDynCpu;
            x += drawSep(c, x, baseline);
        }
        if ((showMask & SHOW_RAM) != 0) {
            c.drawText("RAM ", x, baseline, pRam); x += wLabelRam;
            c.drawText(strRam, x, baseline, pVal); x += wDynRam;
            x += drawSep(c, x, baseline);
        }
        if ((showMask & SHOW_BATT) != 0) {
            c.drawText("PWR ", x, baseline, pBat); x += wLabelPwr;
            c.drawText(strPwr, x, baseline, snapCharging ? pChg : pVal);
            x += wDynPwr;
            if (!strTmp.isEmpty()) {
                x += drawSep(c, x, baseline);
                c.drawText("TMP ", x, baseline, pTmp); x += wLabelTmp;
                c.drawText(strTmp, x, baseline, pVal); x += wDynTmp;
            }
            x += drawSep(c, x, baseline);
        }
        if ((showMask & SHOW_FPS) != 0) {
            c.drawText("FPS ", x, baseline, pFps);  x += wLabelFps;
            c.drawText(strFps, x, baseline, pVal);  
        }
    }

    private void drawVertical(Canvas c) {
        float lineH = TS + PAD * 2;
        float rows  = countVerticalRows();
        float w     = measureVertical();
        float h     = rows * lineH + PAD;

        bgRect.set(0, 0, w, h);
        c.drawRoundRect(bgRect, CORNER, CORNER, pBg);

        float y = PAD;
        if ((showMask & SHOW_RENDERER) != 0) {
            c.drawText(strRend, PAD, y + TS, pRend); y += lineH;
        }
        if ((showMask & SHOW_GPU) != 0) {
            c.drawText("GPU ", PAD, y + TS, pGpu);
            c.drawText(strGpu, PAD + wLabelGpu, y + TS, pVal); y += lineH;
        }
        if ((showMask & SHOW_CPU) != 0) {
            c.drawText("CPU ", PAD, y + TS, pCpu);
            c.drawText(strCpu, PAD + wLabelCpu, y + TS, pVal); y += lineH;
        }
        if ((showMask & SHOW_RAM) != 0) {
            c.drawText("RAM ", PAD, y + TS, pRam);
            c.drawText(strRam, PAD + wLabelRam, y + TS, pVal); y += lineH;
        }
        if ((showMask & SHOW_BATT) != 0) {
            c.drawText("PWR ", PAD, y + TS, pBat);
            c.drawText(strPwr, PAD + wLabelPwr, y + TS, snapCharging ? pChg : pVal);
            y += lineH;
            if (!strTmp.isEmpty()) {
                c.drawText("TMP ", PAD, y + TS, pTmp);
                c.drawText(strTmp, PAD + wLabelTmp, y + TS, pVal); y += lineH;
            }
        }
        if ((showMask & SHOW_FPS) != 0) {
            c.drawText("FPS ", PAD, y + TS, pFps);
            c.drawText(strFps, PAD + wLabelFps, y + TS, pVal);  
        }
    }

    private float getCachedHorizWidth() {
        if (layoutDirty || cachedHorizWidth < 0) {
            cachedHorizWidth = measureHorizontal();
            layoutDirty = false;
        }
        return cachedHorizWidth;
    }

    private float drawSep(Canvas c, float x, float baseline) {
        c.drawText(" | ", x, baseline, pSep);
        return wSep;
    }

    private float measureHorizontal() {
        float w = PAD;
        if ((showMask & SHOW_RENDERER) != 0) w += wDynRend + wSep;
        if ((showMask & SHOW_GPU)      != 0) w += wLabelGpu + wVal100pct + wSep;
        if ((showMask & SHOW_CPU)      != 0) w += wLabelCpu + wVal100pct + wSep;
        if ((showMask & SHOW_RAM)      != 0) w += wLabelRam + wVal100pct + wSep;
        if ((showMask & SHOW_BATT)     != 0) w += wLabelPwr + wValWatt + wSep + wLabelTmp + wValTemp + wSep;
        if ((showMask & SHOW_FPS)      != 0) w += wLabelFps + wValFps;
        return w + PAD;
    }

    private float measureVertical() {
        float w = PAD * 2;
        if ((showMask & SHOW_RENDERER) != 0) w = Math.max(w, PAD * 2 + wDynRend);
        if ((showMask & SHOW_GPU)      != 0) w = Math.max(w, PAD * 2 + wLabelGpu + wVal100pct);
        if ((showMask & SHOW_CPU)      != 0) w = Math.max(w, PAD * 2 + wLabelCpu + wVal100pct);
        if ((showMask & SHOW_RAM)      != 0) w = Math.max(w, PAD * 2 + wLabelRam + wVal100pct);
        if ((showMask & SHOW_BATT)     != 0) w = Math.max(w, PAD * 2 + wLabelPwr + wValWatt);
        if ((showMask & SHOW_FPS)      != 0) w = Math.max(w, PAD * 2 + wLabelFps + wValFps);
        return w;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        float lineH = TS + PAD * 2;
        float w = vertical ? measureVertical() : measureHorizontal();
        float h = vertical ? (countVerticalRows() * lineH + PAD) : lineH;
        setMeasuredDimension((int) Math.ceil(w), (int) Math.ceil(h));
    }

    private float countVerticalRows() {
        float r = 0;
        if ((showMask & SHOW_RENDERER) != 0) r++;
        if ((showMask & SHOW_GPU)      != 0) r++;
        if ((showMask & SHOW_CPU)      != 0) r++;
        if ((showMask & SHOW_RAM)      != 0) r++;
        if ((showMask & SHOW_BATT)     != 0) { r++; if (snapTmp > 0) r++; }
        if ((showMask & SHOW_FPS)      != 0) r++;
        return Math.max(1, r);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (e.getPointerCount() > 1) return true;
                touchX = e.getRawX(); touchY = e.getRawY();
                startX = getX();      startY = getY();
                dragging = false;
                touchDownMs = System.currentTimeMillis();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - touchX, dy = e.getRawY() - touchY;
                if (!dragging && Math.hypot(dx, dy) > DRAG_THRESH) dragging = true;
                if (dragging) { setX(startX + dx); setY(startY + dy); }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false; touchDownMs = 0; return true;
            case MotionEvent.ACTION_UP:
                if (e.getPointerCount() > 1) { dragging = false; return true; }
                if (dragging) {
                    savePosition();
                } else if (touchDownMs > 0 && System.currentTimeMillis() - touchDownMs < 300) {
                    vertical = !vertical;
                    prefs.edit().putBoolean(KEY_VERT, vertical).apply();
                    try { requestLayout(); invalidate(); } catch (Exception ignored) {}
                    uiHandler.postDelayed(this::ensureVisible, 250);
                }
                dragging = false;
                return true;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (userEnabled) {
            uiHandler.removeCallbacks(redrawRunnable);
            redrawScheduled = false;
            setVisibility(VISIBLE);
            scheduleRedraw();
        }
    }

    private void startStatsThread() {
        if (statsThread == null) {
            statsThread = new HandlerThread("WinlatorHUD-stats", Process.THREAD_PRIORITY_BACKGROUND);
            statsThread.start();
            statsHandler = new Handler(statsThread.getLooper());
        }
        statsHandler.removeCallbacks(statsRunnable);
        statsHandler.post(statsRunnable);
    }

    private void stopStatsThread() {
        if (statsHandler != null) statsHandler.removeCallbacks(statsRunnable);
        if (statsThread != null) {
            statsThread.quitSafely();
            statsThread = null;
            statsHandler = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        uiHandler.removeCallbacks(redrawRunnable);
        stopStatsThread();
        redrawScheduled = false;
    }

    private void ensureVisible() {

        if (userEnabled) {
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            scheduleRedraw();
        }
    }

    private void savePosition() {
        prefs.edit().putFloat(KEY_X, getX()).putFloat(KEY_Y, getY()).apply();
    }

    private void scheduleRedraw() {
        if (!redrawScheduled) {
            redrawScheduled = true;
            uiHandler.postDelayed(redrawRunnable, 400);
        }
    }

    @Override
    protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);

        if (vis == VISIBLE && userEnabled) {
            scheduleRedraw();
        } else {
            uiHandler.removeCallbacks(redrawRunnable);
            redrawScheduled = false;
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);

        if (visibility == VISIBLE && userEnabled) {
            uiHandler.removeCallbacks(redrawRunnable);
            redrawScheduled = false;
            uiHandler.postDelayed(this::ensureVisible, 150);
        }
    }

    private void loadPrefs() {
        showMask = prefs.getInt(KEY_SHOW, SHOW_DEFAULT);
        hudAlpha = prefs.getInt(KEY_ALPHA, 100) / 100f;
        vertical = prefs.getBoolean(KEY_VERT, false);
        float scale = prefs.getFloat(KEY_SCALE, 1f);
        setScaleX(scale); setScaleY(scale);
        setX(prefs.getFloat(KEY_X, 16f));
        setY(prefs.getFloat(KEY_Y, 16f));
        userEnabled = false;
        setVisibility(GONE);
    }

    private volatile boolean rendererActive = false;

    public boolean hasSavedPref()   { return prefs.contains(KEY_VIS); }
    public boolean isSavedVisible() { return prefs.getBoolean(KEY_VIS, false); }

    public boolean isUserEnabled()  { return userEnabled; }

    public void enableByUser() {
        userEnabled = true;

        rendererActive = true;
        prefs.edit().putBoolean(KEY_VIS, true).apply();
        uiHandler.removeCallbacks(redrawRunnable);
        redrawScheduled = false;
        startStatsThread();
        setVisibility(VISIBLE);
        scheduleRedraw();
    }

    public void disableByUser() { disableByUser(true); }

    public void disableByUser(boolean savePrefs) {
        userEnabled = false;
        stopStatsThread();
        if (savePrefs) prefs.edit().putBoolean(KEY_VIS, false).apply();
        uiHandler.removeCallbacks(redrawRunnable);
        redrawScheduled = false;
        setVisibility(GONE);
    }

    public void resetFromContainer() {
        uiHandler.post(() -> {
            uiHandler.removeCallbacks(redrawRunnable);
            redrawScheduled = false;
            frameAccum.set(0); snapFps = 0; lastFpsNs = 0;

            strRend = (isNative ? "+" : "") + rendererLabel;
            wDynRend = pRend.measureText(strRend);
            layoutDirty = true;
            if (userEnabled) { setVisibility(VISIBLE); scheduleRedraw(); startStatsThread(); }
            else              { setVisibility(GONE); stopStatsThread(); }
        });
    }

    public void onRendererDetected(String name) {
        rendererActive = true;
        if (name != null && !name.isEmpty()) rendererLabel = name;
        uiHandler.post(() -> {
            strRend = (isNative ? "+" : "") + rendererLabel;
            wDynRend = pRend.measureText(strRend);
            layoutDirty = true;
            if (userEnabled) {

                startStatsThread();
                setVisibility(VISIBLE);
                scheduleRedraw();
            }
        });
    }

    public void onRendererGone() {

        uiHandler.postDelayed(() -> {
            if (rendererActive) return; 

            frameAccum.set(0); snapFps = 0; lastFpsNs = 0;
            if (!"0".equals(strFps)) { strFps = "0"; wDynFps = pVal.measureText("0"); }
            if (userEnabled) {

                invalidate();
                return;
            }
            uiHandler.removeCallbacks(redrawRunnable);
            redrawScheduled = false;
            stopStatsThread();
            setVisibility(GONE);
        }, 400);

        rendererActive = false;
    }

    public void setRenderer(String name) {
        if (name != null && !name.isEmpty()) {
            rendererLabel = name;
            uiHandler.post(() -> {
                strRend = (isNative ? "+" : "") + rendererLabel;
                wDynRend = pRend.measureText(strRend);
                layoutDirty = true;
                rendererActive = true;
                if (userEnabled) {

                    startStatsThread();
                    if (getVisibility() != VISIBLE) {
                        setVisibility(VISIBLE);
                        scheduleRedraw();
                    } else {
                        invalidate();
                    }
                }
            });
        }
    }

    public void setGpuName(String name) {}

    public void toggleElement(int idx, boolean on) {
        int bit = idxToMask(idx);
        if (bit == 0) return;
        if (on) showMask |= bit; else showMask &= ~bit;
        prefs.edit().putInt(KEY_SHOW, showMask).apply();
        layoutDirty = true;
        try { requestLayout(); invalidate(); } catch (Exception ignored) {}
    }

    public void syncCheckboxes(android.widget.CheckBox cbFps, android.widget.CheckBox cbGpu,
            android.widget.CheckBox cbCpuRam, android.widget.CheckBox cbBattTemp,
            android.widget.CheckBox cbGraph,
            android.widget.CheckBox cbRenderer) {
        if (cbFps      != null) cbFps.setChecked((showMask & SHOW_FPS)          != 0);
        if (cbGpu      != null) cbGpu.setChecked((showMask & SHOW_GPU)          != 0);
        if (cbCpuRam   != null) cbCpuRam.setChecked((showMask & SHOW_CPU)       != 0);
        if (cbBattTemp != null) cbBattTemp.setChecked((showMask & SHOW_BATT)    != 0);

        if (cbRenderer != null) cbRenderer.setChecked((showMask & SHOW_RENDERER)!= 0);
    }

    public void setDataSource(Object dataSource) {}

    public void setHudScale(float scale) {
        setScaleX(scale); setScaleY(scale);
        prefs.edit().putFloat(KEY_SCALE, scale).apply();
    }

    public void setHudAlpha(float a) {
        hudAlpha = Math.max(0f, Math.min(1f, a));
        prefs.edit().putInt(KEY_ALPHA, (int)(hudAlpha * 100)).apply();
        invalidate();
    }

    public void reset() {

        strRend = (isNative ? "+" : "") + rendererLabel;
        wDynRend = pRend.measureText(strRend);
        layoutDirty = true;
        frameAccum.set(0); snapFps = 0; lastFpsNs = 0;
    }

    public void forceReset() {
        uiHandler.post(() -> {
            uiHandler.removeCallbacks(redrawRunnable);
            redrawScheduled = false;
            frameAccum.set(0); snapFps = 0; lastFpsNs = 0;
            dragging = false; touchDownMs = 0;
            rendererActive = true; userEnabled = true;
            prefs.edit().putBoolean(KEY_VIS, true).apply();
            startStatsThread();
            setVisibility(VISIBLE);
            scheduleRedraw();
        });
    }

    private int idxToMask(int idx) {
        switch (idx) {
            case 0: return SHOW_FPS;
            case 2: return SHOW_GPU;
            case 3: return SHOW_CPU;
            case 4: return SHOW_BATT;
            case 6: return SHOW_RENDERER;
            case 7: return SHOW_RAM;
            default: return 0;
        }
    }
}
