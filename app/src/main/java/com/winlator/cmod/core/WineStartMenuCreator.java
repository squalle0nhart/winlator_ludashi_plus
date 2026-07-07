package com.winlator.cmod.core;

import android.content.Context;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.xenvironment.ImageFs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public abstract class WineStartMenuCreator {
    private static final String AJAY_MENU_TARGET = "Z:/home/.Ajay_Prefix/Ajay_Prefix_Pro_v1.8_Downloader.exe";
    private static final String AJAY_ASSET_PATH = "programs/Ajay_Prefix_Pro_v1.8_Downloader.exe";
    private static final String AJAY_HOST_RELATIVE_PATH = "home/.Ajay_Prefix/Ajay_Prefix_Pro_v1.8_Downloader.exe";

    private static int parseShowCommand(String value) {
        if (value.equals("SW_SHOWMAXIMIZED")) {
            return MSLink.SW_SHOWMAXIMIZED;
        }
        else if (value.equals("SW_SHOWMINNOACTIVE")) {
            return MSLink.SW_SHOWMINNOACTIVE;
        }
        else return MSLink.SW_SHOWNORMAL;
    }

    private static void createMenuEntry(JSONObject item, File currentDir) throws JSONException {
        if (item.has("children")) {
            currentDir = new File(currentDir, item.getString("name"));
            currentDir.mkdirs();

            JSONArray children = item.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) createMenuEntry(children.getJSONObject(i), currentDir);
        }
        else {
            File outputFile = new File(currentDir, item.getString("name")+".lnk");
            MSLink.Options options = new MSLink.Options();
            options.targetPath = item.getString("path");
            options.cmdArgs = item.optString("cmdArgs");
            options.iconLocation = item.optString("iconLocation", options.targetPath);
            options.iconIndex = item.optInt("iconIndex", 0);
            if (item.has("showCommand")) options.showCommand = parseShowCommand(item.getString("showCommand"));
            MSLink.createFile(options, outputFile);
        }
    }

    private static void removeMenuEntry(JSONObject item, File currentDir) throws JSONException {
        if (item.has("children")) {
            currentDir = new File(currentDir, item.getString("name"));

            JSONArray children = item.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) removeMenuEntry(children.getJSONObject(i), currentDir);

            if (FileUtils.isEmpty(currentDir)) currentDir.delete();
        }
        else (new File(currentDir, item.getString("name")+".lnk")).delete();
    }

    private static void removeOldMenu(File containerStartMenuFile, File startMenuDir) throws JSONException {
        if (!containerStartMenuFile.isFile()) return;
        JSONArray data = new JSONArray(FileUtils.readString(containerStartMenuFile));
        for (int i = 0; i < data.length(); i++) removeMenuEntry(data.getJSONObject(i), startMenuDir);
    }

    private static JSONArray filterMenuData(Context context, JSONArray data) throws JSONException {
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = filterMenuEntry(context, data.getJSONObject(i));
            if (item != null) filtered.put(item);
        }
        return filtered;
    }

    private static JSONObject filterMenuEntry(Context context, JSONObject item) throws JSONException {
        if (item.has("children")) {
            JSONArray filteredChildren = filterMenuData(context, item.getJSONArray("children"));
            if (filteredChildren.length() == 0) return null;

            JSONObject filteredItem = new JSONObject(item.toString());
            filteredItem.put("children", filteredChildren);
            return filteredItem;
        }

        if (AJAY_MENU_TARGET.equals(item.optString("path")) && !ensureAjayProgramInstalled(context)) {
            return null;
        }

        return new JSONObject(item.toString());
    }

    private static boolean ensureAjayProgramInstalled(Context context) {
        File targetFile = new File(ImageFs.find(context).getRootDir(), AJAY_HOST_RELATIVE_PATH);
        if (targetFile.isFile() && targetFile.length() > 0) return true;
        if (!assetExists(context, AJAY_ASSET_PATH)) return false;

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        FileUtils.copy(context, AJAY_ASSET_PATH, targetFile);
        if (parent != null) FileUtils.chmod(parent, 0771);
        if (targetFile.exists()) FileUtils.chmod(targetFile, 0644);
        return targetFile.isFile() && targetFile.length() > 0;
    }

    private static boolean assetExists(Context context, String assetPath) {
        try (InputStream ignored = context.getAssets().open(assetPath)) {
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public static void create(Context context, Container container) {
        try {
            File startMenuDir = container.getStartMenuDir();
            File containerStartMenuFile = new File(container.getRootDir(), ".startmenu");
            removeOldMenu(containerStartMenuFile, startMenuDir);

            JSONArray data = filterMenuData(context, new JSONArray(FileUtils.readString(context, "wine_startmenu.json")));
            FileUtils.writeString(containerStartMenuFile, data.toString());
            for (int i = 0; i < data.length(); i++) createMenuEntry(data.getJSONObject(i), startMenuDir);
        }
        catch (JSONException e) {}
    }
}
