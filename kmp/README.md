# วิธีรัน Little Farm — Kotlin Multiplatform

แอปเกม **สวนหลังเลิกงาน v0.2** ใช้ Kotlin Multiplatform + Compose Multiplatform แชร์ UI และกติกาเกมบน iOS/Android ไม่ใช่ WebView และไม่ใช้ Expo

มีเลเวลปลดล็อกพืช 7 ชนิด งานต่อเนื่อง แต่งสวน สมุดพืช แมว และแจ้งเตือนแบบเลือกเปิด เล่น Guest ออฟไลน์ได้โดยไม่ต้องตั้ง Firebase ดู [กติกาและรายละเอียด v0.2](PROGRESSION-UPDATE.md)

## เตรียมเครื่อง

- **JDK 21**: launcher ใช้ JBR ที่ `/Applications/Android Studio.app/Contents/jbr/Contents/Home` โดยอัตโนมัติ หรือกำหนด `LITTLEFARM_JAVA_HOME` ให้ชี้ไป JDK ของคุณ ถ้าตำแหน่งที่เลือกไม่พบ Java จะลอง `JAVA_HOME` ต่อ
- **อินเทอร์เน็ตตอน build ครั้งแรก**: ไฟล์ติดตั้ง Gradle (distribution), dependencies และ Kotlin/Native toolchain ไม่รวมใน Git แต่มี wrapper สำหรับดาวน์โหลดให้แล้ว หลังดาวน์โหลดครบจึงค่อยเติม `--offline` ได้
- **พื้นที่ว่าง**: ควรเตรียม 10–20 GB หรือมากกว่านั้นหากยังไม่มี SDK/runtime ตัว launcher หยุด iOS/native build เมื่อพื้นที่ต่ำกว่า **5 GiB** ไม่แนะนำให้ข้าม guard เพื่อฝืน build
- **Mac desktop preview**: ไม่ต้องมี Xcode หรือ Android SDK แต่ต้องรันใน desktop session ที่แสดงหน้าต่างได้
- **iOS**: Mac Apple Silicon, Xcode และ iOS Simulator runtime โปรเจกต์มี `iosArm64` และ `iosSimulatorArm64` เท่านั้น ยังไม่มี target สำหรับ Intel Mac Simulator
- **Android**: Android Studio รุ่นที่รองรับ AGP 9.1 หรือใช้ Gradle CLI กับ Android SDK API 36, SDK Build-Tools ที่ AGP ต้องการ และ Platform-Tools

เวอร์ชันที่โค้ดกำหนด: Kotlin/Compose compiler **2.4.10**, Compose Multiplatform **1.11.1**, AGP **9.1.0**, Gradle **9.3.1**; Android minSdk **24**, compileSdk/targetSdk **36**, iOS deployment target **15.0** ไม่ต้องติดตั้ง Gradle แยก ให้ใช้ wrapper ที่อยู่ใน repo

ถ้าติดตั้ง JDK 21 แยกและ macOS มองเห็น JDK นั้น:

```sh
/usr/libexec/java_home -v 21
export LITTLEFARM_JAVA_HOME="$(/usr/libexec/java_home -v 21)"
"$LITTLEFARM_JAVA_HOME/bin/java" -version
```

ถ้าคำสั่งแรกหา JDK ไม่พบ ให้ติดตั้ง JDK 21 หรือใช้ Java ที่มากับ Android Studio ก่อน ค่า `export` มีผลใน Terminal หน้าต่างนี้ ไม่ได้แก้ shell profile และ Xcode ที่เปิดจาก Dock อาจไม่ได้รับค่านี้ ใช้คำสั่ง CLI ด้านล่างจาก Terminal เดียวกันได้

## เข้าโฟลเดอร์ให้ถูกก่อน

กรณี clone ใหม่:

```sh
git clone https://github.com/ize-050/Game-after-working-garden.git
cd Game-after-working-garden/kmp
```

