package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DriverDownloadDialog;
import com.winlator.cmod.contentdialog.DriverRepo;
import com.winlator.cmod.contentdialog.RepositoryManagerDialog;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.Downloader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdrenotoolsFragment extends Fragment {
    private AdrenotoolsManager adrenotoolsManager;
    private RecyclerView recyclerView;
    private RecyclerView updatesRecyclerView;
    private RepoPreviewAdapter repoPreviewAdapter;
    private UpdateAdapter updateAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        this.adrenotoolsManager = new AdrenotoolsManager(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.adrenotools_fragment, container, false);
        recyclerView = layout.findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(new DriversAdapter(adrenotoolsManager.enumarateInstalledDrivers()));


        updatesRecyclerView = layout.findViewById(R.id.UpdatesRecyclerView);
        updatesRecyclerView.setLayoutManager(new LinearLayoutManager(updatesRecyclerView.getContext()));
        updateAdapter = new UpdateAdapter(new ArrayList<>());
        updatesRecyclerView.setAdapter(updateAdapter);

        RecyclerView reposRecyclerView = layout.findViewById(R.id.ReposRecyclerView);
        reposRecyclerView.setLayoutManager(new LinearLayoutManager(reposRecyclerView.getContext()));
        repoPreviewAdapter = new RepoPreviewAdapter(RepositoryManagerDialog.loadDriverRepos(getContext(), 3));
        reposRecyclerView.setAdapter(repoPreviewAdapter);

        layout.findViewById(R.id.BTCheckUpdates).setOnClickListener(v -> fetchLatestUpdates());

        View btInstallDriver = layout.findViewById(R.id.BTInstallDriver);
        btInstallDriver.setOnClickListener((v) -> {
            ContentDialog.confirm(getContext(), getString(R.string.install_drivers_message) + " " + getString(R.string.install_drivers_warning), () -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
            });
        });
        fetchLatestUpdates();
        return layout;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.adrenotools_gpu_drivers);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String driver = adrenotoolsManager.installDriver(uri);
            if (!driver.isEmpty())
                ((DriversAdapter)recyclerView.getAdapter()).addItem(driver);
        }
    }

    private void fetchLatestUpdates() {
        if (updateAdapter == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            List<UpdateItem> updates = new ArrayList<>();
            DriverRepo repo = RepositoryManagerDialog.getStevenMxzRepo();
            String jsonStr = Downloader.downloadString(repo.apiUrl);

            if (jsonStr == null) {
                runOnUi(() -> Toast.makeText(getContext(), "Connection failed!", Toast.LENGTH_SHORT).show());
                return;
            }

            try {
                JSONArray releases = new JSONArray(jsonStr);
                for (int i = 0; i < releases.length() && updates.size() < 5; i++) {
                    JSONObject release = releases.getJSONObject(i);
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets == null) continue;

                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String downloadUrl = asset.optString("browser_download_url", "");
                        if (downloadUrl.endsWith(".zip") || downloadUrl.endsWith(".tzst")) {
                            String name = release.optString("name", release.optString("tag_name", asset.optString("name", "Driver Update")));
                            String repoUrl = release.optString("html_url", "");
                            updates.add(new UpdateItem(name, repo.name, downloadUrl, repoUrl));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUi(() -> Toast.makeText(getContext(), "Unable to parse driver updates.", Toast.LENGTH_SHORT).show());
                return;
            }

            runOnUi(() -> {
                updateAdapter.setItems(updates);
                if (updates.isEmpty()) {
                    Toast.makeText(getContext(), "No driver updates found.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void downloadUpdate(UpdateItem item) {
        if (getContext() == null) return;

        File cacheDir = getContext().getCacheDir();
        Toast.makeText(getContext(), "Downloading " + item.name + "...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            File tmpFile = new File(cacheDir, "driver_update.zip");
            if (tmpFile.exists()) tmpFile.delete();

            boolean success = Downloader.downloadFile(item.downloadUrl, tmpFile);
            runOnUi(() -> {
                if (!success) {
                    Toast.makeText(getContext(), "Download failed!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String installedName = adrenotoolsManager.installDriver(Uri.fromFile(tmpFile));
                if (!installedName.isEmpty()) {
                    Toast.makeText(getContext(), "Installed: " + installedName, Toast.LENGTH_SHORT).show();
                    RecyclerView.Adapter adapter = recyclerView.getAdapter();
                    if (adapter instanceof DriversAdapter) {
                        ((DriversAdapter)adapter).reloadList();
                    }
                } else {
                    Toast.makeText(getContext(), "Installation failed! Invalid ZIP.", Toast.LENGTH_LONG).show();
                }
                tmpFile.delete();
            });
        });
    }

    private void openRepositoryManager() {
        RepositoryManagerDialog dialog = new RepositoryManagerDialog(getContext());
        dialog.setOnDismissCallback(() -> {
            RecyclerView.Adapter adapter = recyclerView.getAdapter();
            if (adapter instanceof DriversAdapter) {
                ((DriversAdapter)adapter).reloadList();
            }
            if (repoPreviewAdapter != null) {
                repoPreviewAdapter.setRepos(RepositoryManagerDialog.loadDriverRepos(getContext(), 3));
            }
        });
        dialog.show();
    }

    private void runOnUi(Runnable action) {
        Activity activity = getActivity();
        if (activity != null) activity.runOnUiThread(action);
    }

    private class DriversAdapter extends RecyclerView.Adapter<DashboardViewHolder> {
        private ArrayList<String> driversList;
        public DriversAdapter(ArrayList<String> driversList) { this.driversList = driversList; }
        public void reloadList() { this.driversList = adrenotoolsManager.enumarateInstalledDrivers(); notifyDataSetChanged(); }
        @Override public DashboardViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) { return new DashboardViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.adrenotools_dashboard_item, viewGroup, false)); }
        @Override public void onBindViewHolder(DashboardViewHolder h, final int position) {
            h.name.setText(adrenotoolsManager.getDriverName(driversList.get(position)));
            h.version.setText(adrenotoolsManager.getDriverVersion(driversList.get(position)));
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText("Current Driver");
            h.actionButton.setImageResource(android.R.drawable.ic_menu_delete);
            h.actionButton.setOnClickListener((v) -> removeAtIndex(position));
        }
        public void addItem(String item) { driversList.add(item); notifyItemInserted(getItemCount() - 1); }
        public void removeAtIndex(int index) { String deletedDriver = driversList.remove(index); adrenotoolsManager.removeDriver(deletedDriver); notifyItemRemoved(index); notifyItemRangeChanged(index, getItemCount()); }
        @Override public int getItemCount() { return driversList.size(); }
    }

    private class UpdateAdapter extends RecyclerView.Adapter<DashboardViewHolder> {
        private List<UpdateItem> items;
        UpdateAdapter(List<UpdateItem> items) { this.items = items; }
        void setItems(List<UpdateItem> items) { this.items = items; notifyDataSetChanged(); }
        @Override public DashboardViewHolder onCreateViewHolder(ViewGroup p, int v) { return new DashboardViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.adrenotools_dashboard_item, p, false)); }
        @Override public void onBindViewHolder(DashboardViewHolder h, int position) {
            UpdateItem item = items.get(position);
            h.name.setText(item.name);
            h.version.setText(item.repoName);
            h.badge.setVisibility(View.GONE);
            h.actionButton.setImageResource(android.R.drawable.stat_sys_download);
            h.actionButton.setOnClickListener(v -> downloadUpdate(item));
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private class RepoPreviewAdapter extends RecyclerView.Adapter<DashboardViewHolder> {
        private List<DriverRepo> repos;
        RepoPreviewAdapter(List<DriverRepo> repos) { this.repos = repos; }
        void setRepos(List<DriverRepo> repos) { this.repos = repos; notifyDataSetChanged(); }
        @Override public DashboardViewHolder onCreateViewHolder(ViewGroup p, int v) { return new DashboardViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.adrenotools_dashboard_item, p, false)); }
        @Override public void onBindViewHolder(DashboardViewHolder h, int position) {
            DriverRepo repo = repos.get(position);
            h.name.setText(repo.name);
            h.version.setText(repo.apiUrl.replace("https://api.github.com/repos/", "/"));
            h.badge.setVisibility(View.GONE);
            h.actionButton.setImageResource(android.R.drawable.ic_menu_manage);
            h.actionButton.setOnClickListener(v -> openRepositoryManager());
            h.itemView.setOnClickListener(v -> { DriverDownloadDialog d = new DriverDownloadDialog(getContext(), repo.apiUrl); d.setOnDismissCallback(() -> ((DriversAdapter)recyclerView.getAdapter()).reloadList()); d.show(); });
        }
        @Override public int getItemCount() { return repos.size(); }
    }

    private static class DashboardViewHolder extends RecyclerView.ViewHolder {
        TextView name, version, badge;
        ImageButton actionButton;
        DashboardViewHolder(View v) { super(v); name = v.findViewById(R.id.TVName); version = v.findViewById(R.id.TVVersion); badge = v.findViewById(R.id.TVBadge); actionButton = v.findViewById(R.id.BTMenu); }
    }

    private static class UpdateItem {
        String name, repoName, downloadUrl, repoUrl;
        UpdateItem(String name, String repoName, String downloadUrl, String repoUrl) { this.name = name; this.repoName = repoName; this.downloadUrl = downloadUrl; this.repoUrl = repoUrl; }
    }
}
