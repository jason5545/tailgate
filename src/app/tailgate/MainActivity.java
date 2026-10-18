package app.tailgate;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView heroState;
    private TextView heroSub;
    private View heroDot;
    private LinearLayout heroCard;

    private TextView chipTun;
    private TextView chipPort;
    private TextView chipBoot;
    private TextView chipCount;

    private Ui.Toggle toggleRule;
    private Ui.Toggle toggleAccept;
    private TextView toggleAcceptSub;

    private TextView btnPrimary;
    private TextView btnBoot;
    private TextView logView;
    private ScrollView logScroll;

    private Rules.Snapshot last;

    private final Shizuku.OnRequestPermissionResultListener permListener =
            (code, result) -> {
                toast(result == 0 ? getString(R.string.toast_granted) : getString(R.string.toast_denied));
                refresh();
            };

    private final Shizuku.OnBinderReceivedListener binderListener = this::refresh;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        try {
            Shizuku.addRequestPermissionResultListener(permListener);
            Shizuku.addBinderReceivedListenerSticky(binderListener);
        } catch (Throwable ignored) {
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permListener);
        } catch (Throwable ignored) {
        }
        io.shutdownNow();
        super.onDestroy();
    }

    // ================= UI =================

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.surface(this));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // 這台 HyperOS 會在左邊畫一條前景殘影，關掉 foreground 才乾淨
        scroll.setForeground(null);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 32));
        scroll.addView(root);

        // ---- 標題 ----
        TextView kicker = Ui.text(this, getString(R.string.kicker), 12f, Ui.onSurfaceVariant(this), true);
        kicker.setLetterSpacing(0.16f);
        root.addView(kicker);

        TextView title = Ui.text(this, getString(R.string.title), 32f, Ui.onSurface(this), true);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(title);

        TextView sub = Ui.text(this, getString(R.string.subtitle),
                14f, Ui.onSurfaceVariant(this), false);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20));
        root.addView(sub);

        // ---- Hero 狀態卡 ----
        heroCard = Ui.card(this, Ui.primaryContainer(this), 28f);
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout dotWrap = new FrameLayout(this);
        heroDot = new View(this);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
        dotWrap.addView(heroDot, dlp);
        heroRow.addView(dotWrap, new LinearLayout.LayoutParams(
                Ui.dp(this, 22), Ui.dp(this, 22)));

        LinearLayout heroTexts = new LinearLayout(this);
        heroTexts.setOrientation(LinearLayout.VERTICAL);
        heroTexts.setPadding(Ui.dp(this, 10), 0, 0, 0);
        heroState = Ui.text(this, getString(R.string.state_checking), 22f, Ui.onPrimaryContainer(this), true);
        heroSub = Ui.text(this, "", 13f, Ui.onPrimaryContainer(this), false);
        heroTexts.addView(heroState);
        heroTexts.addView(heroSub);
        heroRow.addView(heroTexts);
        heroCard.addView(heroRow);
        root.addView(heroCard, Ui.lpMatch());

        // ---- 狀態 chips ----
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, Ui.dp(this, 14), 0, 0);
        chipTun = chip(getString(R.string.chip_tun_short));
        chipPort = chip(getString(R.string.chip_port_short));
        chipBoot = chip(getString(R.string.chip_boot_short));
        chips.addView(chipTun, chipLp());
        chips.addView(chipPort, chipLp());
        chips.addView(chipBoot, chipLp());
        root.addView(chips);

        chipCount = Ui.text(this, "", 12f, Ui.onSurfaceVariant(this), false);
        chipCount.setPadding(Ui.dp(this, 2), Ui.dp(this, 12), 0, 0);
        root.addView(chipCount);

        // ---- 開關卡 ----
        LinearLayout switches = Ui.card(this, Ui.surfaceContainer(this), 24f);
        ((LinearLayout.LayoutParams) Ui.lpMatch()).topMargin = 0;
        LinearLayout.LayoutParams slp = Ui.lpMatch();
        slp.topMargin = Ui.dp(this, 20);
        root.addView(switches, slp);

        // 開關 1：只放行 Tailscale
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.addView(Ui.text(this, getString(R.string.switch_rule_title), 17f, Ui.onSurface(this), true));
        col1.addView(Ui.text(this, getString(R.string.switch_rule_sub), 13f, Ui.onSurfaceVariant(this), false));
        row1.addView(col1, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toggleRule = new Ui.Toggle(this);
        toggleRule.setOnChanged(this::onToggleRule);
        row1.addView(toggleRule, new LinearLayout.LayoutParams(
                Ui.dp(this, 52), Ui.dp(this, 32)));
        switches.addView(row1);

        switches.addView(divider());

        // 開關 2：自動接受
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.addView(Ui.text(this, getString(R.string.switch_accept_title), 17f, Ui.onSurface(this), true));
        toggleAcceptSub = Ui.text(this, getString(R.string.switch_accept_sub),
                13f, Ui.onSurfaceVariant(this), false);
        col2.addView(toggleAcceptSub);
        row2.addView(col2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toggleAccept = new Ui.Toggle(this);
        toggleAccept.setOnChanged(this::onToggleAccept);
        row2.addView(toggleAccept, new LinearLayout.LayoutParams(
                Ui.dp(this, 52), Ui.dp(this, 32)));
        switches.addView(row2);

        // ---- 動作 ----
        btnPrimary = Ui.pill(this, getString(R.string.btn_grant), true);
        btnPrimary.setOnClickListener(v -> primaryAction());
        LinearLayout.LayoutParams plp = Ui.lpMatch();
        plp.topMargin = Ui.dp(this, 20);
        root.addView(btnPrimary, plp);

        btnBoot = Ui.pill(this, getString(R.string.btn_boot_install), false);
        btnBoot.setOnClickListener(v -> doBoot());
        LinearLayout.LayoutParams blp = Ui.lpMatch();
        blp.topMargin = Ui.dp(this, 10);
        root.addView(btnBoot, blp);

        // ---- 紀錄 ----
        TextView logLabel = Ui.text(this, getString(R.string.log_label), 12f, Ui.onSurfaceVariant(this), true);
        logLabel.setLetterSpacing(0.12f);
        logLabel.setPadding(Ui.dp(this, 2), Ui.dp(this, 28), 0, Ui.dp(this, 8));
        root.addView(logLabel);

        logView = Ui.text(this, getString(R.string.log_empty), 11.5f, Ui.onSurfaceVariant(this), false);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        LinearLayout logCard = Ui.card(this, Ui.surfaceContainerHigh(this), 18f);
        logCard.setPadding(0, 0, 0, 0);
        logCard.addView(logView);

        logScroll = new ScrollView(this);
        logScroll.setVerticalScrollBarEnabled(false);
        logScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        logScroll.setForeground(null);
        logScroll.addView(logCard);
        LinearLayout.LayoutParams llp = Ui.lpMatch();
        llp.topMargin = Ui.dp(this, 2);
        root.addView(logScroll, llp);

        return scroll;
    }

    private TextView chip(String s) {
        TextView t = Ui.text(this, s, 12f, Ui.onSurfaceVariant(this), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 20));
        bg.setColor(Ui.surfaceContainerHigh(this));
        t.setBackground(bg);
        return t;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        return lp;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.outline(this));
        v.setAlpha(0.2f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.7f)));
        lp.topMargin = Ui.dp(this, 18);
        lp.bottomMargin = Ui.dp(this, 18);
        v.setLayoutParams(lp);
        return v;
    }

    // ================= 行為 =================

    private void primaryAction() {
        if (!ShizukuBridge.isReady()) {
            toast(getString(R.string.toast_shizuku_down));
            return;
        }
        if (!ShizukuBridge.hasPermission()) {
            ShizukuBridge.requestPermission();
            return;
        }
        doApplyRule();
    }

    private void onToggleRule() {
        if (!guardRoot()) {
            toggleRule.setChecked(last != null && last.ruleActive, true);
            return;
        }
        final boolean want = toggleRule.isChecked();
        log(want ? getString(R.string.log_apply_rule) : getString(R.string.log_remove_rule));
        io.execute(() -> {
            String out = want ? Rules.apply() : Rules.remove();
            log(out);
            refresh();
            toast(want ? getString(R.string.toast_rule_applied) : getString(R.string.toast_rule_removed));
        });
    }

    private void onToggleAccept() {
        if (!guardRoot()) {
            toggleAccept.setChecked(last != null && last.autoAccept, true);
            return;
        }
        final boolean want = toggleAccept.isChecked();

        // 安全互鎖：沒有第一層保護時，不准開自動接受
        if (want && last != null && !last.ruleActive) {
            toggleAccept.setChecked(false, true);
            toast(getString(R.string.toast_rule_first));
            log(getString(R.string.log_accept_refused));
            return;
        }

        log(want ? getString(R.string.log_accept_on) : getString(R.string.log_accept_off));
        io.execute(() -> {
            String out = Rules.setAutoAccept(want);
            log(out);
            // 重開機也要記得
            if (last == null || !last.bootInstalled) {
                Rules.installBootScript();
            }
            refresh();
            toast(want ? getString(R.string.toast_accept_on) : getString(R.string.toast_accept_off));
        });
    }

    private void doApplyRule() {
        if (!guardRoot()) return;
        log(getString(R.string.log_applying));
        io.execute(() -> {
            String a = Rules.apply();
            String p = Rules.setPersistPort();
            log(a);
            log(p);
            if (last == null || !last.bootInstalled) {
                log(Rules.installBootScript());
            }
            refresh();
            toast(getString(R.string.toast_done));
        });
    }

    private void doBoot() {
        if (!guardRoot()) return;
        log(getString(R.string.log_writing_boot));
        io.execute(() -> {
            String out = Rules.installBootScript();
            log(out);
            refresh();
            toast(out.contains("BOOT_OK")
                    ? getString(R.string.toast_boot_ok)
                    : getString(R.string.toast_boot_fail));
        });
    }

    private boolean guardRoot() {
        if (!ShizukuBridge.isReady()) {
            toast(getString(R.string.toast_shizuku_down_short));
            return false;
        }
        if (!ShizukuBridge.hasPermission()) {
            toast(getString(R.string.toast_no_permission));
            return false;
        }
        if (ShizukuBridge.serverUid() != 0) {
            toast(getString(R.string.toast_not_root, ShizukuBridge.serverUid()));
            return false;
        }
        return true;
    }

    // ================= 狀態 =================

    private void refresh() {
        io.execute(() -> {
            boolean ready = ShizukuBridge.isReady();
            boolean perm = ready && ShizukuBridge.hasPermission();
            int uid = ready ? ShizukuBridge.serverUid() : -1;
            boolean canAct = perm && uid == 0;

            Rules.Snapshot s = null;
            if (canAct) {
                try {
                    s = Rules.snapshot();
                    // 舊版開機腳本寫死 tun0，Tailscale 換到 tun1 後重開機會把自己擋在外面：自動換成新版
                    if (s.bootOutdated) {
                        log(Rules.installBootScript());
                        s = Rules.snapshot();
                    }
                } catch (Throwable ignored) {
                }
            }
            last = s;

            final Rules.Snapshot fs = s;
            final boolean fReady = ready, fPerm = perm, fCan = canAct;
            final int fUid = uid;

            ui.post(() -> render(fReady, fPerm, fCan, fUid, fs));
        });
    }

    private void render(boolean ready, boolean perm, boolean canAct, int uid, Rules.Snapshot s) {
        // Hero
        int dotColor;
        String state;
        String sub;

        if (!ready) {
            dotColor = Ui.error(this);
            state = getString(R.string.state_no_shizuku);
            sub = getString(R.string.sub_no_shizuku);
        } else if (!perm) {
            dotColor = Ui.error(this);
            state = getString(R.string.state_waiting);
            sub = getString(R.string.sub_waiting);
        } else if (uid != 0) {
            dotColor = Ui.error(this);
            state = getString(R.string.state_no_root);
            sub = getString(R.string.sub_no_root, uid);
        } else if (s != null && s.ruleActive) {
            dotColor = Ui.success(this);
            state = getString(R.string.state_protected);
            sub = getString(s.autoAccept ? R.string.sub_protected_accept : R.string.sub_protected);
        } else {
            dotColor = Ui.error(this);
            state = getString(R.string.state_open);
            sub = getString(R.string.sub_open);
        }

        heroState.setText(state);
        heroSub.setText(sub);

        android.graphics.drawable.GradientDrawable dg = new android.graphics.drawable.GradientDrawable();
        dg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dg.setColor(dotColor);
        heroDot.setBackground(dg);

        // 開關狀態
        boolean rule = s != null && s.ruleActive;
        boolean accept = s != null && s.autoAccept;
        toggleRule.setChecked(rule, true);
        toggleAccept.setChecked(accept, true);
        toggleRule.setEnabled(canAct);
        toggleAccept.setEnabled(canAct && rule);
        toggleAcceptSub.setText(rule
                ? getString(R.string.switch_accept_sub)
                : getString(R.string.switch_accept_sub_locked));

        // chips
        String tun = (s != null && !TextUtils.isEmpty(s.tunIp))
                ? s.tunIp : getString(R.string.chip_tun_offline);
        chipTun.setText(getString(R.string.chip_tun, tun));
        chipPort.setText(getString(R.string.chip_port,
                (s != null && !TextUtils.isEmpty(s.persistPort))
                        ? s.persistPort : getString(R.string.chip_port_unset)));
        chipBoot.setText((s != null && s.bootInstalled)
                ? getString(R.string.chip_boot_on) : getString(R.string.chip_boot_off));

        if (s != null) {
            chipCount.setText(getString(R.string.chip_count, s.allowed, s.blocked));
        } else {
            chipCount.setText("");
        }

        // 主按鈕
        if (!ready) {
            btnPrimary.setText(getString(R.string.btn_shizuku_off));
        } else if (!perm) {
            btnPrimary.setText(getString(R.string.btn_grant));
        } else if (!rule) {
            btnPrimary.setText(getString(R.string.btn_enable));
        } else {
            btnPrimary.setText(getString(R.string.btn_reapply));
        }
        btnPrimary.setAlpha(canAct || !perm ? 1f : 0.6f);

        btnBoot.setText((s != null && s.bootInstalled)
                ? getString(R.string.btn_boot_update) : getString(R.string.btn_boot_install));
        btnBoot.setEnabled(canAct);
        btnBoot.setAlpha(canAct ? 1f : 0.4f);
    }

    private void log(String s) {
        if (s == null || s.trim().isEmpty()) return;
        ui.post(() -> {
            String prev = logView.getText().toString();
            String body = prev.startsWith(getString(R.string.log_empty)) ? "" : prev;
            String merged = s.trim() + (body.isEmpty() ? "" : "\n\n" + body);
            if (merged.length() > 4000) merged = merged.substring(0, 4000) + "\n…";
            logView.setText(merged);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_UP));
        });
    }

    private void toast(String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}
