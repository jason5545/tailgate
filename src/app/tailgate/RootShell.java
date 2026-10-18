package app.tailgate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 透過 Shizuku 以 root 身分執行 shell 指令。
 * Shizuku server 以 UID 0 執行時，newProcess 直接得到 root shell。
 */
public final class RootShell {

    public static final class Result {
        public final int code;
        public final String out;
        public final String err;

        Result(int code, String out, String err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }

        public boolean ok() {
            return code == 0;
        }

        public String combined() {
            StringBuilder sb = new StringBuilder();
            if (out != null && !out.isEmpty()) sb.append(out.trim());
            if (err != null && !err.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(err.trim());
            }
            return sb.toString();
        }
    }

    private RootShell() {
    }

    /** 執行一段 shell script（可多行），回傳 stdout/stderr/exit code。 */
    public static Result run(String script) {
        Process p = null;
        try {
            p = ShizukuBridge.newRootProcess(script);
            if (p == null) {
                return new Result(-1, "", "Shizuku 未就緒或沒有權限");
            }
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            int code = p.waitFor();
            return new Result(code, out, err);
        } catch (Throwable t) {
            return new Result(-1, "", t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 執行多條指令，回傳每一條的輸出（用於狀態檢查）。 */
    public static List<String> runAll(String... cmds) {
        List<String> results = new ArrayList<>();
        for (String c : cmds) {
            Result r = run(c);
            results.add(r.combined());
        }
        return results;
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