กรณีมีโปรเจกต์อยู่แล้ว ให้เข้าโฟลเดอร์เกมเดิมแล้ว `cd kmp` ไม่ต้อง clone ซ้ำ **คำสั่งในส่วนถัดไปทั้งหมดรันจากโฟลเดอร์ `kmp`** เว้นแต่จะระบุเป็นอย่างอื่น

ตรวจเครื่องก่อนเริ่ม:

```sh
pwd
df -h .
./scripts/gradle.sh --version
```

คำสั่ง Gradle ครั้งแรกอาจดาวน์โหลด wrapper ก่อน แล้วแสดง Gradle/JVM ที่ใช้ launcher เก็บ cache ใน `.tooling/gradle-home` และ `.tooling/konan` ภายในโปรเจกต์

## Mac desktop preview

เปิดเล่น UI และกติกาเดียวกับมือถือในหน้าต่างบน Mac:

```sh
./scripts/gradle.sh -p verification playDesktop --no-daemon --console=plain
```

เมื่อ compile เสร็จหน้าต่าง Little Farm จะเปิด กดเล่นต่อเพื่อเข้าสวน ปิดหน้าต่างเพื่อจบโปรแกรม เปิดครั้งต่อไปจะเล่นต่อจากเซฟใน `.tooling/desktop-preview/` ถ้าต้องหยุด process ใช้ Ctrl+C ใน Terminal

นี่เป็น shared Compose/Desktop สำหรับทดสอบ **ไม่ใช่ iOS Simulator หรือแอปที่แจกติดตั้ง** เซฟและตัวเลือกเสียงแยกจากมือถือ ใช้ Guest เท่านั้น Google/Apple Login และแจ้งเตือน OS จริงไม่ทำงานใน preview นี้

## iOS Simulator

### วิธีผ่าน Xcode

1. เปิด Xcode ให้ติดตั้งส่วนประกอบเริ่มต้นและยอมรับข้อตกลงให้เรียบร้อย
2. ติดตั้ง iOS Simulator runtime ผ่านส่วนจัดการ Components/Platforms ของ Xcode ถ้ามี runtime ที่ใช้ได้อยู่แล้ว ไม่ต้องดาวน์โหลดซ้ำ
3. ตรวจว่า command-line tools ชี้ไป Xcode ที่ต้องการ:

   ```sh
   xcode-select -p
   xcodebuild -version
   ```

   ถ้าชี้ไป `/Library/Developer/CommandLineTools` หรือ Xcode คนละชุด ให้เลือก Xcode ที่ถูกใน **Xcode → Settings → Locations → Command Line Tools**

4. เปิดโปรเจกต์:

   ```sh
   open iosApp/iosApp.xcodeproj
   ```

5. เลือก scheme **LittleFarm** และ iPhone Simulator ที่ติดตั้งไว้ ไม่เลือก Generic iOS Device หรือ iPhone จริงในขั้นตอนนี้
6. กด **Run (⌘R)** รอ build แล้วแอปจะเปิดใน Simulator ใช้ Guest ได้โดยไม่ตั้ง Firebase หรือ Signing Team สำหรับ Simulator

Xcode มี build phase เรียก `scripts/gradle.sh :composeApp:embedAndSignAppleFrameworkForXcode` อยู่แล้ว ไม่ต้องสร้าง Shared framework ด้วยมือ และ launcher เลือก `littlefarm.iosOnly=true` อัตโนมัติ จึงไม่ต้องมี Android SDK เพื่อรัน iOS

### วิธีผ่าน Terminal บน Apple Silicon

แสดงเครื่องจำลองที่มีอยู่ เลือก **UDID** ของ iPhone ที่ต้องการจากผลลัพธ์:

```sh
xcrun simctl list devices available
```

แทนที่ `PASTE_DEVICE_UDID` ก่อนรัน และใช้ Terminal หน้าต่างเดิมสำหรับคำสั่งต่อไป:

```sh
FARM_SIMULATOR_ID="PASTE_DEVICE_UDID"
open -a Simulator
xcrun simctl bootstatus "$FARM_SIMULATOR_ID" -b
```

