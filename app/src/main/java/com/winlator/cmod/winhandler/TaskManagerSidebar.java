package com.winlator.cmod.winhandler;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.CPUStatus;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.ProcessInfo;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TaskManagerSidebar {
    private final XServerDisplayActivity activity;
    private final View rootView;
    private final LayoutInflater inflater;
    private Timer timer;

    public TaskManagerSidebar(XServerDisplayActivity activity, View rootView) {
        this.activity = activity;
        this.rootView = rootView;
        this.inflater = LayoutInflater.from(activity);

        View newTask = rootView.findViewById(R.id.BTTaskNewTask);
        if (newTask != null) newTask.setVisibility(View.GONE);
    }

    public void start() {
        stop();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                List<ProcessInfo> processes = ProcessHelper.listProcessInfo();
                activity.runOnUiThread(() -> {
                    populateProcessList(processes);
                    updateCPUInfoView();
                    updateMemoryInfoView();
                });
            }
        }, 0, 1000);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void updateNow() {
        new Thread(() -> {
            List<ProcessInfo> processes = ProcessHelper.listProcessInfo();
            activity.runOnUiThread(() -> {
                populateProcessList(processes);
                updateCPUInfoView();
                updateMemoryInfoView();
            });
        }).start();
    }

    private void populateProcessList(List<ProcessInfo> processes) {
        LinearLayout container = rootView.findViewById(R.id.LLProcessList);
        if (container == null) return;

        int numProcesses = processes.size();
        TextView title = rootView.findViewById(R.id.TVProcessesTitle);
        if (title != null) title.setText("Processes: " + numProcesses);

        View empty = rootView.findViewById(R.id.TVEmptyText);
        if (numProcesses == 0) {
            container.removeAllViews();
            if (empty != null) empty.setVisibility(View.VISIBLE);
            return;
        }

        if (empty != null) empty.setVisibility(View.GONE);

        XServer xServer = activity.getXServer();
        try (XLock xlock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            for (int index = 0; index < numProcesses; index++) {
                ProcessInfo processInfo = processes.get(index);
                int childCount = container.getChildCount();
                boolean isNew = index >= childCount;
                View itemView = isNew
                        ? inflater.inflate(R.layout.process_info_list_item, container, false)
                        : container.getChildAt(index);

                ((TextView) itemView.findViewById(R.id.TVName)).setText(
                        processInfo.name + (processInfo.wow64Process ? " *32" : ""));
                ((TextView) itemView.findViewById(R.id.TVPID)).setText("PID: " + processInfo.pid);
                ((TextView) itemView.findViewById(R.id.TVMemoryUsage)).setText(
                        processInfo.getFormattedMemoryUsage());

                itemView.findViewById(R.id.BTMenu)
                        .setOnClickListener(v -> showListItemMenu(v, processInfo));

                Window window = xServer.windowManager.findWindowByProcessName(processInfo.name);
                ImageView ivIcon = itemView.findViewById(R.id.IVIcon);
                ivIcon.setImageResource(R.drawable.taskmgr_process);
                if (window != null) {
                    Bitmap icon = xServer.pixmapManager.getWindowIcon(window);
                    if (icon != null) ivIcon.setImageBitmap(icon);
                }

                if (isNew) container.addView(itemView);
            }
        }

        int childCount = container.getChildCount();
        for (int i = childCount - 1; i >= numProcesses; i--) container.removeViewAt(i);
    }

    private void showListItemMenu(View anchorView, ProcessInfo processInfo) {
        PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

        listItemMenu.inflate(R.menu.process_popup_menu);
        listItemMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.process_affinity) {
                showProcessorAffinityDialog(processInfo);
            } else if (itemId == R.id.bring_to_front) {
                activity.getWinHandler().bringToFront(processInfo.name);
            } else if (itemId == R.id.process_end) {
                ContentDialog.confirm(activity, R.string.do_you_want_to_end_this_process,
                        () -> ProcessHelper.killProcess(processInfo.pid));
            }
            return true;
        });
        listItemMenu.show();
    }

    private void showProcessorAffinityDialog(ProcessInfo processInfo) {
        ContentDialog dialog = new ContentDialog(activity, R.layout.cpu_list_dialog);
        dialog.setTitle(processInfo.name);
        dialog.setIcon(R.drawable.icon_cpu);
        CPUListView cpuListView = dialog.findViewById(R.id.CPUListView);
        cpuListView.setCheckedCPUList(processInfo.getCPUList());
        cpuListView.setCheckboxesEnabled(false);
        dialog.show();
    }

    private void updateCPUInfoView() {
        short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
        if (clockSpeeds.length == 0) return;

        int totalClockSpeed = 0;
        short maxClockSpeed = 0;
        for (int i = 0; i < clockSpeeds.length; i++) {
            short clockSpeed = CPUStatus.getMaxClockSpeed(i);
            totalClockSpeed += clockSpeeds[i];
            maxClockSpeed = (short) Math.max(maxClockSpeed, clockSpeed);
        }

        int avgClockSpeed = totalClockSpeed / clockSpeeds.length;
        byte cpuUsagePercent = maxClockSpeed == 0 ? 0 :
                (byte) (((float) avgClockSpeed / maxClockSpeed) * 100.0f);

        TextView compact = rootView.findViewById(R.id.TVCPUInfoCompact);
        if (compact != null) compact.setText(cpuUsagePercent + "%");
    }

    private void updateMemoryInfoView() {
        ActivityManager activityManager =
                (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long usedMem = memoryInfo.totalMem - memoryInfo.availMem;

        TextView info = rootView.findViewById(R.id.TVMemoryInfo);
        if (info != null) {
            info.setText(StringUtils.formatBytes(usedMem, false) + " / " +
                    StringUtils.formatBytes(memoryInfo.totalMem));
        }
    }
}
