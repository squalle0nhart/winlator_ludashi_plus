package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.ThemeUtils;
import com.winlator.cmod.core.UnitUtils;

import java.util.Locale;

/** Shared plain-language glossary for the container and per-game shortcut editors. */
public final class ContainerGlossaryDialog extends ContentDialog {
    private static final class Entry {
        final String section;
        final String term;
        final String definition;

        Entry(String section, String term, String definition) {
            this.section = section;
            this.term = term;
            this.definition = definition;
        }
    }

    private static final Entry[] ENTRIES = {
        new Entry("Graphics translation (DirectX → Vulkan)", "DXVK",
                "Translates a game's DirectX 9/10/11 graphics into Vulkan. It is the default and usually the fastest option."),
        new Entry("Graphics translation (DirectX → Vulkan)", "VKD3D",
                "Translates DirectX 12 into Vulkan. It needs a newer Vulkan setup, so not every wrapper or GPU can use it."),
        new Entry("Graphics translation (DirectX → Vulkan)", "WineD3D",
                "An older fallback that translates DirectX into OpenGL instead of Vulkan. Try it when a game misbehaves on DXVK."),
        new Entry("Graphics translation (DirectX → Vulkan)", "VEGAS",
                "An Adreno-focused DXVK alternative. It can help games that have trouble with a regular DXVK build."),
        new Entry("Graphics translation (DirectX → Vulkan)", "DirectX (D3D9–12)",
                "The Windows graphics APIs used by games. DXVK handles D3D9/10/11 and VKD3D handles D3D12."),
        new Entry("Graphics translation (DirectX → Vulkan)", "OpenGL",
                "An older cross-platform graphics standard and a fallback host renderer for some games."),
        new Entry("Graphics translation (DirectX → Vulkan)", "Zink",
                "Translates OpenGL into Vulkan so OpenGL games and tools can run through a Vulkan phone driver."),

        new Entry("The Windows layer", "Wine",
                "Runs Windows programs without Windows itself by translating their system calls into Android/Linux calls."),
        new Entry("The Windows layer", "Proton",
                "Valve's gaming-focused Wine variant with extra compatibility and performance patches."),
        new Entry("The Windows layer", "Container",
                "A self-contained virtual Windows environment with its own C: drive, settings, and installed components."),
        new Entry("The Windows layer", "Wrapper (Graphics Driver)",
                "The Vulkan driver layer used to talk to your GPU. Different wrappers suit different Adreno and Mali devices."),

        new Entry("Running x86 games on ARM", "FEXCore",
                "Translates x86/x64 instructions into ARM instructions, with strong support for arm64ec containers."),
        new Entry("Running x86 games on ARM", "Box64",
                "The classic x86/x64-to-ARM instruction translator and an alternative to FEXCore."),
        new Entry("Running x86 games on ARM", "arm64ec",
                "A format that lets native ARM code and translated x86 code run together in the same Windows process."),

        new Entry("GPUs & how they draw", "Vulkan",
                "The modern low-level graphics standard used by DXVK, VKD3D, frame generation, and most phone GPU drivers."),
        new Entry("GPUs & how they draw", "Mali vs Adreno",
                "The main Android GPU families. Adreno commonly uses Turnip; Mali often needs extra compressed-texture support."),
        new Entry("GPUs & how they draw", "Turnip",
                "The open-source Vulkan driver for Adreno GPUs, often used for improved performance and compatibility."),
        new Entry("GPUs & how they draw", "BCn emulation",
                "Converts PC BCn/DXT compressed textures for GPUs that cannot read them directly. Missing support can cause black textures."),
        new Entry("GPUs & how they draw", "ASTC / ETC2",
                "Compressed-texture formats supported by mobile GPUs. BCn content can be transcoded into one of these formats."),
        new Entry("GPUs & how they draw", "Adrenotools",
                "Loads a custom GPU driver such as Turnip instead of the phone's built-in Vulkan driver."),

        new Entry("Picture settings", "Colors: RGBA vs BGRA",
                "The ordering of red and blue color channels. Switch only when a game's red and blue colors appear swapped."),
        new Entry("Picture settings", "Render scale / supersampling",
                "Renders above the display resolution and shrinks the image for extra sharpness, at a performance cost."),
        new Entry("Picture settings", "Frame generation",
                "Inserts generated in-between frames to make motion look smoother. It can add latency and visual artifacts, and remains experimental."),
        new Entry("Picture settings", "win-fg",
                "A bundled clean-room Vulkan frame-generation layer using FSR3 optical flow and its own synthesis. On uses 2x; its model and flow scale can update live."),
        new Entry("Picture settings", "LSFG-VK",
                "An experimental Vulkan frame-generation layer that requires your own Lossless.dll."),
        new Entry("Picture settings", "Frame-gen FPS numbers",
                "Counters can disagree because they count frames at different points. Generated output is still capped by the display refresh rate."),
        new Entry("Picture settings", "DXVK HUD / overlay",
                "An informational overlay for FPS, frame time, GPU load, and driver details. It does not change game performance by itself."),
        new Entry("Picture settings", "Present mode",
                "How finished frames reach the screen. FIFO is the safe default, Mailbox is best with frame generation, and Immediate can tear."),
        new Entry("Picture settings", "FIFO (present mode)",
                "Vsync on: frames wait for the next screen refresh. It is smooth, tear-free, and power-efficient, but can hold frame generation back."),
        new Entry("Picture settings", "Mailbox (present mode)",
                "Fast vsync: the screen takes the newest finished frame without blocking the game. It is tear-free and lets generated frames through."),
        new Entry("Picture settings", "Immediate (present mode)",
                "Vsync off: frames are presented immediately for low latency, but parts of different frames can appear together as tearing."),
        new Entry("Picture settings", "SurfaceFlinger (renderer)",
                "An experimental renderer that hands frames directly to Android's display system. Vulkan is the safer default."),
        new Entry("Picture settings", "Shader stutter & cache",
                "A game may stutter while compiling shaders for the first time. Saved shader caches usually make later sessions smoother."),

        new Entry("Under the hood", "glibc vs bionic",
                "Different C libraries: Android uses bionic while desktop Linux commonly uses glibc. Native libraries must match their target."),
        new Entry("Under the hood", "Environment variables",
                "Advanced per-container switches for tuning Wine, DXVK, VKD3D, drivers, and translation behavior."),
        new Entry("Under the hood", "ESYNC / FSYNC",
                "Wine synchronization speed-ups. Leave them enabled unless a particular game requires otherwise."),
        new Entry("Under the hood", "DLL overrides",
                "Choose whether Wine uses its built-in library or a native Windows DLL supplied by a game."),
        new Entry("Under the hood", "Components",
                "Extra Windows runtimes such as Visual C++, .NET, and DirectX components that some games require."),

        new Entry("Controls & audio", "XInput vs DInput",
                "The modern Xbox-style controller API and the older DirectInput API. Use the one expected by the game."),
        new Entry("Controls & audio", "Exclusive input",
                "Hands a controller to one game so Android and the game do not both react to the same input."),
        new Entry("Controls & audio", "MIDI SoundFont",
                "A sound bank used by older games for MIDI music. It does not affect games that use recorded audio."),
    };

