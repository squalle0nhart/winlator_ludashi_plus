package com.winlator.cmod.fexcore;

import com.winlator.cmod.R;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.widget.SwitchCompat;

import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.ThemeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class FEXCoreManager {
    public static boolean isUnixLibsCompatible(String fexcoreVersion, String wineVersion) {
        String normalizedFEXCore = fexcoreVersion != null
                ? fexcoreVersion.toLowerCase(Locale.ROOT) : "";
        String normalizedWine = wineVersion != null
                ? wineVersion.toLowerCase(Locale.ROOT) : "";
        return normalizedFEXCore.contains("unix") && normalizedWine.contains("unix");
    }

    public static void updateUnixLibsToggle(
            SwitchCompat toggle, Spinner fexcoreSpinner, String wineVersion) {
        Object selectedItem = fexcoreSpinner != null ? fexcoreSpinner.getSelectedItem() : null;
        boolean compatible = isUnixLibsCompatible(
                selectedItem != null ? selectedItem.toString() : "", wineVersion);
        toggle.setEnabled(compatible);
        toggle.setAlpha(compatible ? 1.0f : 0.5f);
    }

    public static void loadFEXCoreVersion(Context context, ContentsManager contentsManager, Spinner spinner, String fexcoreVersion) {
        String[] originalItems = context.getResources().getStringArray(R.array.fexcore_version_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));
        for (ContentProfile profile : contentsManager.getInstalledProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)) {
            String entryName = ContentsManager.getEntryName(profile);
            int firstDashIndex = entryName.indexOf('-');
            itemList.add(entryName.substring(firstDashIndex + 1));
        }
        spinner.setAdapter(ThemeUtils.createSpinnerAdapter(context, itemList));
        AppUtils.setSpinnerSelectionFromValue(spinner, fexcoreVersion);
    }
}
