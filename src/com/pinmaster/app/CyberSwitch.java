package com.pinmaster.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Cyber-Shield teması için fütüristik neon anahtar bileşeni.
 * Üretici arayüzlerinden (Samsung OneUI, MIUI vb.) bağımsız,
 * piksel hassasiyetinde modern siber geçiş sunar.
 */
public class CyberSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CyberSwitch view, boolean isChecked);
    }

    private boolean isChecked = false;
    private float progress = 0f; // 0 (kapalı) -> 1 (açık)
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    // Cyber-Shield Renkleri
    private static final int COLOR_TRACK_ON = 0xFF00E5FF;
    private static final int COLOR_TRACK_OFF = 0xFF141D2E;
    private static final int COLOR_STROKE_ON = 0x6600E5FF;
    private static final int COLOR_STROKE_OFF = 0xFF283C5C;
    private static final int COLOR_THUMB_ON = 0xFFFFFFFF;
    private static final int COLOR_THUMB_OFF = 0xFF64748B;

    public CyberSwitch(Context context) {
        super(context);
        init();
    }

    public CyberSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CyberSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.2f));

        setOnClickListener(v -> toggle());
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animate) {
        if (this.isChecked == checked && progress == (checked ? 1f : 0f)) {
            return;
        }
        this.isChecked = checked;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        if (animate && isAttachedToWindow()) {
            float target = checked ? 1f : 0f;
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(160);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            progress = checked ? 1f : 0f;
            invalidate();
        }
    }

    public void toggle() {
        boolean next = !isChecked;
        setChecked(next, true);
        if (listener != null) {
            listener.onCheckedChanged(this, next);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        this.listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredW = (int) dp(42);
        int desiredH = (int) dp(24);
        int w = resolveSize(desiredW, widthMeasureSpec);
        int h = resolveSize(desiredH, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        trackRect.set(1.5f, 1.5f, w - 1.5f, h - 1.5f);

        // 1. Ray dolgusu
        int trackColor = blendColor(COLOR_TRACK_OFF, COLOR_TRACK_ON, progress);
        trackPaint.setColor(trackColor);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        // 2. Ray kenarlığı
        int strokeColor = blendColor(COLOR_STROKE_OFF, COLOR_STROKE_ON, progress);
        strokePaint.setColor(strokeColor);
        canvas.drawRoundRect(trackRect, radius, radius, strokePaint);

        // 3. Topuz
        float thumbPadding = dp(2.5f);
        float thumbRadius = radius - thumbPadding;
        float startX = radius;
        float endX = w - radius;
        float currentX = startX + (endX - startX) * progress;
        float currentY = h / 2f;

        int thumbColor = blendColor(COLOR_THUMB_OFF, COLOR_THUMB_ON, progress);
        thumbPaint.setColor(thumbColor);
        canvas.drawCircle(currentX, currentY, thumbRadius, thumbPaint);
    }

    private int blendColor(int from, int to, float ratio) {
        float inv = 1f - ratio;
        int a = (int) (((from >>> 24) * inv) + ((to >>> 24) * ratio));
        int r = (int) ((((from >> 16) & 0xFF) * inv) + (((to >> 16) & 0xFF) * ratio));
        int g = (int) ((((from >> 8) & 0xFF) * inv) + (((to >> 8) & 0xFF) * ratio));
        int b = (int) (((from & 0xFF) * inv) + ((to & 0xFF) * ratio));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
