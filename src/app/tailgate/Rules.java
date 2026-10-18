package app.tailgate;

/**
 * ADB 守門規則。
 *
 * 兩層保護：
 *  1) iptables 只放行 tun0（Tailscale）的 5555，其餘 DROP。
 *  2) 可選的「自動接受」：把 ro.adb.secure 設 0，adbd 不再要求授權，
 *     配合第 1 層，等於只有 tailnet 內的人能連、且連了就直接進。
 *
 * 安全互鎖：自動接受只有在第 1 層生效時才准開啟，否則等於對全網開放無密碼 ADB。
 */
public final class Rules {

    public static final String PORT = "5555";
    public static final String IFACE = "tun0";

    static final String FLAG_AUTOACCEPT = "/data/adb/adbguard_autoaccept";
    static final String BOOT = "/data/adb/service.d/adbguard.sh";

    /** resetprop 可能不在 root shell 的 PATH 裡，先找絕對路徑。 */
    private static final String RP_PRELUDE =
            "RP=/data/adb/ksu/bin/resetprop; [ -x \"$RP\" ] || RP=$(command -v resetprop); ";

    private Rules() {
    }

    public static final class Snapshot {
        public boolean ruleActive;
        public boolean autoAccept;
        public boolean secureOff;
        public boolean bootInstalled;
        public String tunIp = "";
        public String persistPort = "";
        public int allowed;
        public int blocked;
        public String raw = "";
    }

    /** 套用第一層：只放行 tun0。順序關鍵，先插 DROP 再把 ACCEPT 插到最前面。 */
    public static String apply() {
        String script =
                "iptables -D INPUT -i " + IFACE + " -p tcp --dport " + PORT + " -j ACCEPT 2>/dev/null; " +
                        "iptables -D INPUT -p tcp --dport " + PORT + " -j DROP 2>/dev/null; " +
                        "iptables -I INPUT -p tcp --dport " + PORT + " -j DROP; " +
                        "iptables -I INPUT 1 -i " + IFACE + " -p tcp --dport " + PORT + " -j ACCEPT; " +
                        "echo APPLIED";
        return RootShell.run(script).combined();
    }

    /** 移除第一層，恢復任何介面都能連。 */
    public static String remove() {
        String script =
                "iptables -D INPUT -i " + IFACE + " -p tcp --dport " + PORT + " -j ACCEPT 2>/dev/null; " +
                        "iptables -D INPUT -p tcp --dport " + PORT + " -j DROP 2>/dev/null; " +
                        "echo REMOVED";
        return RootShell.run(script).combined();
    }

    public static boolean isActive() {
        RootShell.Result r = RootShell.run(
                "iptables -C INPUT -p tcp --dport " + PORT + " -j DROP 2>/dev/null && echo YES || echo NO");
        return r.combined().contains("YES");
    }

    /** 把 persist.adb.tcp.port 固定成 5555，重開機也不會掉。 */
    public static String setPersistPort() {
        String script = RP_PRELUDE
                + "[ -n \"$RP\" ] && \"$RP\" persist.adb.tcp.port " + PORT + "; "
                + "setprop persist.adb.tcp.port " + PORT + " 2>/dev/null; "
                + "echo PERSIST=$(getprop persist.adb.tcp.port)";
        return RootShell.run(script).combined();
    }

    /** 開關「自動接受」。
     * 旗標檔是開機腳本唯一判斷依據，所以每次設定都先寫檔再改 prop，
     * 避免「畫面顯示已開、開機後卻沒生效」的狀態不一致。
     */
    public static String setAutoAccept(boolean on) {
        String script = RP_PRELUDE
                + (on
                ? "touch " + FLAG_AUTOACCEPT + "; chmod 644 " + FLAG_AUTOACCEPT + "; "
                : "rm -f " + FLAG_AUTOACCEPT + "; ")
                + "[ -n \"$RP\" ] && \"$RP\" ro.adb.secure " + (on ? "0" : "1") + "; "
                + "stop adbd; sleep 1; start adbd; sleep 1; "
                + "echo AUTO=" + (on ? "ON" : "OFF")
                + " secure=$(getprop ro.adb.secure)"
                + " flag=$([ -f " + FLAG_AUTOACCEPT + " ] && echo 1 || echo 0)";
        return RootShell.run(script).combined();
    }

