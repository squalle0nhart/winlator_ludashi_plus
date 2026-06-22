package com.winlator.cmod;

import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.contentdialog.ContentDialog;

import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.NumberPicker;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class ControlsEditorActivity extends AppCompatActivity implements View.OnClickListener {
    private InputControlsView inputControlsView;
    private ControlsProfile profile;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AppUtils.hideSystemUI(this);
        setContentView(R.layout.controls_editor_activity);

        inputControlsView = new InputControlsView(this);
        inputControlsView.setEditMode(true);
        inputControlsView.setOverlayOpacity(0.6f);

        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, getIntent().getIntExtra("profile_id", 0)));
        ((TextView)findViewById(R.id.TVProfileName)).setText(profile.getName());
        inputControlsView.setProfile(profile);

        FrameLayout container = findViewById(R.id.FLContainer);
        container.addView(inputControlsView, 0);

        container.findViewById(R.id.BTAddElement).setOnClickListener(this);
        container.findViewById(R.id.BTRemoveElement).setOnClickListener(this);
        container.findViewById(R.id.BTElementSettings).setOnClickListener(this);
        container.findViewById(R.id.BTSchemeColor).setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Use a handler delay to ensure the activity is fully visible and interactive
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (!prefs.getBoolean("mix_warning_shown_v4", false)) {
                ContentDialog.alert(this, R.string.warning_gamepad_mouse_mix, () -> {
                    prefs.edit().putBoolean("mix_warning_shown_v4", true).apply();
                });
            }
        }, 500);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.BTAddElement:
                if (!inputControlsView.addElement()) {
                    AppUtils.showToast(this, R.string.no_profile_selected);
                }
                break;
            case R.id.BTRemoveElement:
                if (!inputControlsView.removeElement()) {
                    AppUtils.showToast(this, R.string.no_control_element_selected);
                }
                break;
            case R.id.BTElementSettings:
                ControlElement selectedElement = inputControlsView.getSelectedElement();
                if (selectedElement != null) {
                    showControlElementSettings(v);
                }
                else AppUtils.showToast(this, R.string.no_control_element_selected);
                break;
            case R.id.BTSchemeColor:
                showSchemeColorPicker(v);
                break;
        }
    }

    // Cor aplicada ao esquema inteiro: todo elemento sem cor própria (customColor == 0)
    // passa a usar essa cor em vez do azul padrão. Reaproveita a mesma paleta/lógica de
    // seleção usada no popup de cada elemento (loadColorSwatches).
    private void showSchemeColorPicker(View anchorView) {
        LinearLayout popupContent = new LinearLayout(this);
        popupContent.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)UnitUtils.dpToPx(12);
        popupContent.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Scheme Color");
        title.setTextColor(0xffffffff);
        title.setTextSize(14);
        popupContent.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Applies to every control that doesn't have its own custom color");
        subtitle.setTextColor(0xffaaaaaa);
        subtitle.setTextSize(11);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = (int)UnitUtils.dpToPx(2);
        subtitleParams.bottomMargin = (int)UnitUtils.dpToPx(8);
        subtitle.setLayoutParams(subtitleParams);
        popupContent.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final LinearLayout llSchemeColorList = new LinearLayout(this);
        llSchemeColorList.setOrientation(LinearLayout.VERTICAL);
        llSchemeColorList.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(llSchemeColorList);
        popupContent.addView(scrollView);

        loadColorSwatches(llSchemeColorList, profile.getThemeColor(), color -> {
            profile.setThemeColor(color);
            profile.save();
            inputControlsView.invalidate();
        });

        PopupWindow popupWindow = AppUtils.showPopupWindow(anchorView, popupContent, 320, 0);
    }

    private void showControlElementSettings(View anchorView) {
        final ControlElement element = inputControlsView.getSelectedElement();
        View view = LayoutInflater.from(this).inflate(R.layout.control_element_settings, null);

        final Runnable updateLayout = () -> {
            ControlElement.Type type = element.getType();
            view.findViewById(R.id.LLShape).setVisibility(View.GONE);
            view.findViewById(R.id.CBToggleSwitch).setVisibility(View.GONE);
            view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.GONE);
            view.findViewById(R.id.LLRangeOptions).setVisibility(View.GONE);

            if (type == ControlElement.Type.BUTTON) {
                view.findViewById(R.id.LLShape).setVisibility(View.VISIBLE);
                view.findViewById(R.id.CBToggleSwitch).setVisibility(View.VISIBLE);
                view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.VISIBLE);
            }
            else if (type == ControlElement.Type.RANGE_BUTTON) {
                view.findViewById(R.id.LLRangeOptions).setVisibility(View.VISIBLE);
            }

            loadBindingSpinners(element, view);
        };

        loadTypeSpinner(element, view.findViewById(R.id.SType), updateLayout);
        loadShapeSpinner(element, view.findViewById(R.id.SShape));
        loadRangeSpinner(element, view.findViewById(R.id.SRange));

        RadioGroup rgOrientation = view.findViewById(R.id.RGOrientation);
        rgOrientation.check(element.getOrientation() == 1 ? R.id.RBVertical : R.id.RBHorizontal);
        rgOrientation.setOnCheckedChangeListener((group, checkedId) -> {
            element.setOrientation((byte)(checkedId == R.id.RBVertical ? 1 : 0));
            profile.save();
            inputControlsView.invalidate();
        });

        NumberPicker npColumns = view.findViewById(R.id.NPColumns);
        npColumns.setValue(element.getBindingCount());
        npColumns.setOnValueChangeListener((numberPicker, value) -> {
            element.setBindingCount(value);
            profile.save();
            inputControlsView.invalidate();
        });

        final TextView tvScale = view.findViewById(R.id.TVScale);
        SeekBar sbScale = view.findViewById(R.id.SBScale);
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvScale.setText(progress+"%");
                if (fromUser) {
                    progress = (int)Mathf.roundTo(progress, 5);
                    seekBar.setProgress(progress);
                    element.setScale(progress / 100.0f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbScale.setProgress((int)(element.getScale() * 100));

        CheckBox cbToggleSwitch = view.findViewById(R.id.CBToggleSwitch);
        cbToggleSwitch.setChecked(element.isToggleSwitch());
        cbToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            element.setToggleSwitch(isChecked);
            profile.save();
        });

        final TextView tvOpacity = view.findViewById(R.id.TVOpacity);
        SeekBar sbOpacity = view.findViewById(R.id.SBOpacity);
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvOpacity.setText(progress+"%");
                if (fromUser) {
                    element.setOpacity(progress / 100f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbOpacity.setProgress((int)(element.getOpacity() * 100));

        final LinearLayout llColorList = view.findViewById(R.id.LLColorList);
        loadColorSwatches(llColorList, element.getCustomColor(), color -> {
            element.setCustomColor(color);
            profile.save();
            inputControlsView.invalidate();
        });

        CheckBox cbMouseMoveMode = view.findViewById(R.id.CBMouseMoveMode);
        cbMouseMoveMode.setChecked(element.isMouseMoveMode());
        cbMouseMoveMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            element.setMouseMoveMode(isChecked);
            profile.save();
            inputControlsView.invalidate();
        });

        final EditText etCustomText = view.findViewById(R.id.ETCustomText);
        etCustomText.setText(element.getText());
        final LinearLayout llIconList = view.findViewById(R.id.LLIconList);
        loadIcons(llIconList, element.getIconId());

        updateLayout.run();

        PopupWindow popupWindow = AppUtils.showPopupWindow(anchorView, view, 340, 0);
        popupWindow.setOnDismissListener(() -> {
            String text = etCustomText.getText().toString().trim();
            byte iconId = 0;
            for (int i = 0; i < llIconList.getChildCount(); i++) {
                View child = llIconList.getChildAt(i);
                if (child.isSelected()) {
                    iconId = (byte)child.getTag();
                    break;
                }
            }

            element.setText(text);
            element.setIconId(iconId);
            profile.save();
            inputControlsView.invalidate();
        });
    }

    // Paleta de cores pra "temar" um controle individualmente. 0 = "padrão" (segue a
    // cor normal do tema, hoje o azul do Winlator); os demais são cores fixas.
    private interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private static final int SWATCHES_PER_ROW = 8;

    // Paleta ampliada, organizada por matiz.
    // 0 = "padrão do app" (mesmo azul do Winlator), representado visualmente pelo azul.
    private static final int[] PALETTE_COLORS = {
        // Padrão + escala de cinza
        0, 0xffffffff, 0xffdddddd, 0xffaaaaaa, 0xff777777, 0xff444444, 0xff222222, 0xff000000,
        // Vermelhos
        0xffff3b30, 0xffff6961, 0xffff453a, 0xffcc0000, 0xff800000, 0xff4d0000, 0xffff8080, 0xffffb3b3,
        // Laranjas / Amarelos
        0xffff9500, 0xffff6000, 0xfffe9f0d, 0xffffcc00, 0xffffd60a, 0xffffea00, 0xffffb347, 0xffffdca5,
        // Verdes
        0xff34c759, 0xff30d158, 0xff4cd964, 0xff00b050, 0xff2e8b57, 0xff006400, 0xffa8e6cf, 0xffd4edda,
        // Cianos / Teals
        0xff5ac8fa, 0xff32ade6, 0xff00b4d8, 0xff0096c7, 0xff00758a, 0xff004c5a, 0xffb2ebf2, 0xffe0f7fa,
        // Azuis
        0xff007aff, 0xff0a84ff, 0xff2184ff, 0xff1a56db, 0xff003f8f, 0xff001a66, 0xffbed6f8, 0xffdce9ff,
        // Roxos / Violetas
        0xffaf52de, 0xffbf5af2, 0xff9b59b6, 0xff6e3fa3, 0xff4a0e8f, 0xff2d0066, 0xffd7b4f3, 0xffede0f8,
        // Rosas / Magentas
        0xffff2d92, 0xffff375f, 0xffff6ab0, 0xffe91e8c, 0xffad1457, 0xff6a0032, 0xffffb3d9, 0xffffdcef,
    };

    private void loadColorSwatches(final LinearLayout parent, int selectedColor, final OnColorSelectedListener listener) {
        parent.removeAllViews();
        int size = (int)UnitUtils.dpToPx(26);
        int margin = (int)UnitUtils.dpToPx(2);
        int strokeWidth = (int)UnitUtils.dpToPx(2);

        // Guarda todos os swatches criados pra poder limpar a seleção entre eles.
        final List<View> allSwatches = new ArrayList<>();

        for (int rowStart = 0; rowStart < PALETTE_COLORS.length; rowStart += SWATCHES_PER_ROW) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            parent.addView(row);

            int rowEnd = Math.min(rowStart + SWATCHES_PER_ROW, PALETTE_COLORS.length);
            for (int ci = rowStart; ci < rowEnd; ci++) {
                final int color = PALETTE_COLORS[ci];
                final View swatch = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                params.setMargins(margin, margin, margin, margin);
                swatch.setLayoutParams(params);
                swatch.setTag(color);
                boolean isSelected = color == selectedColor;
                swatch.setSelected(isSelected);

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(color != 0 ? color : 0xffffffff);
                bg.setStroke(isSelected ? strokeWidth : 0, 0xffffffff);
                swatch.setBackground(bg);

                swatch.setOnClickListener(v -> {
                    for (View s : allSwatches) {
                        s.setSelected(false);
                        ((GradientDrawable)s.getBackground()).setStroke(0, 0xffffffff);
                    }
                    swatch.setSelected(true);
                    ((GradientDrawable)swatch.getBackground()).setStroke(strokeWidth, 0xffffffff);
                    listener.onColorSelected(color);
                });

                allSwatches.add(swatch);
                row.addView(swatch);
            }
        }
    }

    private void loadTypeSpinner(final ControlElement element, Spinner spinner, Runnable callback) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Type.names()));
        spinner.setSelection(element.getType().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                element.setType(ControlElement.Type.values()[position]);
                profile.save();
                callback.run();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadShapeSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Shape.names()));
        spinner.setSelection(element.getShape().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                element.setShape(ControlElement.Shape.values()[position]);
                profile.save();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadBindingSpinners(ControlElement element, View view) {
        LinearLayout container = view.findViewById(R.id.LLBindings);
        container.removeAllViews();

        ControlElement.Type type = element.getType();
        if (type == ControlElement.Type.BUTTON) {
            loadBindingSpinner(element, container, 0, R.string.binding);
            loadBindingSpinner(element, container, 1, R.string.binding_secondary);
        }
        else if (type == ControlElement.Type.D_PAD || type == ControlElement.Type.STICK || type == ControlElement.Type.TRACKPAD) {
            loadBindingSpinner(element, container, 0, R.string.binding_up);
            loadBindingSpinner(element, container, 1, R.string.binding_right);
            loadBindingSpinner(element, container, 2, R.string.binding_down);
            loadBindingSpinner(element, container, 3, R.string.binding_left);
        }
    }

    private void loadBindingSpinner(final ControlElement element, LinearLayout container, final int index, int titleResId) {
        View view = LayoutInflater.from(this).inflate(R.layout.binding_field, container, false);
        ((TextView)view.findViewById(R.id.TVTitle)).setText(titleResId);
        final Spinner sBindingType = view.findViewById(R.id.SBindingType);
        final Spinner sBinding = view.findViewById(R.id.SBinding);

        Runnable update = () -> {
            String[] bindingEntries = null;
            switch (sBindingType.getSelectedItemPosition()) {
                case 0:
                    bindingEntries = Binding.keyboardBindingLabels();
                    break;
                case 1:
                    bindingEntries = Binding.mouseBindingLabels();
                    break;
                case 2:
                    bindingEntries = Binding.gamepadBindingLabels();
                    break;
            }

            sBinding.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, bindingEntries));
            AppUtils.setSpinnerSelectionFromValue(sBinding, element.getBindingAt(index).toString());
        };

        sBindingType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Binding selectedBinding = element.getBindingAt(index);
        if (selectedBinding.isKeyboard()) {
            sBindingType.setSelection(0, false);
        }
        else if (selectedBinding.isMouse()) {
            sBindingType.setSelection(1, false);
        }
        else if (selectedBinding.isGamepad()) {
            sBindingType.setSelection(2, false);
        }

        sBinding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Binding binding = Binding.NONE;
                switch (sBindingType.getSelectedItemPosition()) {
                    case 0:
                        binding = Binding.keyboardBindingValues()[position];
                        break;
                    case 1:
                        binding = Binding.mouseBindingValues()[position];
                        break;
                    case 2:
                        binding = Binding.gamepadBindingValues()[position];
                        break;
                }

                if (binding != element.getBindingAt(index)) {
                    element.setBindingAt(index, binding);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        update.run();
        container.addView(view);
    }

    private void loadRangeSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Range.names()));
        spinner.setSelection(element.getRange().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                element.setRange(ControlElement.Range.values()[position]);
                profile.save();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadIcons(final LinearLayout parent, byte selectedId) {
        byte[] iconIds = new byte[0];
        try {
            String[] filenames = getAssets().list("inputcontrols/icons/");
            iconIds = new byte[filenames.length];
            for (int i = 0; i < filenames.length; i++) {
                iconIds[i] = Byte.parseByte(FileUtils.getBasename(filenames[i]));
            }
        }
        catch (IOException e) {}

        Arrays.sort(iconIds);

        int size = (int)UnitUtils.dpToPx(40);
        int margin = (int)UnitUtils.dpToPx(2);
        int padding = (int)UnitUtils.dpToPx(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, 0, margin, 0);

        for (final byte id : iconIds) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(params);
            imageView.setPadding(padding, padding, padding, padding);
            imageView.setBackgroundResource(R.drawable.icon_background);
            imageView.setTag(id);
            imageView.setSelected(id == selectedId);
            imageView.setOnClickListener((v) -> {
                for (int i = 0; i < parent.getChildCount(); i++) parent.getChildAt(i).setSelected(false);
                imageView.setSelected(true);
            });

            try (InputStream is = getAssets().open("inputcontrols/icons/"+id+".png")) {
                imageView.setImageBitmap(BitmapFactory.decodeStream(is));
            }
            catch (IOException e) {}

            parent.addView(imageView);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up);  // Custom slide animations for exiting
    }

}
