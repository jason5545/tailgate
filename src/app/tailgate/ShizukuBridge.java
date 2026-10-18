package app.tailgate;

import android.os.IBinder;

import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IRemoteProcess;

import java.lang.reflect.Constructor;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Shizuku 橋接層。
 *
 * Shizuku 13.x 把 Shizuku.newProcess 收成 private，所以這裡直接用 AIDL 產生的
 * IShizukuService.Stub.asInterface(binder) 呼叫 newProcess，再包成 ShizukuRemoteProcess。
 * 這條路不依賴公開包裝 API，版本相容性最好。
 */
public final class ShizukuBridge {

    private ShizukuBridge() {
    }

    public static boolean isReady() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shizuku server 的 UID；0 代表 root。 */
    public static int serverUid() {
        try {
            return Shizuku.getUid();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static int version() {
        try {
            return Shizuku.getVersion();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void requestPermission() {
        try {
            Shizuku.requestPermission(0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 開一個 root shell。Shizuku server 是 UID 0 時，這個 process 就是 root。
     *
     * @return Process，失敗回 null
     */
    public static Process newRootProcess(String script) {
        try {
            IBinder binder = Shizuku.getBinder();
            if (binder == null) return null;

            IShizukuService service = IShizukuService.Stub.asInterface(binder);
            if (service == null) return null;

            String[] cmd = new String[]{"/system/bin/sh", "-c", script};
            IRemoteProcess remote = service.newProcess(cmd, null, null);
            if (remote == null) return null;

            // ShizukuRemoteProcess 的建構子不是 public，用反射
            Constructor<ShizukuRemoteProcess> ctor =
                    ShizukuRemoteProcess.class.getDeclaredConstructor(IRemoteProcess.class);
            ctor.setAccessible(true);
            return ctor.newInstance(remote);
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        }
    }

    /** 最近一次失敗原因，給 UI 顯示。 */
    public static volatile String lastError = "";
}