    private final LinearLayout entriesLayout;

    public ContainerGlossaryDialog(Context context) {
        super(context, R.layout.container_glossary_dialog);
        setTitle(R.string.container_glossary);
        setIcon(R.drawable.icon_info);
        findViewById(R.id.BTCancel).setVisibility(View.GONE);

        FrameLayout frame = findViewById(R.id.FrameLayout);
        ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
        frameParams.width = Math.min(AppUtils.getPreferredDialogWidth(context), dp(520));
        frame.setLayoutParams(frameParams);

        entriesLayout = findViewById(R.id.LLGlossaryEntries);
        EditText search = findViewById(R.id.ETGlossarySearch);
        ThemeUtils.applyEditTextTheme(search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        render("");
    }

    private int dp(int value) {
        return Math.round(UnitUtils.dpToPx(value));
    }

    private void render(String query) {
        entriesLayout.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        String currentSection = null;
        int matches = 0;

        for (Entry entry : ENTRIES) {
            String searchable = (entry.section + " " + entry.term + " " + entry.definition)
                    .toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !searchable.contains(needle)) continue;

            if (!entry.section.equals(currentSection)) {
                currentSection = entry.section;
                TextView section = new TextView(getContext());
                section.setText(entry.section);
                section.setTextColor(ThemeUtils.getColorAttr(getContext(), R.attr.themeAccentColor));
                section.setTextSize(14);
                section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                section.setPadding(0, matches == 0 ? dp(4) : dp(14), 0, dp(4));
                entriesLayout.addView(section);
            }

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(6);
            card.setLayoutParams(cardParams);
            card.setBackgroundResource(R.drawable.sidebar_card);

            TextView term = new TextView(getContext());
            term.setText(entry.term);
            term.setTextColor(ThemeUtils.getColorAttr(getContext(), R.attr.colorOnSurface));
            term.setTextSize(14);
            term.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(term);

            TextView definition = new TextView(getContext());
            definition.setText(entry.definition);
            definition.setTextColor(ThemeUtils.getColorAttr(getContext(), R.attr.colorOnSurfaceVariant));
            definition.setTextSize(13);
            definition.setPadding(0, dp(3), 0, 0);
            card.addView(definition);

            entriesLayout.addView(card);
            matches++;
        }

        if (matches == 0) {
            TextView empty = new TextView(getContext());
            empty.setText(R.string.container_glossary_no_results);
            empty.setTextColor(ThemeUtils.getColorAttr(getContext(), R.attr.colorOnSurfaceVariant));
            empty.setPadding(0, dp(16), 0, dp(16));
            entriesLayout.addView(empty);
        }
    }
}
