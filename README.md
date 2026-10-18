# Tailgate

> 只為你的 tailnet 開的門。ADB 5555 只放行 Tailscale，其他人一律擋掉。

一個用 Shizuku 執行的小工具，把 Android 的無線 ADB 鎖在 Tailscale 網段內，並可選擇讓 tailnet 內的機器免授權直接連線。

不用 root、不用 KernelSU 模組，只要 Shizuku 以 root 身分執行（Shizuku 從 root 啟動時 server 就是 UID 0）。

**[⬇ 下載 APK](https://github.com/jason5545/tailgate/releases/latest)** · Android 8.0+ · 57 KB

---

## 問題

`adb tcpip 5555` 一開，`adbd` 會綁在 `[::]:5555`，也就是**所有網路介面**。同一個 Wi-Fi 下的人、電信網段的掃描器、任何拿得到你 IP 的人，都能發起 ADB 連線。你能靠的只有「授權對話框」這道防線，而它會問你每一次。

Tailscale 只解決了「怎麼連得到」，沒解決「誰連得到」。

## 解法

兩層，各自負責一半：

| 層 | 做什麼 | 效果 |
|---|---|---|
| **iptables** | `tun0` 的 5555 放行，其餘全部 DROP | 只有 tailnet 內的封包進得來 |
| **`ro.adb.secure=0`** | 讓 `adbd` 根本不發授權請求 | 連進來就直接可用，不用再按允許 |

兩層合起來，才等於「只有我的 tailnet 能連，而且連了就直接進」。

---

## 安全互鎖（重要）

App 裡的「自動接受連線」開關**預設是鎖住的**，必須先開啟「只放行 Tailscale」才能開。

理由很直接：`ro.adb.secure=0` 等於**無密碼的 root shell**。如果沒有 iptables 那層擋著，就是對全網開放 root 存取。這個順序不能反，App 也強制不讓你反。

如果你只需要「擋來源」但不在意每次連線要按允許，只開第一層就好，這是預設狀態。

---

## 畫面

主畫面就是狀態本身：

- **防護中** / **未防護** — 一眼看目前有沒有在擋
- `Tailscale 100.x.x.x` · `埠 5555` · `開機自動 ✓` — 三個狀態膠囊
- `tun0 放行 N 包 · 攔截 N 包` — iptables 的實際計數，不是估算
- 兩個開關：只放行 Tailscale、自動接受連線
- 一顆主要動作按鈕 + 開機腳本按鈕

Material 3 Expressive 風格：tonal palette、彈性按壓回饋、圓角卡片。深淺色自動跟隨系統。

## 語言

內建四種語言，跟隨系統設定（Android 13+ 也可在系統設定裡單獨指定本 App 的語言）：

| 語言 | 資料夾 |
|---|---|
| English（預設） | `res/values/` |
| 繁體中文 | `res/values-zh-rTW/` |
| 简体中文 | `res/values-zh-rCN/` |
| 日本語 | `res/values-ja/` |

預設語言是英文，因為這是公開專案。要加新語言就在 `res/values-<locale>/strings.xml` 放一份翻譯，四個語言檔各有 60 條字串，缺漏的會自動退回英文。

---

## 需求

- Android 8.0（API 26）以上
- [Shizuku](https://shizuku.rikka.app/) **以 root 身分啟動**（`shizuku_server` 的 UID 必須是 0）
  - 從 Magisk / KernelSU 啟動 Shizuku，或用 `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
  - Shizuku 以 shell 身分啟動的話改不了 iptables，App 會直接告訴你
- Tailscale 已登入並連上
- 開發者選項裡的 USB 偵錯已開

## 安裝

從 [Releases](https://github.com/jason5545/tailgate/releases/latest) 下載 `tailgate-*.apk` 直接安裝（需要允許安裝未知來源）。

或自己建置：

```bash
# 建置
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk
./build.sh

# 安裝
adb install -r tailgate.apk
```

> 發布的 APK 是 **debug 簽章**。自己重新建置會產生不同的簽章，要從舊版升級得先解除安裝。

## 使用

1. 開啟 Tailgate，按「取得 Shizuku 授權」並允許
2. 按「立即啟用防護」— 套用 iptables 規則
3. 按「寫入開機自動套用」— 讓它撐過重開機
4. 想要免按允許，再開「自動接受連線」

從電腦連線：

```bash
adb connect <你的-tailscale-ip>:5555
```

---

## 它是怎麼做的

### 為什麼不用 root 模組

一開始的版本是 KernelSU 模組，靠 `/data/adb/service.d/` 的開機腳本套 iptables。能用，但有幾個缺點：要 root、要重開機才生效、狀態看不到、改設定要手動編輯檔案。

改用 Shizuku 之後，這些問題一次解決：不用 root 框架、改設定即時生效、狀態直接讀 iptables 顯示。**Shizuku 從 root 啟動時，它的 server 就是 UID 0**，透過 `IShizukuService.newProcess()` 開出來的 shell 直接是 root shell，能做跟 root 模組一樣的事。

### 為什麼自己刻 UI

沒有拉 AppCompat / Material Components。這台建置機沒有 Gradle，手動解析 AAR 的遞迴依賴風險太高，而且為了幾個圓角和開關拉進整個 Material 函式庫不划算。

所以照 M3 Expressive 的規範手刻：tonal palette（`primaryContainer`、`surfaceContainerHigh` 等）、`RippleDrawable` 漣漪、`OvershootInterpolator` 彈性回饋、28dp 圓角。**APK 只有 45 KB。**

### Shizuku API 的坑

Shizuku 13.x 把 `Shizuku.newProcess()` 從 public 收成 private。本專案的做法是直接用 AIDL 產生的 `IShizukuService.Stub.asInterface(binder)` 呼叫 `newProcess()`，再反射包成 `ShizukuRemoteProcess`。這條路不依賴公開包裝 API，版本相容性最好。

`moe.shizuku.server.*` 這組 AIDL **不在** Maven 上的 `shizuku-api` AAR 裡，要從 [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 抓原始 AIDL 自己編。`build.sh` 會處理，但要注意兩件事：

- `aidl` 要求所有依賴檔一併當輸入傳入，不能只給主檔
- framework 型別（`Bundle`、`Intent`）要自己補 stub aidl，`IBinder` 則是內建的不能宣告

### iptables 的順序

```
iptables -I INPUT -p tcp --dport 5555 -j DROP          # 先插 DROP
iptables -I INPUT 1 -i tun0 -p tcp --dport 5555 -j ACCEPT  # 再把 ACCEPT 插到最前面
```

順序反了會把自己鎖在外面。每條指令都先 `-D` 再 `-I`，所以重複執行不會疊規則。

---

## 開機自動套用

寫入 `/data/adb/service.d/adbguard.sh`，開機時：

1. 等 `sys.boot_completed`
2. 設 `persist.adb.tcp.port=5555`
3. 等 `tun0` 出現（最多 120 秒）
4. 重套 iptables 規則
5. **只有在旗標檔 `/data/adb/adbguard_autoaccept` 存在時**才把 `ro.adb.secure` 設 0

第 5 點的條件很重要：你把自動接受關掉，重開機後它不會被偷偷打開。

## 已知限制

- **`ro.adb.secure` 是 runtime 值**，靠 `resetprop` 改，不是 persist 分區。重開機後要靠開機腳本重新套用，所以那個腳本不能刪。
- **只處理 IPv4。** IPv6 的 5555 沒有擋。如果你的電信商給了公網 IPv6 位址，那是個缺口。要補的話 `ip6tables` 同樣兩條規則。
- **不在 tailnet 上就完全進不來。** 這是設計，但重開機後如果 Tailscale 沒起來，只能靠 USB。
- **`resetprop` 的路徑**：預設找 `/data/adb/ksu/bin/resetprop`，找不到退回 `command -v`。Magisk 環境的路徑不同但也能找到。
- 沒有測試套件。這是個人在用的工具，驗證靠實際連線測試（見下）。

## 驗證方式

改完之後這樣確認：

```bash
# 1. 規則在不在
adb shell su -c 'iptables -L INPUT -v -n --line-numbers | head -4'
# 預期：ACCEPT tun0 dpt:5555 在前，DROP dpt:5555 在後

# 2. 免授權連線（殺掉 server 模擬全新 client）
adb kill-server && adb start-server && adb connect <ip>:5555
adb shell 'echo OK; id -u'
# 預期：OK / 0，中間不跳任何授權對話框

# 3. 非 tun0 來源被擋
adb shell su -c 'nc -z -w 3 <手機自己的公網IP> 5555; echo $?'
# 預期：Timeout，且 DROP 計數會增加
```

## 專案結構

```
AndroidManifest.xml          單一 Activity，宣告 Shizuku provider
build.sh                     aapt2 → javac → d8 → zipalign → apksigner
aidl/                        Shizuku 的 AIDL（從 Shizuku-API 取得）
  android/os/Bundle.aidl     framework 型別 stub
  android/content/Intent.aidl
  moe/shizuku/server/*.aidl  IShizukuService 等
libs/                        Shizuku API / provider AAR
res/values/                  英文字串（預設）
res/values-zh-rTW/           繁體中文
res/values-zh-rCN/           简体中文
res/values-ja/               日本語
src/app/tailgate/
  MainActivity.java          UI 與事件
  Rules.java                 iptables / prop / 開機腳本
  RootShell.java             透過 Shizuku 執行 shell
  ShizukuBridge.java         Shizuku binder 橋接
  Ui.java                    M3 Expressive 元件工廠
```

## 授權

MIT

## 致謝

- [Shizuku](https://github.com/RikkaApps/Shizuku) — 沒有它就得靠 root 框架
- [Tailscale](https://tailscale.com/) — 讓「我的網段」這件事變得可定義
