package com.winlator.cmod.box64;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.core.ArrayUtils;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.ui.settings.PresetEditorComposeDialog;
import com.winlator.cmod.ui.settings.PresetEditorVariable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Box64EditPresetDialog {
    private final Context context;
    private final String prefix;
    private final Box64Preset preset;
    private final Dialog dialog;
    private Runnable onConfirmCallback;

    public Box64EditPresetDialog(@NonNull Context context, String prefix, String presetId) {
        this.context = context;
        this.prefix = prefix;
        preset = presetId != null ? Box64PresetManager.getPreset(prefix, context, presetId) : null;
        boolean readonly = preset != null && !preset.isCustom();

        String initialName = preset != null
                ? preset.name
                : context.getString(R.string.preset) + "-" + Box64PresetManager.getNextPresetId(context, prefix);

        List<PresetEditorVariable> variables = loadVariables();
        dialog = PresetEditorComposeDialog.create(
                context,
                StringUtils.getString(context, prefix + "_preset"),
                initialName,
                readonly,
                variables,
                (name, values) -> {
                    EnvVars envVars = toEnvVars(values);
                    Box64PresetManager.editPreset(
                            prefix,
                            context,
                            preset != null ? preset.id : null,
                            name,
                            envVars
                    );
                    if (onConfirmCallback != null) onConfirmCallback.run();
                }
        );
    }

    public void setOnConfirmCallback(Runnable onConfirmCallback) {
        this.onConfirmCallback = onConfirmCallback;
    }

    public void show() {
        dialog.show();
    }

    private List<PresetEditorVariable> loadVariables() {
        ArrayList<PresetEditorVariable> variables = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(FileUtils.readString(context, prefix + "_env_vars.json"));
            EnvVars envVars = preset != null ? Box64PresetManager.getEnvVars(prefix, context, preset.id) : null;

            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String name = item.getString("name");
                String[] choices = ArrayUtils.toStringArray(item.getJSONArray("values"));
                String value = envVars != null && envVars.has(name)
                        ? envVars.get(name)
                        : item.getString("defaultValue");
                String suffix = name
                        .replace(prefix.toUpperCase(Locale.ENGLISH) + "_", "")
                        .toLowerCase(Locale.ENGLISH);
                String help = StringUtils.getString(context, "box64_env_var_help__" + suffix);

                variables.add(new PresetEditorVariable(
                        name,
                        value,
                        Arrays.asList(choices),
                        item.optBoolean("toggleSwitch", false),
                        false,
                        help
                ));
            }
        } catch (JSONException ignored) {}
        return variables;
    }

    private static EnvVars toEnvVars(Map<String, String> values) {
        EnvVars envVars = new EnvVars();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            envVars.put(entry.getKey(), entry.getValue());
        }
        return envVars;
    }
}
