package com.winlator.cmod.contentdialog;

import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import com.winlator.cmod.R;

import android.content.Context;
import com.winlator.cmod.core.KeyValueSet;

public class DisplayXConfigDialog extends ContentDialog {
    public static String DEFAULT_CONFIG = "trueDisplayX=0" + ",performanceMode=1";
    private Context context;
    
    public DisplayXConfigDialog(View anchor) {
        super(anchor.getContext(), R.layout.displayx_config_dialog);
        context = anchor.getContext();
        setIcon(R.drawable.icon_settings);
        setTitle("DisplayX " + context.getString(R.string.configuration));
        
        final CheckBox cbEnableTrueDisplayX = findViewById(R.id.CBEnableTrueDisplayX);
        final CheckBox cbEnablePerfMode = findViewById(R.id.CBEnablePerfMode);
        
        String tag = anchor.getTag().toString();
        KeyValueSet config = parseConfig(anchor.getTag());
        
        cbEnableTrueDisplayX.setChecked(config.get("trueDisplayX").equals("1") ? true : false);
        cbEnablePerfMode.setChecked(config.get("performanceMode").equals("1") ? true : false);
        
        setOnConfirmCallback(() -> {
            config.put("trueDisplayX", cbEnableTrueDisplayX.isChecked() ? "1": "0");
            config.put("performanceMode", cbEnablePerfMode.isChecked() ? "1" : "0");
            anchor.setTag(config.toString());
        });
    }
    
    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() :  DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }
}