    /** 讀目前狀態。 */
    public static Snapshot snapshot() {
        // 不用 awk：多層引號跳脫會把腳本弄壞，改成把原始 iptables 文字拿回來在 Java 解析。
        String script =
                "echo RULE=$(iptables -C INPUT -p tcp --dport " + PORT + " -j DROP 2>/dev/null && echo 1 || echo 0); "
                        + "echo SECURE=$(getprop ro.adb.secure); "
                        + "echo FLAG=$([ -f " + FLAG_AUTOACCEPT + " ] && echo 1 || echo 0); "
                        + "echo PERSIST=$(getprop persist.adb.tcp.port); "
                        + "echo BOOT=$([ -f " + BOOT + " ] && echo 1 || echo 0); "
                        + "echo TUNIP=$(ip -4 addr show " + IFACE + " 2>/dev/null | grep -oE 'inet [0-9.]+' | cut -d' ' -f2); "
                        + "echo ---IPT---; "
                        + "iptables -L INPUT -v -n --line-numbers 2>/dev/null | grep 'dpt:" + PORT + "'";
        RootShell.Result r = RootShell.run(script);
        Snapshot s = new Snapshot();
        s.raw = r.combined();
        boolean flag = false;
        boolean inIpt = false;
        for (String line : s.raw.split("\n")) {
            String t = line.trim();
            if (t.equals("---IPT---")) {
                inIpt = true;
                continue;
            }
            if (inIpt) {
                // 格式：num pkts bytes target prot opt in out source destination ...
                String[] f = t.split("\\s+");
                if (f.length >= 4) {
                    int pkts = parseIntSafe(f[1]);
                    if ("ACCEPT".equals(f[3])) {
                        s.allowed = pkts;
                    } else if ("DROP".equals(f[3])) {
                        s.blocked = pkts;
                    }
                }
                continue;
            }
            if (t.startsWith("RULE=")) {
                s.ruleActive = t.endsWith("1");
            } else if (t.startsWith("SECURE=")) {
                s.secureOff = t.substring(7).trim().equals("0");
            } else if (t.startsWith("FLAG=")) {
                flag = t.endsWith("1");
            } else if (t.startsWith("PERSIST=")) {
                s.persistPort = t.substring(8).trim();
            } else if (t.startsWith("BOOT=")) {
                s.bootInstalled = t.endsWith("1");
            } else if (t.startsWith("TUNIP=")) {
                s.tunIp = t.substring(6).trim();
            }
        }
        // 旗標與 prop 兩者都成立才算真的在運作
        s.autoAccept = flag && s.secureOff;
        return s;
    }

    private static int parseIntSafe(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 寫入開機自動套用腳本（含 persist 埠與可選的自動接受）。 */
    public static String installBootScript() {
        String body =
                "#!/system/bin/sh\n" +
                        "i=0\n" +
                        "while [ \"$(getprop sys.boot_completed)\" != \"1\" ] && [ $i -lt 120 ]; do sleep 1; i=$((i+1)); done\n" +
                        "RP=/data/adb/ksu/bin/resetprop; [ -x \"$RP\" ] || RP=$(command -v resetprop)\n" +
                        "setprop service.adb.tcp.port " + PORT + "\n" +
                        "[ -n \"$RP\" ] && \"$RP\" persist.adb.tcp.port " + PORT + "\n" +
                        "i=0\n" +
                        "while [ $i -lt 120 ]; do\n" +
                        "  ip -4 addr show " + IFACE + " 2>/dev/null | grep -q 'inet ' && break\n" +
                        "  sleep 1; i=$((i+1))\n" +
                        "done\n" +
                        "iptables -D INPUT -i " + IFACE + " -p tcp --dport " + PORT + " -j ACCEPT 2>/dev/null\n" +
                        "iptables -D INPUT -p tcp --dport " + PORT + " -j DROP 2>/dev/null\n" +
                        "iptables -I INPUT -p tcp --dport " + PORT + " -j DROP\n" +
                        "iptables -I INPUT 1 -i " + IFACE + " -p tcp --dport " + PORT + " -j ACCEPT\n" +
                        "if [ -f " + FLAG_AUTOACCEPT + " ] && [ -n \"$RP\" ]; then\n" +
                        "  \"$RP\" ro.adb.secure 0\n" +
                        "  stop adbd; sleep 1; start adbd\n" +
                        "fi\n";

        String script =
                "mkdir -p /data/adb/service.d 2>/dev/null; " +
                        "cat > " + BOOT + " <<'EOS'\n" + body + "EOS\n" +
                        "chmod 755 " + BOOT + "; " +
                        "sh -n " + BOOT + " && echo BOOT_OK || echo BOOT_SYNTAX_ERROR";
        return RootShell.run(script).combined();
    }

    public static String removeBootScript() {
        return RootShell.run("rm -f " + BOOT + "; echo BOOT_REMOVED").combined();
    }
}
