package com.winlator.cmod.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.core.WineThemeManager;

import java.io.File;

public class ImagePickerView extends View implements View.OnClickListener {
    private final Bitmap icon;
    private boolean wallpaperSectionPromoted;

    public ImagePickerView(Context context) {
        this(context, null);
    }

    public ImagePickerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImagePickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_image_picker);

        setBackgroundResource(R.drawable.combo_box);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getId() == R.id.IPVDesktopBackgroundImage && !wallpaperSectionPromoted) {
            post(this::promoteWallpaperEditor);
        }
    }

    private void promoteWallpaperEditor() {
        if (wallpaperSectionPromoted || !isAttachedToWindow()) return;

        View current = this;
        View wallpaperSection = null;
        LinearLayout generalTab = null;

        while (current.getParent() instanceof View) {
            View parent = (View) current.getParent();
            if (parent.getId() == R.id.LLTabWineConfiguration && parent instanceof LinearLayout) {
                generalTab = (LinearLayout) parent;
                wallpaperSection = current;
                break;
            }
            current = parent;
        }

        if (generalTab == null || wallpaperSection == null || wallpaperSection.getParent() != generalTab) return;
        wallpaperSectionPromoted = true;

        wallpaperSection.setVisibility(VISIBLE);
        TextView title = wallpaperSection.findViewById(R.id.TVDesktop);
        if (title != null) title.setText("Wallpaper");

        int oldIndex = generalTab.indexOfChild(wallpaperSection);
        if (oldIndex > 0) {
            ViewGroup.LayoutParams original = wallpaperSection.getLayoutParams();
            generalTab.removeView(wallpaperSection);
            generalTab.addView(wallpaperSection, 0, original);
        }

        installPersistentImageAction(wallpaperSection);
    }

    private void installPersistentImageAction(View wallpaperSection) {
        Spinner backgroundType = wallpaperSection.findViewById(R.id.SDesktopBackgroundType);
        if (backgroundType == null) return;

        View current = backgroundType;
        LinearLayout fieldSet = null;
        while (current.getParent() instanceof View) {
            View parent = (View) current.getParent();
            if (parent == wallpaperSection) break;
            if (parent instanceof LinearLayout) fieldSet = (LinearLayout) parent;
            current = parent;
        }
        if (fieldSet == null || fieldSet.findViewWithTag("winz-wallpaper-image-action") != null) return;

        LinearLayout row = new LinearLayout(getContext());
        row.setTag("winz-wallpaper-image-action");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView label = new TextView(getContext());
        label.setText("Wallpaper image");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(getContext());
        action.setText(WineThemeManager.getUserWallpaperFile(getContext()).isFile() ? "Change image" : "Choose image");
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        action.setFocusable(true);
        action.setBackgroundResource(R.drawable.combo_box);
        action.setPadding(dp(14), dp(8), dp(14), dp(8));
        action.setOnClickListener(v -> {
            backgroundType.setSelection(WineThemeManager.BackgroundType.IMAGE.ordinal());
            post(this::performClick);
        });
        row.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fieldSet.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float rectSize = height - UnitUtils.dpToPx(12);
        float startX = (width - rectSize) * 0.5f - UnitUtils.dpToPx(16);
        float startY = (height - rectSize) * 0.5f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
        RectF dstRect = new RectF(startX, startY, startX + rectSize, startY + rectSize);
        canvas.drawBitmap(icon, srcRect, dstRect, paint);
    }

    @Override
    public void onClick(View anchor) {
        final Context context = getContext();
        final File userWallpaperFile = WineThemeManager.getUserWallpaperFile(context);

        View view = LayoutInflater.from(context).inflate(R.layout.image_picker_view, null);
        ImageView imageView = view.findViewById(R.id.ImageView);

        if (userWallpaperFile.isFile()) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(userWallpaperFile.getPath()));
        }
        else imageView.setImageResource(R.drawable.wallpaper);

        final PopupWindow[] popupWindow = {null};
        View browseButton = view.findViewById(R.id.BTBrowse);
        browseButton.setOnClickListener((v) -> {
            MainActivity activity = (MainActivity)context;
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            popupWindow[0].dismiss();
            activity.startActivityForResult(intent, MainActivity.OPEN_IMAGE_REQUEST_CODE);
        });

        View removeButton = view.findViewById(R.id.BTRemove);
        if (userWallpaperFile.isFile()) {
            removeButton.setVisibility(View.VISIBLE);
            removeButton.setOnClickListener((v) -> {
                FileUtils.delete(userWallpaperFile);
                popupWindow[0].dismiss();
            });
        }

        popupWindow[0] = AppUtils.showPopupWindow(anchor, view, 200, 240);
    }
}
