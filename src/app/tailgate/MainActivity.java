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
                toast(result == 0 ? "已授權" : "授權被拒絕");
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
        TextView kicker = Ui.text(this, "TAILGATE", 12f, Ui.onSurfaceVariant(this), true);
        kicker.setLetterSpacing(0.16f);
        root.addView(kicker);

        TextView title = Ui.text(this, "只為 tailnet 開的門", 32f, Ui.onSurface(this), true);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(title);

        TextView sub = Ui.text(this, "ADB 5555 只放行 Tailscale，其他人一律擋掉。",
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
        heroState = Ui.text(this, "檢查中…", 22f, Ui.onPrimaryContainer(this), true);
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
        chipTun = chip("Tailscale");
        chipPort = chip("埠");
        chipBoot = chip("開機");
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
        col1.addView(Ui.text(this, "只放行 Tailscale", 17f, Ui.onSurface(this), true));
        col1.addView(Ui.text(this, "封鎖 5555 的其他來源", 13f, Ui.onSurfaceVariant(this), false));
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
        col2.addView(Ui.text(this, "自動接受連線", 17f, Ui.onSurface(this), true));
        toggleAcceptSub = Ui.text(this, "tailnet 內的機器連進來不用再按允許",
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
        btnPrimary = Ui.pill(this, "取得 Shizuku 授權", true);
        btnPrimary.setOnClickListener(v -> primaryAction());
        LinearLayout.LayoutParams plp = Ui.lpMatch();
        plp.topMargin = Ui.dp(this, 20);
        root.addView(btnPrimary, plp);

        btnBoot = Ui.pill(this, "寫入開機自動套用", false);
        btnBoot.setOnClickListener(v -> doBoot());
        LinearLayout.LayoutParams blp = Ui.lpMatch();
        blp.topMargin = Ui.dp(this, 10);
        root.addView(btnBoot, blp);

        // ---- 紀錄 ----
        TextView logLabel = Ui.text(this, "紀錄", 12f, Ui.onSurfaceVariant(this), true);
        logLabel.setLetterSpacing(0.12f);
        logLabel.setPadding(Ui.dp(this, 2), Ui.dp(this, 28), 0, Ui.dp(this, 8));
        root.addView(logLabel);

        logView = Ui.text(this, "（尚無動作）", 11.5f, Ui.onSurfaceVariant(this), false);
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
            toast("Shizuku 沒在跑，先開 Shizuku 啟動它");
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
        log(want ? "套用「只放行 Tailscale」…" : "移除「只放行 Tailscale」…");
        io.execute(() -> {
            String out = want ? Rules.apply() : Rules.remove();
            log(out);
            refresh();
            toast(want ? "已只放行 Tailscale" : "已恢復全開");
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
            toast("先開啟「只放行 Tailscale」再開自動接受");
            log("拒絕開啟自動接受：第一層規則未生效，否則等於全網無密碼 ADB");
            return;
        }

        log(want ? "開啟自動接受…" : "關閉自動接受…");
        io.execute(() -> {
            String out = Rules.setAutoAccept(want);
            log(out);
            // 重開機也要記得
            if (last == null || !last.bootInstalled) {
                Rules.installBootScript();
            }
            refresh();
            toast(want ? "已開啟：tailnet 連線免授權" : "已關閉：恢復授權確認");
        });
    }

    private void doApplyRule() {
        if (!guardRoot()) return;
        log("套用規則並固定埠號…");
        io.execute(() -> {
            String a = Rules.apply();
            String p = Rules.setPersistPort();
            log(a);
            log(p);
            if (last == null || !last.bootInstalled) {
                log(Rules.installBootScript());
            }
            refresh();
            toast("完成");
        });
    }

    private void doBoot() {
        if (!guardRoot()) return;
        log("寫入開機腳本…");
        io.execute(() -> {
            String out = Rules.installBootScript();
            log(out);
            refresh();
            toast(out.contains("BOOT_OK") ? "開機腳本已寫入" : "寫入失敗，看紀錄");
        });
    }

    private boolean guardRoot() {
        if (!ShizukuBridge.isReady()) {
            toast("Shizuku 沒在跑");
            return false;
        }
        if (!ShizukuBridge.hasPermission()) {
            toast("還沒授權");
            return false;
        }
        if (ShizukuBridge.serverUid() != 0) {
            toast("Shizuku 不是 root（UID=" + ShizukuBridge.serverUid() + "）");
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
            state = "Shizuku 未連線";
            sub = "先開啟 Shizuku app 並啟動服務";
        } else if (!perm) {
            dotColor = Ui.error(this);
            state = "等待授權";
            sub = "點下方按鈕授予 Shizuku 權限";
        } else if (uid != 0) {
            dotColor = Ui.error(this);
            state = "權限不足";
            sub = "Shizuku 需以 root 執行（目前 UID " + uid + "）";
        } else if (s != null && s.ruleActive) {
            dotColor = Ui.success(this);
            state = "防護中";
            sub = s.autoAccept ? "只有 tailnet 進得來，且免授權" : "只有 tailnet 進得來";
        } else {
            dotColor = Ui.error(this);
            state = "門戶大開";
            sub = "5555 對所有來源開放中";
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
                ? "tailnet 內的機器連進來不用再按允許"
                : "需先開啟「只放行 Tailscale」");

        // chips
        String tun = (s != null && !TextUtils.isEmpty(s.tunIp)) ? s.tunIp : "未連線";
        chipTun.setText("Tailscale " + tun);
        chipPort.setText("埠 " + ((s != null && !TextUtils.isEmpty(s.persistPort))
                ? s.persistPort : "未固定"));
        chipBoot.setText((s != null && s.bootInstalled) ? "開機自動 ✓" : "開機未設");

        if (s != null) {
            chipCount.setText("tun0 放行 " + s.allowed + " 包　·　攔截 " + s.blocked + " 包");
        } else {
            chipCount.setText("");
        }

        // 主按鈕
        if (!ready) {
            btnPrimary.setText("Shizuku 未啟動");
        } else if (!perm) {
            btnPrimary.setText("取得 Shizuku 授權");
        } else if (!rule) {
            btnPrimary.setText("立即啟用防護");
        } else {
            btnPrimary.setText("重新套用規則");
        }
        btnPrimary.setAlpha(canAct || !perm ? 1f : 0.6f);

        btnBoot.setText((s != null && s.bootInstalled) ? "更新開機腳本" : "寫入開機自動套用");
        btnBoot.setEnabled(canAct);
        btnBoot.setAlpha(canAct ? 1f : 0.4f);
    }

    private void log(String s) {
        if (s == null || s.trim().isEmpty()) return;
        ui.post(() -> {
            String prev = logView.getText().toString();
            String body = prev.startsWith("（尚無") ? "" : prev;
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
