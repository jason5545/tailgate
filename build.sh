#!/usr/bin/env bash
set -euo pipefail

# ---- 設定 ----
PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/36.0.0"
PLATFORM="$SDK/platforms/android-36/android.jar"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
JAVAC="$JAVA_HOME/bin/javac"
KEYSTORE="$PROJ/debug.keystore"
OUT="$PROJ/build"

echo "== 清理 =="
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/res" "$OUT/aidlgen" "$OUT/libs"

echo "== 準備 Shizuku 依賴 =="
# libs/*.aar 進版控，裡面的 classes.jar 每次建置現抽，不讓編譯中間物進 repo。
for name in api provider; do
  aar="$PROJ/libs/$name.aar"
  [ -f "$aar" ] || { echo "缺少 $aar"; exit 1; }
  mkdir -p "$OUT/libs/$name"
  unzip -o -q "$aar" classes.jar -d "$OUT/libs/$name"
done
API_JAR="$OUT/libs/api/classes.jar"
PROV_JAR="$OUT/libs/provider/classes.jar"
echo "   api $(unzip -l "$API_JAR" | grep -c '\.class') classes, provider $(unzip -l "$PROV_JAR" | grep -c '\.class') classes"

echo "== 編譯 Shizuku AIDL =="
# Shizuku 的 AAR 不含 moe.shizuku.server.* 的 AIDL stub，要自己從 Shizuku-API 源碼編。
# aidl 要求所有依賴檔一併當輸入傳入，且 framework 型別要提供 stub aidl。
"$BT/aidl" -I "$PROJ/aidl" -o "$OUT/aidlgen" --lang=java \
  "$PROJ/aidl/android/os/Bundle.aidl" \
  "$PROJ/aidl/android/content/Intent.aidl" \
  "$PROJ/aidl/moe/shizuku/server/IRemoteProcess.aidl" \
  "$PROJ/aidl/moe/shizuku/server/IShizukuApplication.aidl" \
  "$PROJ/aidl/moe/shizuku/server/IShizukuServiceConnection.aidl" \
  "$PROJ/aidl/moe/shizuku/server/IShizukuService.aidl"
AIDL_COUNT=$(find "$OUT/aidlgen" -name '*.java' | wc -l)
echo "   AIDL 產生 $AIDL_COUNT 個 java"
[ "$AIDL_COUNT" -ge 4 ] || { echo "AIDL 編譯失敗"; exit 1; }

echo "== aapt2 編譯資源 =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res/compiled.zip"

echo "== 連結資源 + manifest =="
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --auto-add-overlay \
  "$OUT/res/compiled.zip"

echo "== 編譯 Java =="
find "$PROJ/src" "$OUT/gen" "$OUT/aidlgen" -name '*.java' > "$OUT/sources.txt"
wc -l < "$OUT/sources.txt" | xargs echo "   java 檔數："
"$JAVAC" -source 11 -target 11 \
  -classpath "$PLATFORM:$API_JAR:$PROV_JAR" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt" 2>&1 | grep -vE 'bootstrap class path|unknown enum constant|class file for androidx|reason:|location of system modules|--release 11 is recommended' || true

echo "== d8 轉 dex =="
# 注意：AAR 抽出來的 classes.jar 本身就是合法 jar，直接餵給 d8。
# 不要把它再包一層 jar，d8 看不到裡面，會導致 ShizukuProvider ClassNotFound。

"$BT/d8" \
  --release \
  --min-api 26 \
  --lib "$PLATFORM" \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class') \
  "$API_JAR" \
  "$PROV_JAR"

echo "== 打包 =="
cp "$OUT/base.apk" "$OUT/unsigned.apk"
# d8 可能產出 classes.dex + classes2.dex，全部塞進去
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes*.dex)

# 驗證 ShizukuProvider 真的進了 dex（用 -c 而非 -q，避免管道 SIGPIPE 誤判）
SHZ_CNT=$("$BT/dexdump" -f "$OUT/dex/classes.dex" 2>/dev/null | grep -c "Lrikka/shizuku/ShizukuProvider;" || true)
if [ "${SHZ_CNT:-0}" -lt 1 ]; then
  echo "錯誤：ShizukuProvider 沒進 classes.dex（比對數=$SHZ_CNT）"
  exit 1
fi
echo "   ShizukuProvider 已確認在 dex 內（$SHZ_CNT 筆）"

# 驗證 AIDL stub 也進了 dex，否則執行期會 ClassNotFoundException
AIDL_CNT=$("$BT/dexdump" -f "$OUT/dex/classes.dex" 2>/dev/null | grep -c "Lmoe/shizuku/server/IShizukuService\$Stub;" || true)
if [ "${AIDL_CNT:-0}" -lt 1 ]; then
  echo "錯誤：IShizukuService\$Stub 沒進 classes.dex"
  exit 1
fi
echo "   IShizukuService\$Stub 已確認在 dex 內"

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== 簽章 =="
if [ ! -f "$KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -v \
    -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$PROJ/tailgate.apk" \
  "$OUT/aligned.apk"

"$BT/apksigner" verify --print-certs "$PROJ/tailgate.apk" | head -5
echo "== 完成：$PROJ/tailgate.apk =="
ls -l "$PROJ/tailgate.apk"
