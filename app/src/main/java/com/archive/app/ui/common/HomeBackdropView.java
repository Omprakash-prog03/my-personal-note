package com.archive.app.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.archive.app.ArchiveApp;
import com.archive.app.R;
import com.archive.app.utils.SettingsStore;
import com.archive.app.utils.ThemeController;

import java.util.Random;

/**
 * Home wallpaper: paper grain, plain, ruled, dotted or gradient, all drawn from the active theme so
 * changing theme/appearance never leaves a stale colour behind.
 */
public class HomeBackdropView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lastStyle = -1;

    public HomeBackdropView(Context context) { this(context, null); }

    public HomeBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        SettingsStore settings = ArchiveApp.get(getContext()).settings();
        int style = settings.homeBackground();
        lastStyle = style;
        int background = ThemeController.themeColor(getContext(), android.R.attr.colorBackground);
        int accent = ThemeController.themeColor(getContext(), androidx.appcompat.R.attr.colorPrimary);
        int muted = ThemeController.themeColor(getContext(), R.attr.archiveMuted);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        switch (style) {
            case SettingsStore.HOME_BG_RULED:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(withAlpha(muted, 40));
                for (float y = 72f; y < getHeight(); y += 96f) {
                    canvas.drawLine(0, y, getWidth(), y, paint);
                }
                break;
            case SettingsStore.HOME_BG_DOTS:
                paint.setColor(withAlpha(muted, 55));
                for (float x = 48f; x < getWidth(); x += 56f) {
                    for (float y = 56f; y < getHeight(); y += 56f) {
                        canvas.drawCircle(x, y, 1.6f, paint);
                    }
                }
                break;
            case SettingsStore.HOME_BG_GRADIENT:
                paint.setShader(new LinearGradient(0, 0, getWidth() * 0.4f, getHeight(),
                        withAlpha(accent, 34), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                paint.setShader(null);
                break;
            case SettingsStore.HOME_BG_PAPER:
            default:
                Random random = new Random(0xA2C41E1L);
                paint.setColor(withAlpha(muted, 18));
                int dots = Math.max(120, (getWidth() * getHeight()) / 5200);
                for (int i = 0; i < dots; i++) {
                    float x = random.nextFloat() * getWidth();
                    float y = random.nextFloat() * getHeight();
                    canvas.drawCircle(x, y, 0.8f + random.nextFloat() * 1.4f, paint);
                }
                break;
        }
    }

    public void refresh() {
        invalidate();
    }

    private static int withAlpha(int colour, int alpha) {
        return Color.argb(alpha, Color.red(colour), Color.green(colour), Color.blue(colour));
    }
}
