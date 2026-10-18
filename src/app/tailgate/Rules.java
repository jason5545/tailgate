package app.tailgate;

/**
 * ADB 守門規則。
 *
 * 兩層保護：
 *  1) iptables／ip6tables 只放行 tun 介面（Tailscale）的 5555，其餘 DROP。
 *     介面用 tun+ 比對：Tailscale 重建 VPN 時會換名字（tun0 → tun1），寫死 tun0 會把自己擋在外面。
 *  2) 可選的「自動接受」：把 ro.adb.secure 設 0，adbd 不再要求授權，
 *     配合第 1 層，等於只有 tailnet 內的人能連、且連了就直接進。
 *
 * 安全互鎖：自動接受只有在第 1 層生效時才准開啟，否則等於對全網開放無密碼 ADB；
 * 移除第 1 層時也一併關掉自動接受。
 */
public final class Rules {

    public static final String PORT = "5555";
    /** iptables 的介面比對，+ 是萬用字元。 */
    public static final String IFACE = "tun+";
    /** 舊版寫死的介面，套用時順手清掉留下來的規則。 */
    private static final String LEGACY_IFACE = "tun0";

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
        /** 開機腳本是舊版（寫死 tun0、沒有 IPv6），要重寫。 */
        public boolean bootOutdated;
        public String tunIp = "";
        public String persistPort = "";
        public int allowed;
        public int blocked;
        public String raw = "";
    }

    /** 刪掉本工具的規則（含舊版的 tun0），重複執行不會出錯。一行一條，給 app 與開機腳本共用。 */
    private static String deleteLines() {
        StringBuilder sb = new StringBuilder();
        for (String t : new String[]{"iptables", "ip6tables"}) {
            sb.append(t).append(" -D INPUT -i ").append(LEGACY_IFACE).append(" -p tcp --dport ").append(PORT).append(" -j ACCEPT 2>/dev/null\n");
            sb.append(t).append(" -D INPUT -i ").append(IFACE).append(" -p tcp --dport ").append(PORT).append(" -j ACCEPT 2>/dev/null\n");
            sb.append(t).append(" -D INPUT -p tcp --dport ").append(PORT).append(" -j DROP 2>/dev/null\n");
        }
        return sb.toString();
    }

    /** 先刪再插：順序關鍵，先插 DROP 再把 ACCEPT 插到最前面。IPv4、IPv6 各一組。 */
    private static String applyLines() {
        StringBuilder sb = new StringBuilder(deleteLines());
        for (String t : new String[]{"iptables", "ip6tables"}) {
            sb.append(t).append(" -I INPUT -p tcp --dport ").append(PORT).append(" -j DROP\n");
            sb.append(t).append(" -I INPUT 1 -i ").append(IFACE).append(" -p tcp --dport ").append(PORT).append(" -j ACCEPT\n");
        }
        return sb.toString();
    }

    /** 套用第一層：只放行 tun 介面。 */
    public static String apply() {
        return RootShell.run(applyLines() + "echo APPLIED").combined();
    }

    /** 移除第一層，恢復任何介面都能連。互鎖：沒有第一層時自動接受不准開著，一併關掉。 */
    public static String remove() {
        String script = deleteLines()
                + "if [ -f " + FLAG_AUTOACCEPT + " ] || [ \"$(getprop ro.adb.secure)\" = 0 ]; then\n"
                + "  " + setAutoAcceptLines(false) + "\n"
                + "fi\n"
                + "echo REMOVED";
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
        return RootShell.run(setAutoAcceptLines(on)).combined();
    }

    private static String setAutoAcceptLines(boolean on) {
        return RP_PRELUDE
                + (on
                ? "touch " + FLAG_AUTOACCEPT + "; chmod 644 " + FLAG_AUTOACCEPT + "; "
                : "rm -f " + FLAG_AUTOACCEPT + "; ")
                + "[ -n \"$RP\" ] && \"$RP\" ro.adb.secure " + (on ? "0" : "1") + "; "
                + "stop adbd; sleep 1; start adbd; sleep 1; "
                + "echo AUTO=" + (on ? "ON" : "OFF")
                + " secure=$(getprop ro.adb.secure)"
                + " flag=$([ -f " + FLAG_AUTOACCEPT + " ] && echo 1 || echo 0)";
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
                        + "echo BOOTNEW=$(grep -q 'ip6tables' " + BOOT + " 2>/dev/null && echo 1 || echo 0); "
                        + "echo TUNIP=$(ip -4 -o addr show 2>/dev/null | grep -E ' tun[0-9]+ ' | grep -oE 'inet [0-9.]+' | head -1 | cut -d' ' -f2); "
                        + "echo ---IPT---; "
                        + "iptables -L INPUT -v -n --line-numbers 2>/dev/null | grep 'dpt:" + PORT + "'; "
                        + "ip6tables -L INPUT -v -n --line-numbers 2>/dev/null | grep 'dpt:" + PORT + "'";
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
                    // IPv4 與 IPv6 各一組，加總
                    if ("ACCEPT".equals(f[3])) {
                        s.allowed += pkts;
                    } else if ("DROP".equals(f[3])) {
                        s.blocked += pkts;
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
            } else if (t.startsWith("BOOTNEW=")) {
                s.bootOutdated = !t.endsWith("1");
            } else if (t.startsWith("TUNIP=")) {
                s.tunIp = t.substring(6).trim();
            }
        }
        s.bootOutdated = s.bootInstalled && s.bootOutdated;
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
                        // tun+ 不需要介面已經存在，不用等 Tailscale 起來，開機就擋
                        applyLines() +
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
