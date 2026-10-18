package app.tailgate;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Material 3 Expressive 風格元件工廠。
 *
 * 直接手刻，不拉 AppCompat / Material Components：這台機器沒有 Gradle，
 * 手動解析 AAR 的遞迴依賴風險高，手刻反而能精準控制外觀，APK 也小得多。
 */
final class Ui {

    private Ui() {
    }

    // ---------- 色票（M3 tonal palette，深淺兩套）----------
    static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int primary(Context c)              { return dark(c) ? 0xFF8CD5B4 : 0xFF0F6B4F; }
    static int onPrimary(Context c)            { return dark(c) ? 0xFF003825 : 0xFFFFFFFF; }
    static int primaryContainer(Context c)     { return dark(c) ? 0xFF00513A : 0xFFA8F2D0; }
    static int onPrimaryContainer(Context c)   { return dark(c) ? 0xFFA8F2D0 : 0xFF002114; }
    static int surface(Context c)              { return dark(c) ? 0xFF101412 : 0xFFFBFDF8; }
    static int surfaceContainer(Context c)     { return dark(c) ? 0xFF1C211E : 0xFFEEF2EC; }
    static int surfaceContainerHigh(Context c) { return dark(c) ? 0xFF262B28 : 0xFFE4E9E2; }
    static int onSurface(Context c)            { return dark(c) ? 0xFFE0E3DF : 0xFF191C1A; }
    static int onSurfaceVariant(Context c)     { return dark(c) ? 0xFFBFC9C2 : 0xFF404943; }
    static int outline(Context c)              { return dark(c) ? 0xFF8A938C : 0xFF707972; }
    static int error(Context c)                { return dark(c) ? 0xFFFFB4AB : 0xFFBA1A1A; }
    static int errorContainer(Context c)       { return dark(c) ? 0xFF93000A : 0xFFFFDAD6; }
    static int onErrorContainer(Context c)     { return dark(c) ? 0xFFFFDAD6 : 0xFF410002; }
    static int success(Context c)              { return dark(c) ? 0xFF7ED9A8 : 0xFF1B6B43; }

    static int dp(Context c, float v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    // ---------- 文字 ----------
    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold
                ? Typeface.create("sans-serif", Typeface.BOLD)
                : Typeface.create("sans-serif", Typeface.NORMAL));
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    // ---------- 卡片 ----------
    static LinearLayout card(Context c, int fill, float radiusDp) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(c, radiusDp));
        v.setBackground(bg);
        int p = dp(c, 20);
        v.setPadding(p, p, p, p);
        return v;
    }

    // ---------- 藥丸按鈕 ----------
    static TextView pill(Context c, CharSequence s, boolean filled) {
        TextView t = text(c, s, 15f, filled ? onPrimary(c) : primary(c), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 24), dp(c, 16), dp(c, 24), dp(c, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 28));
        if (filled) {
            bg.setColor(primary(c));
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(c, 1), outline(c));
        }
        t.setClickable(true);
        t.setFocusable(true);
        ripple(t, c, bg, filled ? 0x40FFFFFF : 0x280F6B4F, 28f);
        springPress(t);
        return t;
    }

    /**
     * 建立 RippleDrawable。
     * 外層圓角靠 mask，所以內容必須是「透明填充 + 帶顏色」的漸層，
     * 不能直接把半透明色填進 shape，不然圓角外會出現方塊雜點。
     */
    static void ripple(View v, Context c, GradientDrawable content, int rippleColor, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        v.setBackground(new RippleDrawable(
                ColorStateList.valueOf(rippleColor), content, mask));
    }

    /** 給卡片這類不需要圓角遮罩的元件用。 */
    static void rippleOn(View v, Context c, int rippleColor) {
        v.setBackground(new RippleDrawable(
                ColorStateList.valueOf(rippleColor), v.getBackground(), null));
    }

    /** M3 Expressive 的彈性回饋：按下縮小，放開帶 overshoot 彈回。 */
    static void springPress(final View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(300)
                            .setInterpolator(new OvershootInterpolator(2.4f)).start();
                    break;
            }
            return false;
        });
    }

    static LinearLayout.LayoutParams lpMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, hDp)));
        return v;
    }

    // ---------- 開關 ----------
    static final class Toggle extends FrameLayout {
        private final View track;
        private final View thumb;
        private final Context ctx;
        private boolean checked;
        private boolean enabled = true;
        private Runnable onChanged;

        Toggle(Context c) {
            super(c);
            this.ctx = c;
            int trackW = dp(c, 52), trackH = dp(c, 32), thumbD = dp(c, 24), m = dp(c, 4);

            track = new View(c);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(trackW, trackH);
            tlp.gravity = Gravity.CENTER_VERTICAL;
            addView(track, tlp);

            thumb = new View(c);
            FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(thumbD, thumbD);
            hlp.gravity = Gravity.CENTER_VERTICAL;
            hlp.leftMargin = m;
            addView(thumb, hlp);

            setClickable(true);
            setOnClickListener(v -> {
                if (!enabled) return;
                setChecked(!checked, true);
                if (onChanged != null) onChanged.run();
            });
            render();
        }

        void setOnChanged(Runnable r) {
            this.onChanged = r;
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean v, boolean animate) {
            checked = v;
            render();
            float target = checked ? dp(ctx, 52 - 24 - 4) : dp(ctx, 4);
            if (animate) {
                thumb.animate().translationX(target)
                        .setDuration(280)
                        .setInterpolator(new OvershootInterpolator(1.8f))
                        .start();
            } else {
                thumb.setTranslationX(target);
            }
        }

        @Override
        public void setEnabled(boolean e) {
            enabled = e;
            setAlpha(e ? 1f : 0.4f);
            super.setEnabled(e);
        }

        private void render() {
            GradientDrawable tg = new GradientDrawable();
            tg.setCornerRadius(dp(ctx, 16));
            if (checked) {
                tg.setColor(primary(ctx));
            } else {
                tg.setColor(surfaceContainerHigh(ctx));
                tg.setStroke(dp(ctx, 1), outline(ctx));
            }
            track.setBackground(tg);

            GradientDrawable hg = new GradientDrawable();
            hg.setShape(GradientDrawable.OVAL);
            hg.setColor(checked ? onPrimary(ctx) : onSurfaceVariant(ctx));
            thumb.setBackground(hg);
        }
    }
}
