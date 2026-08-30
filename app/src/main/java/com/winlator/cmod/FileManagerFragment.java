package com.winlator.cmod;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.ExeIconExtractor;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.ui.FileManagerLandscapeNavHost;
import com.winlator.cmod.ui.theme.WinlatorLegacyTheme;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class FileManagerFragment extends Fragment {
    private static final String ARG_START_PATH = "start_path";
    private static final String ARG_DRIVE_ROOT = "drive_root";

    private RecyclerView recyclerView;
    private TextView tvCurrentPath;
    private LinearLayout llDriveSelect;
    private LinearLayout driveOptionsPanel;
    private ImageView ivDriveIcon;
    private TextView tvDriveName;
    private TextView tvDriveStorage;
    private ProgressBar pbDriveStorage;
    private List<File> discoveredExternalStorageRoots;
    private File currentDir;
    private File currentDriveRoot;
    private FileAdapter adapter;
    private ContainerManager containerManager;
    private FloatingActionButton fabPaste;
    private File clipboardFile = null;
    private boolean isCutOperation = false;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView progressPercent;
    private boolean isOperationCancelled = false;

    private interface ContainerAction {
        void onContainerSelected(Container container);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        containerManager = new ContainerManager(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() != null && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("File Manager");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.file_manager_fragment, container, false);
        installLandscapeNavigation(view);

        tvCurrentPath = view.findViewById(R.id.TVCurrentPath);
        recyclerView = view.findViewById(R.id.RecyclerViewFiles);
        llDriveSelect = view.findViewById(R.id.LLDriveSelect);
        driveOptionsPanel = view.findViewById(R.id.DriveOptionsPanel);
        ivDriveIcon = view.findViewById(R.id.IVDriveIcon);
        tvDriveName = view.findViewById(R.id.TVDriveName);
        tvDriveStorage = view.findViewById(R.id.TVDriveStorage);
        pbDriveStorage = view.findViewById(R.id.PBDriveStorage);

        View up = view.findViewById(R.id.BTUpDir);
        if (up != null) up.setOnClickListener(v -> navigateUp());
        if (llDriveSelect != null) llDriveSelect.setOnClickListener(v -> toggleDriveOptions());

        fabPaste = view.findViewById(R.id.fabPaste);
        if (fabPaste != null) {
            fabPaste.setVisibility(View.GONE);
            fabPaste.setOnClickListener(v -> startPasteOperation());
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        Bundle args = getArguments();
        File startDir = null;
        if (args != null) {
            String startPath = args.getString(ARG_START_PATH, "");
            String rootPath = args.getString(ARG_DRIVE_ROOT, "");
            if (!startPath.isEmpty()) startDir = new File(startPath);
            if (!rootPath.isEmpty()) currentDriveRoot = new File(rootPath);
        }

        if (startDir == null || !startDir.exists() || !startDir.isDirectory()) {
            startDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!startDir.exists()) startDir = Environment.getExternalStorageDirectory();
        }
        if (currentDriveRoot == null || !currentDriveRoot.exists()) currentDriveRoot = inferDriveRoot(startDir);

        loadDirectory(startDir);
        populateDriveOptions();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLandscapeChrome();
    }

    @Override
    public void onPause() {
        clearToolbarActions();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isAdded() || currentDir == null) return;

        Bundle args = new Bundle();
        args.putString(ARG_START_PATH, currentDir.getAbsolutePath());
        if (currentDriveRoot != null) args.putString(ARG_DRIVE_ROOT, currentDriveRoot.getAbsolutePath());

        FileManagerFragment replacement = new FileManagerFragment();
        replacement.setArguments(args);
        getParentFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.FLFragmentContainer, replacement)
                .commitAllowingStateLoss();
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void installLandscapeNavigation(View view) {
        if (!isLandscape() || !(requireActivity() instanceof MainActivity)) return;
        View rootView = view.findViewById(R.id.FileManagerRoot);
        View content = view.findViewById(R.id.FileManagerContent);
        if (!(rootView instanceof android.widget.FrameLayout) || content == null) return;

        android.widget.FrameLayout root = (android.widget.FrameLayout) rootView;
        View navigation = FileManagerLandscapeNavHost.create((MainActivity) requireActivity());
        android.widget.FrameLayout.LayoutParams navParams = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.TOP);
        root.addView(navigation, 0, navParams);

        ViewGroup.LayoutParams rawParams = content.getLayoutParams();
        if (rawParams instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams contentParams =
                    (android.widget.FrameLayout.LayoutParams) rawParams;
            contentParams.topMargin = dp(54);
            content.setLayoutParams(contentParams);
        }
    }

    private void updateLandscapeChrome() {
        if (!(requireActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) requireActivity();
        boolean landscape = isLandscape();
        activity.setBottomNavigationVisible(!landscape);
        activity.setMainToolbarVisible(!landscape);

        Toolbar toolbar = activity.findViewById(R.id.Toolbar);
        if (toolbar != null) toolbar.getMenu().clear();
    }

    private void addToolbarDestination(Menu menu, int id, int icon, String title, int destination) {
        MenuItem item = menu.add(Menu.NONE, id, Menu.NONE, title);
        item.setIcon(icon);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item.setOnMenuItemClickListener(clicked -> {
            clearToolbarActions();
            if (requireActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) requireActivity();
                activity.setBottomNavigationVisible(true);
                activity.navigateToMainDestination(destination);
            }
            return true;
        });
    }

    private void clearToolbarActions() {
        if (!isAdded()) return;
        Toolbar toolbar = requireActivity().findViewById(R.id.Toolbar);
        if (toolbar != null) toolbar.getMenu().clear();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private String normalizeFilePath(String path) {
        if (path == null) return "";
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return new File(path).getAbsolutePath();
        }
    }

    private boolean samePath(File first, File second) {
        if (first == null || second == null) return false;
        return normalizeFilePath(first.getAbsolutePath()).equals(normalizeFilePath(second.getAbsolutePath()));
    }

    private boolean isWithinRoot(File file, File root) {
        if (file == null || root == null) return true;
        String filePath = normalizeFilePath(file.getAbsolutePath());
        String rootPath = normalizeFilePath(root.getAbsolutePath());
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private void toggleDriveOptions() {
        if (driveOptionsPanel == null) return;
        boolean opening = driveOptionsPanel.getVisibility() != View.VISIBLE;
        if (opening) populateDriveOptions();
        driveOptionsPanel.setVisibility(opening ? View.VISIBLE : View.GONE);
        View arrow = getView() != null ? getView().findViewById(R.id.IVDriveArrow) : null;
        if (arrow != null) arrow.animate().rotation(opening ? 180f : 0f).setDuration(120).start();
    }

    private void collapseDriveOptions() {
        if (driveOptionsPanel != null) driveOptionsPanel.setVisibility(View.GONE);
        View arrow = getView() != null ? getView().findViewById(R.id.IVDriveArrow) : null;
        if (arrow != null) arrow.animate().rotation(0f).setDuration(100).start();
    }

    private View createDriveOptionRow(String titleText, String subtitleText, boolean selected, Runnable action) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(10), dp(8));
        row.setMinimumHeight(dp(52));
        row.setClickable(true);
        row.setFocusable(true);
        if (selected) {
            row.setBackground(roundedBackground(WinlatorLegacyTheme.surfaceVariant(requireContext()), 11));
        }

        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(WinlatorLegacyTheme.onSurface(requireContext()));
        labels.addView(title);

        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView subtitle = new TextView(requireContext());
            subtitle.setText(subtitleText);
            subtitle.setTextSize(12);
            subtitle.setTextColor(WinlatorLegacyTheme.onSurfaceVariant(requireContext()));
            subtitle.setPadding(0, dp(1), 0, 0);
            labels.addView(subtitle);
        }
        row.addView(labels, labelsLp);

        if (selected) {
            TextView check = new TextView(requireContext());
            check.setText("✓");
            check.setTextSize(18);
            check.setGravity(Gravity.CENTER);
            check.setTextColor(WinlatorLegacyTheme.onSurface(requireContext()));
            row.addView(check, new LinearLayout.LayoutParams(dp(34), dp(34)));
        }

        row.setOnClickListener(v -> {
            collapseDriveOptions();
            action.run();
        });
        return row;
    }

    private List<File> getExternalStorageRoots() {
        ArrayList<File> result = new ArrayList<>();
        java.util.LinkedHashMap<String, File> roots = new java.util.LinkedHashMap<>();
        String primaryPath = normalizeFilePath(Environment.getExternalStorageDirectory().getAbsolutePath());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.storage.StorageManager storageManager =
                    (android.os.storage.StorageManager) requireContext().getSystemService(Context.STORAGE_SERVICE);
            if (storageManager != null) {
                for (android.os.storage.StorageVolume volume : storageManager.getStorageVolumes()) {
                    if (volume.isPrimary()) continue;
                    File directory = volume.getDirectory();
                    if (directory != null && directory.exists()) {
                        roots.put(normalizeFilePath(directory.getAbsolutePath()), directory);
                    }
                }
            }
        }

        File[] appExternalDirs = requireContext().getExternalFilesDirs(null);
        if (appExternalDirs != null) {
            for (File appDir : appExternalDirs) {
                if (appDir == null) continue;
                File cursor = appDir;
                while (cursor != null && cursor.getParentFile() != null) {
                    File parent = cursor.getParentFile();
                    if ("/storage".equals(parent.getAbsolutePath())) {
                        String path = normalizeFilePath(cursor.getAbsolutePath());
                        if (!path.equals(primaryPath)) roots.put(path, cursor);
                        break;
                    }
                    cursor = parent;
                }
            }
        }

        File[] entries = new File("/storage").listFiles();
        if (entries != null) {
            for (File entry : entries) {
                String name = entry.getName();
                if (!entry.isDirectory() || name.equals("emulated") || name.equals("self")) continue;
                String path = normalizeFilePath(entry.getAbsolutePath());
                if (!path.equals(primaryPath) && entry.exists()) roots.put(path, entry);
            }
        }

        result.addAll(roots.values());
        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    private void discoverExternalStorage() {
        discoveredExternalStorageRoots = getExternalStorageRoots();
        populateDriveOptions();
        if (driveOptionsPanel != null) driveOptionsPanel.setVisibility(View.VISIBLE);
        View arrow = getView() != null ? getView().findViewById(R.id.IVDriveArrow) : null;
        if (arrow != null) arrow.setRotation(180f);
        if (discoveredExternalStorageRoots.isEmpty()) {
            Toast.makeText(getContext(), "No external storage found", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateDriveOptions() {
        if (driveOptionsPanel == null) return;
        driveOptionsPanel.removeAllViews();

        File dRoot = Environment.getExternalStorageDirectory();
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dTarget = downloads.exists() ? downloads : dRoot;
        driveOptionsPanel.addView(createDriveOptionRow(
                "Drive D:", "Downloads", samePath(currentDriveRoot, dRoot), () -> openDrive(dTarget, dRoot)));

        boolean inDriveC = currentDir != null && normalizeFilePath(currentDir.getAbsolutePath()).contains("/.wine/drive_c");
        driveOptionsPanel.addView(createDriveOptionRow(
                "Drive C:", "Wine System", inDriveC, this::handleDriveCSelection));

        File rootFs = new File(requireContext().getFilesDir(), "imagefs");
        driveOptionsPanel.addView(createDriveOptionRow(
                "Drive Z:", "RootFS", samePath(currentDriveRoot, rootFs), () -> {
                    if (rootFs.exists()) openDrive(rootFs, rootFs);
                    else Toast.makeText(getContext(), "RootFS not found", Toast.LENGTH_SHORT).show();
                }));

        if (discoveredExternalStorageRoots != null) {
            for (File external : discoveredExternalStorageRoots) {
                driveOptionsPanel.addView(createDriveOptionRow(
                        "External Storage", external.getName(), samePath(currentDriveRoot, external), () -> openDrive(external, external)));
            }
        }

        driveOptionsPanel.addView(createDriveOptionRow(
                "Add External Storage",
                discoveredExternalStorageRoots == null ? "Find SD card or USB storage" : "Scan again",
                false,
                this::discoverExternalStorage));
    }

    private void openDrive(File directory, File driveRoot) {
        if (directory == null || !directory.exists() || !directory.isDirectory() || !directory.canRead()) {
            Toast.makeText(getContext(), "Storage is not accessible", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDriveRoot = driveRoot != null ? driveRoot : inferDriveRoot(directory);
        loadDirectory(directory);
        populateDriveOptions();
    }

    private File inferDriveRoot(File directory) {
        if (directory == null) return Environment.getExternalStorageDirectory();
        String path = normalizeFilePath(directory.getAbsolutePath());
        String primary = normalizeFilePath(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (path.equals(primary) || path.startsWith(primary + File.separator)) {
            return Environment.getExternalStorageDirectory();
        }

        int driveCIndex = path.indexOf("/.wine/drive_c");
        if (driveCIndex >= 0) {
            return new File(path.substring(0, driveCIndex + "/.wine/drive_c".length()));
        }

        if (path.startsWith("/storage/")) {
            String rest = path.substring("/storage/".length());
            int slash = rest.indexOf('/');
            String volume = slash >= 0 ? rest.substring(0, slash) : rest;
            if (!volume.isEmpty() && !volume.equals("emulated") && !volume.equals("self")) {
                File external = new File("/storage/" + volume);
                if (external.exists()) return external;
            }
        }

        File imageFs = new File(requireContext().getFilesDir(), "imagefs");
        String imageFsPath = normalizeFilePath(imageFs.getAbsolutePath());
        if (path.equals(imageFsPath) || path.startsWith(imageFsPath + File.separator)) return imageFs;
        return directory;
    }

    private void updateStorageMeter() {
        if (tvDriveStorage == null || pbDriveStorage == null) return;
        File target = currentDriveRoot != null ? currentDriveRoot : currentDir;
        if (target == null || !target.exists()) return;

        try {
            StatFs stat = new StatFs(target.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            long used = Math.max(0L, total - free);
            int percent = total > 0 ? Math.min(100, Math.round((used * 100f) / total)) : 0;
            tvDriveStorage.setText(Formatter.formatShortFileSize(requireContext(), used) + " / " +
                    Formatter.formatShortFileSize(requireContext(), total));
            pbDriveStorage.setMax(100);
            pbDriveStorage.setProgress(percent);
        } catch (Exception ignored) {
            tvDriveStorage.setText("");
            pbDriveStorage.setProgress(0);
        }
    }

    private void handleDriveCSelection() {
        ArrayList<Container> containers = containerManager.getContainers();
        if (containers == null || containers.isEmpty()) {
            new AlertDialog.Builder(getContext())
                    .setTitle("No Containers")
                    .setMessage("You need to create a container first to access Drive C:.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (containers.size() == 1) {
            navigateToContainerDriveC(containers.get(0));
        } else {
            String[] names = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) names[i] = containers.get(i).getName();
            new AlertDialog.Builder(getContext())
                    .setTitle("Select Container Drive C:")
                    .setItems(names, (dialog, which) -> navigateToContainerDriveC(containers.get(which)))
                    .show();
        }
    }

    private void navigateToContainerDriveC(Container container) {
        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        File windowsDir = new File(driveC, "windows");
        if (driveC.exists() && driveC.isDirectory() && windowsDir.exists()) {
            openDrive(driveC, driveC);
            Toast.makeText(getContext(), "Opened C: (" + container.getName() + ")", Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(getContext())
                    .setTitle("Drive C: Not Initialized")
                    .setMessage("The Wine system files (Drive C:) for '" + container.getName() + "' are missing.\n\n" +
                            "Please RUN this container once to generate the filesystem.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void navigateUp() {
        if (currentDir == null) return;
        if (currentDriveRoot != null && samePath(currentDir, currentDriveRoot)) {
            Toast.makeText(getContext(), "Drive root reached", Toast.LENGTH_SHORT).show();
            return;
        }

        File parent = currentDir.getParentFile();
        if (parent != null && parent.canRead() && isWithinRoot(parent, currentDriveRoot)) {
            loadDirectory(parent);
        } else {
            Toast.makeText(getContext(), "Drive root reached", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || !dir.canRead()) {
            Toast.makeText(getContext(), "Folder is not accessible", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDir = dir;
        if (currentDriveRoot == null) currentDriveRoot = inferDriveRoot(dir);
        tvCurrentPath.setText(dir.getAbsolutePath());
        updateDriveButtonLabel(dir);
        updateStorageMeter();

        File[] files = dir.listFiles();
        List<File> fileList = new ArrayList<>();
        if (files != null) fileList.addAll(Arrays.asList(files));

        Collections.sort(fileList, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            if (!f1.isDirectory() && !f2.isDirectory()) {
                boolean isExe1 = isExecutable(f1);
                boolean isExe2 = isExecutable(f2);
                if (isExe1 && !isExe2) return -1;
                if (!isExe1 && isExe2) return 1;
            }
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        adapter = new FileAdapter(fileList);
        recyclerView.setAdapter(adapter);
        if (fabPaste != null) fabPaste.setVisibility(clipboardFile != null ? View.VISIBLE : View.GONE);
    }

    private void updateDriveButtonLabel(File dir) {
        if (tvDriveName == null || ivDriveIcon == null) return;

        String path = normalizeFilePath(dir.getAbsolutePath());
        String primary = normalizeFilePath(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (path.contains("/.wine/drive_c")) {
            tvDriveName.setText("Drive C:");
            ivDriveIcon.setImageResource(R.drawable.icon_wine);
        } else if (path.equals(primary) || path.startsWith(primary + File.separator)) {
            tvDriveName.setText("Drive D:");
            ivDriveIcon.setImageResource(R.drawable.ic_internal_storage);
        } else if (path.startsWith("/storage/") && !path.startsWith("/storage/emulated")) {
            tvDriveName.setText("External Storage");
            ivDriveIcon.setImageResource(R.drawable.ic_internal_storage);
        } else {
            tvDriveName.setText("Drive Z:");
            ivDriveIcon.setImageResource(android.R.drawable.ic_menu_manage);
        }
    }

    private void performContainerAction(File file, ContainerAction action) {
        ArrayList<Container> containers = containerManager.getContainers();
        if (containers == null || containers.isEmpty()) {
            Toast.makeText(getContext(), "Create a container first!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (containers.size() == 1) {
            action.onContainerSelected(containers.get(0));
        } else {
            String[] names = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) names[i] = containers.get(i).getName();
            new AlertDialog.Builder(getContext())
                    .setTitle("Select Container")
                    .setItems(names, (dialog, which) -> action.onContainerSelected(containers.get(which)))
                    .show();
        }
    }

    private File getRemovableStorageRoot(File file) {
        if (file == null) return null;
        String path = normalizeFilePath(file.getAbsolutePath());
        if (!path.startsWith("/storage/")) return null;

        String rest = path.substring("/storage/".length());
        int slash = rest.indexOf('/');
        String volume = slash >= 0 ? rest.substring(0, slash) : rest;
        if (volume.isEmpty() || volume.equals("emulated") || volume.equals("self")) return null;

        File root = new File("/storage/" + volume);
        return root.exists() && root.isDirectory() ? root : null;
    }

    private void setDriveMapping(Container container, String letter, String path) {
        StringBuilder rebuilt = new StringBuilder();
        for (String[] drive : container.drivesIterator()) {
            if (drive == null || drive.length < 2 || drive[0] == null || drive[1] == null) continue;
            String existingLetter = drive[0].replace(":", "").trim().toUpperCase(Locale.ENGLISH);
            if (existingLetter.equals(letter)) continue;
            rebuilt.append(existingLetter).append(':').append(drive[1]);
        }
        rebuilt.append(letter).append(':').append(path);
        container.setDrives(rebuilt.toString());
        container.saveData();
    }

    private boolean ensureExternalStorageMapped(Container container, File file) {
        File volumeRoot = getRemovableStorageRoot(file);
        if (volumeRoot == null) return true;

        String filePath = normalizeFilePath(file.getAbsolutePath());
        String volumePath = normalizeFilePath(volumeRoot.getAbsolutePath());
        String volumeName = volumeRoot.getName();
        boolean[] used = new boolean[26];

        for (String[] drive : container.drivesIterator()) {
            if (drive == null || drive.length < 2 || drive[0] == null || drive[1] == null) continue;
            String letter = drive[0].replace(":", "").trim().toUpperCase(Locale.ENGLISH);
            if (letter.length() == 1) {
                int index = letter.charAt(0) - 'A';
                if (index >= 0 && index < used.length) used[index] = true;
            }

            String mappedPath = normalizeFilePath(drive[1]);
            if (!mappedPath.isEmpty() && (filePath.equals(mappedPath) || filePath.startsWith(mappedPath + File.separator))) {
                WineUtils.createDosdevicesSymlinks(container);
                return true;
            }

            String legacyRoot = normalizeFilePath("/mnt/media_rw/" + volumeName);
            if (mappedPath.equals(legacyRoot) || mappedPath.startsWith(legacyRoot + File.separator)) {
                setDriveMapping(container, letter, volumePath);
                WineUtils.createDosdevicesSymlinks(container);
                return true;
            }
        }

        char driveLetter = 0;
        for (char candidate = 'E'; candidate <= 'Y'; candidate++) {
            int index = candidate - 'A';
            if (!used[index]) {
                driveLetter = candidate;
                break;
            }
        }
        if (driveLetter == 0) return false;

        setDriveMapping(container, String.valueOf(driveLetter), volumePath);
        WineUtils.createDosdevicesSymlinks(container);
        return true;
    }

    private String getContainerWineHome(Container container) {
        File imagefs = new File(requireContext().getFilesDir(), "imagefs");
        String imagefsPath = normalizeFilePath(imagefs.getAbsolutePath());
        String rootPath = normalizeFilePath(container.getRootDir().getAbsolutePath());
        return rootPath.startsWith(imagefsPath)
                ? rootPath.substring(imagefsPath.length())
                : "/home/" + ImageFs.USER;
    }

    private String toDesktopWindowsPath(File file, Container container) {
        String filePath = normalizeFilePath(file.getAbsolutePath());
        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        String driveCPath = normalizeFilePath(driveC.getAbsolutePath());
        if (filePath.equals(driveCPath) || filePath.startsWith(driveCPath + File.separator)) {
            String rel = filePath.substring(driveCPath.length()).replace(File.separatorChar, '\\');
            while (rel.startsWith("\\")) rel = rel.substring(1);
            return "C:\\" + rel;
        }

        for (String[] drive : container.drivesIterator()) {
            if (drive == null || drive.length < 2 || drive[0] == null || drive[1] == null) continue;
            String driveLetter = drive[0].replace(":", "").trim();
            String drivePath = normalizeFilePath(drive[1]);
            if (driveLetter.isEmpty() || drivePath.isEmpty()) continue;
            if (filePath.equals(drivePath) || filePath.startsWith(drivePath + File.separator)) {
                String relativePath = filePath.substring(drivePath.length()).replace(File.separatorChar, '\\');
                while (relativePath.startsWith("\\")) relativePath = relativePath.substring(1);
                return driveLetter.toUpperCase(Locale.ENGLISH) + ":\\" + relativePath;
            }
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String downloadsPath = normalizeFilePath(downloadsDir.getAbsolutePath());
        if (filePath.equals(downloadsPath) || filePath.startsWith(downloadsPath + File.separator)) {
            String relativePath = filePath.substring(downloadsPath.length()).replace(File.separatorChar, '\\');
            while (relativePath.startsWith("\\")) relativePath = relativePath.substring(1);
            return "D:\\" + relativePath;
        }

        String externalPath = normalizeFilePath(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (filePath.equals(externalPath) || filePath.startsWith(externalPath + File.separator)) {
            String relativePath = filePath.substring(externalPath.length()).replace(File.separatorChar, '\\');
            while (relativePath.startsWith("\\")) relativePath = relativePath.substring(1);
            return "D:\\" + relativePath;
        }
        return "Z:" + filePath.replace('/', '\\');
    }

    private String toDesktopPath(File file, Container container) {
        File parent = file.getParentFile();
        if (parent == null) return "";
        String parentPath = normalizeFilePath(parent.getAbsolutePath());

        for (String[] drive : container.drivesIterator()) {
            if (drive == null || drive.length < 2 || drive[0] == null || drive[1] == null) continue;
            String driveLetter = drive[0].replace(":", "").trim();
            String drivePath = normalizeFilePath(drive[1]);
            if (driveLetter.isEmpty() || drivePath.isEmpty()) continue;
            if (parentPath.equals(drivePath) || parentPath.startsWith(drivePath + File.separator)) {
                String relativePath = parentPath.substring(drivePath.length()).replace(File.separatorChar, '/');
                while (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                String basePath = getContainerWineHome(container) + "/.wine/dosdevices/" +
                        driveLetter.toLowerCase(Locale.ENGLISH) + ":";
                return relativePath.isEmpty() ? basePath : basePath + "/" + relativePath;
            }
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String downloadsPath = normalizeFilePath(downloadsDir.getAbsolutePath());
        if (parentPath.equals(downloadsPath) || parentPath.startsWith(downloadsPath + File.separator)) {
            String relativePath = parentPath.substring(downloadsPath.length()).replace(File.separatorChar, '/');
            while (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            String dBase = getContainerWineHome(container) + "/.wine/dosdevices/d:";
            return relativePath.isEmpty() ? dBase : dBase + "/" + relativePath;
        }

        String externalPath = normalizeFilePath(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (parentPath.equals(externalPath) || parentPath.startsWith(externalPath + File.separator)) {
            String relativePath = parentPath.substring(externalPath.length()).replace(File.separatorChar, '/');
            while (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            String dBase = getContainerWineHome(container) + "/.wine/dosdevices/d:";
            return relativePath.isEmpty() ? dBase : dBase + "/" + relativePath;
        }
        return parentPath;
    }

    private void writeDesktopEntry(PrintWriter writer, String name, String execPath, String path, String icon, Container container) {
        writer.println("[Desktop Entry]");
        writer.println("Name=" + name);
        String escapedExecPath = StringUtils.escapeFileDOSPath(execPath);
        String winePrefix = getContainerWineHome(container) + "/.wine";
        writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine " + escapedExecPath);
        writer.println("Type=Application");
        if (path != null && !path.isEmpty()) writer.println("Path=" + path);
        if (icon != null && !icon.isEmpty()) writer.println("Icon=" + icon);
        writer.println("container_id:" + container.id);
    }

    private void runFileDirectly(File file, Container container) {
        try {
            if (!ensureExternalStorageMapped(container, file)) {
                Toast.makeText(getContext(), "No free drive letter for external storage", Toast.LENGTH_LONG).show();
                return;
            }

            File tempShortcut = new File(requireContext().getCacheDir(), "temp_run.desktop");
            String winePrefix = getContainerWineHome(container) + "/.wine";

            try (PrintWriter writer = new PrintWriter(new FileWriter(tempShortcut))) {
                writer.println("[Desktop Entry]");
                writer.println("Name=" + file.getName());
                writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine \"" + file.getAbsolutePath() + "\"");
                writer.println("Type=Application");
                writer.println("container_id:" + container.id);
            }

            Intent intent = new Intent();
            intent.setClassName(requireContext().getPackageName(), "com.winlator.cmod.XServerDisplayActivity");
            intent.putExtra("container_id", container.id);
            intent.putExtra("shortcut_path", tempShortcut.getAbsolutePath());
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error launching: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void createShortcutDirectly(File file, Container container) {
        try {
            if (!ensureExternalStorageMapped(container, file)) {
                Toast.makeText(getContext(), "No free drive letter for external storage", Toast.LENGTH_LONG).show();
                return;
            }

            String displayName = getSmartDisplayName(file);
            String unixPath = file.getAbsolutePath();
            String winePrefix = getContainerWineHome(container) + "/.wine";
            File shortcutsDir = container.getDesktopDir();
            if (!shortcutsDir.exists()) shortcutsDir.mkdirs();
            File desktopFile = new File(shortcutsDir, displayName + ".desktop");

            try (PrintWriter writer = new PrintWriter(new FileWriter(desktopFile))) {
                writer.println("[Desktop Entry]");
                writer.println("Name=" + displayName);
                writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine \"" + unixPath + "\"");
                writer.println("Type=Application");
                writer.println("Icon=" + displayName);
                writer.println("container_id:" + container.id);
            }
            Toast.makeText(getContext(), "Game added to Library!", Toast.LENGTH_SHORT).show();

            File iconDir64 = container.getIconsDir(64);
            if (!iconDir64.exists()) iconDir64.mkdirs();
            File iconDest = new File(iconDir64, displayName + ".png");
            boolean iconExtracted = ExeIconExtractor.extractIcon(file, iconDest);

            File iconsDir = new File(Environment.getExternalStorageDirectory(), "Winlator/icons");
            if (!iconsDir.exists()) iconsDir.mkdirs();
            if (iconExtracted) {
                File userIcon = new File(iconsDir, displayName + ".png");
                if (!userIcon.exists()) {
                    try {
                        FileUtils.copy(iconDest, userIcon);
                    } catch (Exception ignored) {}
                }
            }

            File coversDir = new File(Environment.getExternalStorageDirectory(), "Winlator/covers");
            if (!coversDir.exists()) coversDir.mkdirs();
            File autoCover = new File(coversDir, displayName + ".png");
            if (autoCover.exists()) autoCover.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyToClipboard(File file, boolean isCut) {
        this.clipboardFile = file;
        this.isCutOperation = isCut;
        if (fabPaste != null) fabPaste.setVisibility(View.VISIBLE);
        Toast.makeText(getContext(), (isCut ? "Cut: " : "Copied: ") + file.getName(), Toast.LENGTH_SHORT).show();
    }

    private void startPasteOperation() {
        if (clipboardFile == null || !clipboardFile.exists()) {
            Toast.makeText(getContext(), "Nothing to paste", Toast.LENGTH_SHORT).show();
            return;
        }

        final File source = clipboardFile;
        File dest = new File(currentDir, source.getName());
        if (dest.exists()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("File Conflict");
            builder.setMessage("The destination \"" + dest.getName() + "\" already exists.");
            builder.setPositiveButton("Replace", (dialog, which) -> {
                deleteRecursive(dest);
                executePaste(source, dest);
            });
            builder.setNeutralButton("Rename", (dialog, which) ->
                    executePaste(source, getUniqueDestination(currentDir, source.getName())));
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            executePaste(source, dest);
        }
    }

    private File getUniqueDestination(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;
        String baseName = name;
        String extension = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        }
        int counter = 1;
        while (dest.exists()) {
            dest = new File(dir, baseName + " (" + counter + ")" + extension);
            counter++;
        }
        return dest;
    }

    private void executePaste(File source, File dest) {
        if (isCutOperation && source.renameTo(dest)) {
            Toast.makeText(getContext(), "Moved instantly", Toast.LENGTH_SHORT).show();
            finishPaste(true);
            return;
        }

        showProgressDialog(isCutOperation ? "Moving..." : "Copying...");
        isOperationCancelled = false;
        new Thread(() -> {
            try {
                long totalBytes = getFolderSize(source);
                AtomicLong copiedBytes = new AtomicLong(0);
                copyRecursiveWithProgress(source, dest, totalBytes, copiedBytes);
                if (isCutOperation && !isOperationCancelled) {
                    long srcSize = getFolderSize(source);
                    long dstSize = getFolderSize(dest);
                    if (srcSize > 0 && srcSize == dstSize && source.canRead()) deleteRecursive(source);
                    else throw new IOException("Safety Stop: Sizes mismatch (" + srcSize + " vs " + dstSize + ") or source unreadable.");
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    dismissProgressDialog();
                    if (!isOperationCancelled) {
                        Toast.makeText(getContext(), "Success!", Toast.LENGTH_SHORT).show();
                        finishPaste(isCutOperation);
                    } else {
                        Toast.makeText(getContext(), "Cancelled", Toast.LENGTH_SHORT).show();
                        deleteRecursive(dest);
                        loadDirectory(currentDir);
                    }
                });
            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                new Handler(Looper.getMainLooper()).post(() -> {
                    dismissProgressDialog();
                    Toast.makeText(getContext(), "Error: " + errorMsg + ". Source preserved.", Toast.LENGTH_LONG).show();
                    deleteRecursive(dest);
                    loadDirectory(currentDir);
                });
            }
        }).start();
    }

    private void finishPaste(boolean clearClipboard) {
        if (clearClipboard) {
            clipboardFile = null;
            if (fabPaste != null) fabPaste.setVisibility(View.GONE);
        }
        loadDirectory(currentDir);
    }

    private void showProgressDialog(String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(title);
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);

        progressPercent = new TextView(getContext());
        progressPercent.setText("0%");
        progressPercent.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        progressPercent.setTextSize(18);
        layout.addView(progressPercent);

        progressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        layout.addView(progressBar);

        progressText = new TextView(getContext());
        progressText.setText("Calculating...");
        progressText.setPadding(0, 20, 0, 0);
        layout.addView(progressText);

        builder.setView(layout);
        builder.setNegativeButton("Cancel", (d, w) -> isOperationCancelled = true);
        progressDialog = builder.create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
    }

    private void updateProgress(long current, long total) {
        int percent = total > 0 ? (int) ((current * 100) / total) : 0;
        final String status = formatSize(current) + " / " + formatSize(total);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (progressBar != null) progressBar.setProgress(percent);
            if (progressPercent != null) progressPercent.setText(percent + "%");
            if (progressText != null) progressText.setText(status);
        });
    }

    private long getFolderSize(File file) {
        long size = 0;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) size += getFolderSize(child);
        } else {
            size = file.length();
        }
        return size;
    }

    private void copyRecursiveWithProgress(File src, File dst, long totalBytes, AtomicLong copiedBytes) throws IOException {
        if (isOperationCancelled) return;
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Failed to create dir: " + dst.getName());
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    copyRecursiveWithProgress(new File(src, child), new File(dst, child), totalBytes, copiedBytes);
                }
            }
        } else {
            copyFileSafe(src, dst, totalBytes, copiedBytes);
        }
    }

    private void copyFileSafe(File source, File dest, long totalBytes, AtomicLong totalCopied) throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int len;
            long oneMb = 1024 * 1024;

            while ((len = in.read(buffer)) > 0) {
                if (isOperationCancelled) break;
                out.write(buffer, 0, len);
                long oldTotal = totalCopied.get();
                long newTotal = totalCopied.addAndGet(len);
                if ((newTotal / oneMb) > (oldTotal / oneMb)) updateProgress(newTotal, totalBytes);
            }
            out.flush();
            out.getFD().sync();
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        fileOrDirectory.delete();
    }

    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Rename");
        final EditText input = new EditText(getContext());
        input.setText(file.getName());
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString();
            File newFile = new File(file.getParent(), newName);
            if (file.renameTo(newFile)) loadDirectory(currentDir);
            else Toast.makeText(getContext(), "Rename failed", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private boolean isExecutable(File f) {
        String name = f.getName().toLowerCase(Locale.ENGLISH);
        return name.endsWith(".exe") || name.endsWith(".msi") || name.endsWith(".bat");
    }

    private void showFileOptions(File file, View anchor) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        if (isExecutable(file)) {
            popup.getMenu().add("Run / Open").setOnMenuItemClickListener(item -> {
                performContainerAction(file, container -> runFileDirectly(file, container));
                return true;
            });
            popup.getMenu().add("Add this game").setOnMenuItemClickListener(item -> {
                performContainerAction(file, container -> createShortcutDirectly(file, container));
                return true;
            });
        }
        popup.getMenu().add("Copy").setOnMenuItemClickListener(item -> {
            copyToClipboard(file, false);
            return true;
        });
        popup.getMenu().add("Cut (Move)").setOnMenuItemClickListener(item -> {
            copyToClipboard(file, true);
            return true;
        });
        popup.getMenu().add("Rename").setOnMenuItemClickListener(item -> {
            renameFile(file);
            return true;
        });
        popup.getMenu().add("Delete").setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Delete")
                    .setMessage("Are you sure you want to delete " + file.getName() + "?")
                    .setPositiveButton("Yes", (d, w) -> {
                        deleteRecursive(file);
                        loadDirectory(currentDir);
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        });
        popup.show();
    }

    private String getSmartDisplayName(File file) {
        String filename = cleanGameName(file.getName());
        String lowerName = filename.toLowerCase(Locale.ENGLISH);
        List<String> genericNames = Arrays.asList(
                "game", "launcher", "setup", "installer", "start", "run",
                "speed", "update", "patch", "loader", "client", "app", "main", "boot", "play",
                "application", "shipping", "x64", "x86", "win64", "win32", "binaries"
        );
        boolean isModOrGeneric = false;
        if (lowerName.contains("mod") || lowerName.contains("fix") || lowerName.contains("crack") || lowerName.contains("patch")) {
            isModOrGeneric = true;
        }
        if (!isModOrGeneric && filename.length() < 4) isModOrGeneric = true;
        if (!isModOrGeneric) {
            for (String gen : genericNames) {
                if (lowerName.equals(gen) || lowerName.startsWith(gen + " ")) {
                    isModOrGeneric = true;
                    break;
                }
            }
        }
        if (isModOrGeneric) {
            File parent = file.getParentFile();
            if (parent != null) {
                String parentName = cleanGameName(parent.getName());
                List<String> genericFolders = Arrays.asList("bin", "bin32", "bin64", "system", "release", "retail", "win64");
                if (genericFolders.contains(parentName.toLowerCase(Locale.ENGLISH))) {
                    File grandParent = parent.getParentFile();
                    if (grandParent != null) return cleanGameName(grandParent.getName());
                }
                return parentName;
            }
        }
        return filename;
    }

    private String cleanGameName(String filename) {
        String name = filename;
        int pos = name.lastIndexOf('.');
        if (pos > 0) name = name.substring(0, pos);
        name = name.replace("_", " ").replace(".", " ").replace("-", " ");
        name = name.replaceAll("(?i)\\b(v\\d+|repack|setup|installer|portable|goty|edition)\\b", "");
        name = name.replaceAll("[^a-zA-Z0-9 ]", "");
        name = name.replaceAll("\\s+", " ").trim();
        return name;
    }

    private File getFileIconCacheFile(File file) {
        File cacheDir = new File(requireContext().getCacheDir(), "file-manager-icons");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        String source = normalizeFilePath(file.getAbsolutePath());
        String key = Integer.toHexString(source.hashCode()) + "-" + file.length() + "-" + file.lastModified();
        return new File(cacheDir, key + ".png");
    }

    private void setFallbackFileIcon(ImageView iconView, int drawableRes) {
        iconView.setTag(null);
        iconView.setImageBitmap(null);
        iconView.setImageResource(drawableRes);
        iconView.setImageTintList(ColorStateList.valueOf(WinlatorLegacyTheme.onSurfaceVariant(requireContext())));
    }

    private void bindExecutableIcon(File file, ImageView iconView) {
        final String boundPath = file.getAbsolutePath();
        iconView.setTag(boundPath);
        File cachedIcon = getFileIconCacheFile(file);
        if (cachedIcon.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(cachedIcon.getAbsolutePath());
            if (bitmap != null) {
                iconView.setImageTintList(null);
                iconView.setImageBitmap(bitmap);
                return;
            }
        }

        iconView.setImageResource(R.drawable.icon_wine);
        iconView.setImageTintList(ColorStateList.valueOf(WinlatorLegacyTheme.onSurfaceVariant(requireContext())));
        if (!file.getName().toLowerCase(Locale.ENGLISH).endsWith(".exe")) return;

        ExeIconExtractor.extractAsync(file, cachedIcon, false, () ->
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded() || !boundPath.equals(iconView.getTag())) return;
                    Bitmap bitmap = BitmapFactory.decodeFile(cachedIcon.getAbsolutePath());
                    if (bitmap != null) {
                        iconView.setImageTintList(null);
                        iconView.setImageBitmap(bitmap);
                    }
                })
        );
    }

    private String modifiedLabel(File file) {
        if (file.lastModified() <= 0) return "";
        return new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(new Date(file.lastModified()));
    }

    private String folderDetails(File file) {
        File[] children = file.listFiles();
        int count = children == null ? 0 : children.length;
        String date = modifiedLabel(file);
        return "Folder  •  " + count + (count == 1 ? " item" : " items") + (date.isEmpty() ? "" : "  •  " + date);
    }

    private String fileDetails(File file) {
        String date = modifiedLabel(file);
        return formatSize(file.length()) + (date.isEmpty() ? "" : "  •  " + date);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private final List<File> files;

        FileAdapter(List<File> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_list_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = files.get(position);
            holder.tvName.setText(file.getName());
            holder.btMenu.setVisibility(View.GONE);

            holder.itemView.setOnLongClickListener(v -> {
                showFileOptions(file, holder.itemView);
                return true;
            });

            if (file.isDirectory()) {
                setFallbackFileIcon(holder.ivIcon, R.drawable.icon_open);
                holder.tvDetails.setText(folderDetails(file));
                holder.itemView.setOnClickListener(v -> loadDirectory(file));
            } else {
                holder.tvDetails.setText(fileDetails(file));
                if (isExecutable(file)) bindExecutableIcon(file, holder.ivIcon);
                else setFallbackFileIcon(holder.ivIcon, android.R.drawable.ic_menu_agenda);
                holder.itemView.setOnClickListener(v -> showFileOptions(file, holder.itemView));
            }
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDetails;
            final ImageView ivIcon;
            final ImageView btMenu;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.TVFileName);
                tvDetails = v.findViewById(R.id.TVFileDetails);
                ivIcon = v.findViewById(R.id.IVIcon);
                btMenu = v.findViewById(R.id.BTFileMenu);
            }
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(Locale.getDefault(), "%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
