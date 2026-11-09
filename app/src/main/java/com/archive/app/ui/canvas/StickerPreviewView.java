package com.archive.app.ui.canvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.archive.app.canvas.paint.StickerArt;
import com.archive.app.utils.ThemeController;
import com.archive.app.R;

/** Small canvas that draws one catalogue sticker for the picker grid. */
public class StickerPreviewView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String stickerKey = "tape";

    public StickerPreviewView(Context context) { this(context, null); }

    public StickerPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setStickerKey(String key) {
        this.stickerKey = key;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) * 0.82f;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        int save = canvas.save();
        canvas.translate(left, top);
        int tint = ThemeController.themeColor(getContext(), R.attr.archivePaperInk);
        StickerArt.draw(canvas, stickerKey, size, size, tint, paint);
        canvas.restoreToCount(save);
    }
}
