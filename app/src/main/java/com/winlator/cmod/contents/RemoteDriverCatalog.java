package com.winlator.cmod.contents;

import android.content.Context;
import android.net.Uri;

import com.winlator.cmod.contentdialog.DriverRepo;
import com.winlator.cmod.contentdialog.RepositoryManagerDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class RemoteDriverCatalog {
    private RemoteDriverCatalog() {}

    public static final class Entry {
        public final String repository;
        public final String name;
        public final String url;

        Entry(String repository, String name, String url) {
            this.repository = repository;
            this.name = name;
            this.url = url;
        }
    }

    public static List<Entry> load(Context context) {
        ArrayList<Entry> result = new ArrayList<>();
        OkHttpClient http = new OkHttpClient();
        for (DriverRepo repo : RepositoryManagerDialog.loadDriverRepos(context, 0)) {
            if (repo.apiUrl == null || repo.apiUrl.isEmpty()) continue;
            try (Response response = http.newCall(new Request.Builder().url(repo.apiUrl).build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) continue;
                JSONArray releases = new JSONArray(response.body().string());
                int accepted = 0;
                for (int i = 0; i < releases.length() && accepted < 40; i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null) continue;
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets == null) continue;

                    String releaseName = release.optString("name", release.optString("tag_name", "")).trim();
                    ArrayList<JSONObject> zipAssets = new ArrayList<>();
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.optJSONObject(j);
                        if (asset == null) continue;
                        String url = asset.optString("browser_download_url", "");
                        String assetName = asset.optString("name", "");
                        if (!url.isEmpty() && assetName.toLowerCase(Locale.ENGLISH).endsWith(".zip")) {
                            zipAssets.add(asset);
                        }
                    }

                    for (JSONObject asset : zipAssets) {
                        if (accepted >= 40) break;
                        String url = asset.optString("browser_download_url", "");
                        String assetName = asset.optString("name", "");
                        String assetLabel = assetName.replaceFirst("(?i)\\.zip$", "").trim();

                        String name;
                        if (zipAssets.size() > 1) {
                            name = assetLabel.isEmpty() ? releaseName : assetLabel;
                        } else {
                            name = releaseName.isEmpty() ? assetLabel : releaseName;
                        }
                        if (name.isEmpty()) continue;

                        result.add(new Entry(repo.name, name, url));
                        accepted++;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static String install(Context context, String url) {
        File archive = new File(context.getCacheDir(), "winz-driver-" + System.nanoTime() + ".zip");
        try (Response response = new OkHttpClient().newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            try (InputStream input = response.body().byteStream(); FileOutputStream output = new FileOutputStream(archive)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            return new AdrenotoolsManager(context).installDriver(Uri.fromFile(archive));
        } catch (Exception ignored) {
            return "";
        } finally {
            archive.delete();
        }
    }
}