`bootstatus -b` จะ boot ถ้ายังไม่ได้เปิดและรอจนพร้อม จากนั้น build → install → launch ตามลำดับ โดย `&&` จะไม่ติดตั้ง binary เก่าถ้า build รอบนี้ล้มเหลว:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -target LittleFarm \
  -configuration Debug -sdk iphonesimulator ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CONFIGURATION_BUILD_DIR="$PWD/.tooling/ios-build" \
  CODE_SIGNING_ALLOWED=NO COMPILER_INDEX_STORE_ENABLE=NO build && \
xcrun simctl install "$FARM_SIMULATOR_ID" "$PWD/.tooling/ios-build/LittleFarm.app" && \
xcrun simctl launch "$FARM_SIMULATOR_ID" com.littlefarm.game
```

คำสั่งนี้ใช้ SDK ของ Xcode ที่เลือกอยู่ ไม่ล็อกเลข SDK ผลลัพธ์คือ `.tooling/ios-build/LittleFarm.app` ส่วนการกด Run ผ่าน Xcode ปกติใช้โฟลเดอร์ DerivedData ของ Xcode

ไฟล์นี้เป็น **Simulator app** ไม่ใช่ไฟล์สำหรับลง iPhone จริง ถ้าจะรันบน iPhone ให้เลือก device ใน Xcode ตั้ง Bundle ID/Signing Team ของตนเอง และเตรียมสิทธิ์/การตั้งค่า developer ของอุปกรณ์ก่อน ไม่ใช้คำสั่ง `CODE_SIGNING_ALLOWED=NO` ของ Simulator ไปติดตั้งบนเครื่องจริง

ถ้าต้องการตรวจเฉพาะ shared iOS framework ไม่ใช่ทั้งแอป:

```sh
./scripts/gradle.sh -Plittlefarm.iosOnly=true :composeApp:linkDebugFrameworkIosSimulatorArm64 --no-daemon --console=plain
```

## Android

1. เปิดโฟลเดอร์ `kmp` ใน Android Studio ที่รองรับ AGP 9.1 และเลือก Gradle JDK 21
2. ใน SDK Manager ติดตั้ง **Android SDK Platform API 36**, SDK Build-Tools ที่ AGP ต้องการ, Platform-Tools; เพิ่ม Android Emulator และ system image หากจะใช้เครื่องจำลอง
3. ถ้ายังไม่มี `local.properties` ให้สร้าง SDK path local (ถ้ามีอยู่แล้วให้เปิดแก้เท่านั้น):

   ```sh
   test -f local.properties || cp local.properties.example local.properties
   open -e local.properties
   ```

   เปลี่ยน `sdk.dir` เป็น path จริงที่ SDK Manager แสดง เช่น `/Users/YOUR_USERNAME/Library/Android/sdk` ต้องแทน `YOUR_USERNAME` ไฟล์นี้ถูก Git ignore อยู่ ไม่ commit

4. เปิด emulator จาก Device Manager หรือเชื่อมมือถือที่เปิด USB debugging และยืนยันการเชื่อมต่อบนมือถือ
5. เลือก run configuration/module **androidApp** และอุปกรณ์ แล้วกด Run

หรือสร้าง APK ผ่าน Terminal:

```sh
./scripts/gradle.sh :androidApp:assembleDebug --no-daemon --console=plain
```

APK อยู่ที่ `androidApp/build/outputs/apk/debug/androidApp-debug.apk` ถ้ามี emulator/มือถือที่เชื่อมต่อแล้วให้ใช้:

```sh
./scripts/gradle.sh :androidApp:installDebug --no-daemon --console=plain
```

คำสั่ง `installDebug` ติดตั้งแอป แต่ไม่ได้เปิดเกมให้เสมอไป ให้แตะไอคอนเกมบนอุปกรณ์ หรือกด Run จาก Android Studio เพื่อเปิดด้วย

build ค่าเริ่มต้นเป็น Guest/offline ไม่มี Firebase config ก็ build ได้ ไม่ต้องใช้ `-Plittlefarm.firebase=true` จนกว่าจะตั้งค่าบริการจริงครบ หาก Gradle แจ้ง `SDK location not found` ให้กลับไปตรวจ `local.properties` ไม่ต้องเพิ่ม Firebase เพื่อแก้ข้อผิดพลาดนี้

## ทดสอบและดูภาพ UI

ใช้ source และ resources ชุดเดียวกับแอป โดยไม่ต้องมี iOS/Android SDK:

```sh
./scripts/gradle.sh -p verification jvmTest smokeUi renderScreens --no-daemon --console=plain
```

- `jvmTest`: กติกาเกม เซฟ การย้ายข้อมูล บัญชีจำลอง ซื้อขาย เสียง และ logic แจ้งเตือน
- `smokeUi`: กดใช้งานผ่าน Compose semantics และตรวจผลในเซฟ ไม่ใช่การกดบนมือถือจริง
- `renderScreens`: สร้างภาพจริงจาก Compose หลายขนาดไว้ใน `verification/build/screenshots/`

เปิดรายงานและแกลเลอรีหลังคำสั่งสำเร็จ:

```sh
open verification/build/reports/tests/jvmTest/index.html
open preview/index.html
```

แกลเลอรีเป็นภาพนิ่ง **กดเล่นเกมไม่ได้** และภาพ generated ไม่อยู่ใน Git ถ้า clone ใหม่ต้อง `renderScreens` ก่อน ไม่ควรเปิด HTTP server ครอบโฟลเดอร์ KMP ทั้งหมด เพราะอาจเปิดให้เข้าถึง source/config ที่ไม่ใช่รูปภาพ

เมื่อเคยดาวน์โหลด dependencies ของงานนั้นครบแล้ว หากต้องการไม่ใช้อินเทอร์เน็ตให้เติม `--offline` เช่น:

```sh
./scripts/gradle.sh -p verification jvmTest --offline --no-daemon --console=plain
```

## เซฟ บัญชี และแจ้งเตือน

- Desktop preview เก็บเซฟใน `.tooling/desktop-preview/`; iOS ใช้ UserDefaults; Android ใช้ SharedPreferences เซฟของแต่ละแพลตฟอร์มไม่ใช่ไฟล์เดียวกัน
- สวน Guest และสวนแต่ละบัญชีแยกกันในเครื่อง เซฟ v1 ย้ายเป็น v2 ได้ แต่ไม่ควรนำเซฟ v2 กลับไปเปิดด้วยโค้ดเก่า
- ยังไม่ต้องเปิด Firebase เพื่อเล่นเกม ปุ่ม Google/Apple จะไม่ล็อกอินจริงจนกว่าจะตั้ง SDK/config ตาม [FIREBASE-SETUP.md](FIREBASE-SETUP.md)
- แจ้งเตือนเก็บเกี่ยวปิดไว้ก่อน เปิดได้จากตั้งค่าในแอปมือถือและอนุญาตสิทธิ์ OS เป็น local notification ไม่ต้องมี server และ Android อาจส่งช้าจากการประหยัดพลังงาน
- อย่า commit `.env`, `GoogleService-Info.plist`, `google-services.json`, service-account หรือ signing keys
- อย่า erase Simulator, ถอนแอป หรือกดเริ่มสวนใหม่เพื่อแก้ build โดยไม่ตั้งใจ เพราะอาจทำให้เซฟหาย

## ปัญหาที่พบบ่อย

| อาการ | วิธีตรวจ/แก้ |
| --- | --- |
| `Set LITTLEFARM_JAVA_HOME to a JDK 21 directory` | ติดตั้ง JDK 21/Android Studio หรือกำหนด JDK path จริง ตรวจ `bin/java -version` |
| Xcode หา Java ไม่เจอ แต่ Terminal รันได้ | Xcode จาก Dock อาจไม่ได้รับ `export`; ใช้ CLI จาก Terminal ที่ตั้งค่าแล้ว หรือใช้ JBR ของ Android Studio ในตำแหน่งมาตรฐาน |
| `Native build paused: less than 5 GiB free` | คืนพื้นที่ก่อน ตรวจ `df -h .`; อย่าฝืนปิด guard หรือดาวน์โหลด runtime เพิ่ม |
| หา dependency ไม่เจอใน offline mode | เอา `--offline` ออกและต่ออินเทอร์เน็ตเพื่อดาวน์โหลดครั้งแรก |
| `SDK location not found` ใน Android | ตรวจ `local.properties`, SDK path และการติดตั้ง API 36 |
| ไม่มี iPhone ให้เลือก | ตรวจ runtime ใน Xcode และเลือก Simulator บน Apple Silicon; โปรเจกต์นี้ยังไม่มี `iosX64` |
| Simulator เปิดแล้วแต่ยังเป็นเกมรุ่นเก่า | ต้อง build และติดตั้ง binary ใหม่ให้สำเร็จ ไม่ใช่แค่ `simctl launch` แอปเดิม |
| preview รูปไม่ขึ้น | รัน `renderScreens` ก่อน รูปใน `build/` ไม่รวมใน repository |
| เปิด preview แล้วกดเล่นไม่ได้ | เป็นภาพนิ่ง ให้ใช้ `playDesktop` หรือ Run แอปมือถือ |
| Login หรือแจ้งเตือนไม่ทำงานบน desktop | preview ใช้ Guest และไม่มี OS notification adapter จริง ไม่ใช่ข้อผิดพลาดของเซฟ |

## ขอบเขตการตรวจล่าสุด

22 กันยายน 2026: shared v0.2 ผ่าน **104 tests**, ชุดกด UI และภาพ **50 ภาพ** รุ่นก่อน v0.2 เคย build/install/เปิดบน iPhone 17 Simulator iOS 26.3.1 ด้วย Xcode 26.3 แล้ว แต่ **native v0.2 ยังไม่ได้ rebuild** เพราะพื้นที่ต่ำกว่า guard และ Android SDK ยังไม่พร้อม คำสั่ง native ในคู่มือนี้จึงไม่ใช่การอ้างว่าทดสอบ v0.2 บนมือถือครบแล้ว

ยังต้องตรวจ native compile และการส่งแจ้งเตือนจริงก่อนเผยแพร่ มี linker warning เดิมเกี่ยวกับ ICU ที่สร้างสำหรับ Simulator 18.5 เทียบกับ deployment 15.0 จึงยังไม่รับรอง iOS รุ่นเก่า อ่าน [สถานะเครื่อง](SETUP-STATUS.md) และ [ผลตรวจ v0.2 / checklist](PROGRESSION-UPDATE.md)

## โครงสร้างและเอกสาร

```text
composeApp/src/commonMain/   UI, game engine, save codec, notifications controller
composeApp/src/commonTest/  unit tests ของ shared game
composeApp/src/iosMain/      iOS host bridge, UserDefaults, clock, audio
composeApp/src/androidMain/ SharedPreferences, clock, audio
androidApp/                 Android entry point และ notification adapter
iosApp/                     SwiftUI host, Xcode project, notification adapter
verification/               JVM tests, UI smoke, renderer, desktop preview
preview/                    HTML gallery ของภาพที่สร้างจาก Compose
backend/                    Firestore rules/tests ที่เตรียมไว้ ยังไม่ deploy
scripts/gradle.sh            launcher ใช้ JDK และ cache เฉพาะโปรเจกต์
```

- [README หลัก / HTML prototype รุ่นเก่า](../README.md)
- [PRD](../PRD.md) · [รายละเอียด v0.2](PROGRESSION-UPDATE.md)
- [ดีไซน์และข้อแตกต่างจาก Stitch](DESIGN-PARITY.md) · [Google Stitch](https://stitch.withgoogle.com/projects/11043770835758050762)
- [Firebase](FIREBASE-SETUP.md) · [iOS Firebase](iosApp/FIREBASE-SETUP.md) · [Android Firebase](androidApp/FIREBASE-SETUP.md)

ค่าเริ่มต้น Bundle ID คือ `com.littlefarm.game` ต้องเลือก ID และ Signing Team ของตนเองก่อนแจกแอปจริง ยังไม่ได้ตั้งการเผยแพร่ผ่าน App Store หรือ TestFlight
