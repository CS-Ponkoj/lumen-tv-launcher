#!/bin/sh
# Builds My Home (TV launcher) without Gradle. Output: build/myhome.apk
set -e
cd "$(dirname "$0")"
SDK="$LOCALAPPDATA/Android/Sdk"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-30/android.jar"
export JAVA_HOME="$(ls -d '/c/Program Files/Microsoft/'jdk-17* | head -1)"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf build && mkdir -p build/res build/classes build/dex

"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml build/res.zip --java build/gen

javac -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath "$JAR;$BT/core-lambda-stubs.jar" -d build/classes \
  $(find src build/gen -name '*.java') 2>&1 | grep -v 'warning: \[options\]' || true

"$BT/d8.bat" --min-api 28 --release --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && jar uf ../unsigned.apk classes.dex)

"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
[ -f my-release.keystore ] || keytool -genkeypair -keystore my-release.keystore -alias myhome \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass myhomepass -keypass myhomepass \
  -dname "CN=My Home" >/dev/null 2>&1
"$BT/apksigner.bat" sign --ks my-release.keystore --ks-pass pass:myhomepass --out build/myhome.apk build/aligned.apk
ls -l build/myhome.apk
