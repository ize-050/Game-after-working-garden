# สวนหลังเลิกงาน — Kotlin Multiplatform

แอป native MVP ของ Little Farm ใช้ **Kotlin Multiplatform + Compose Multiplatform** แชร์ระบบเกมและ UI ระหว่าง iOS/Android ไม่ใช่ WebView และไม่ใช้ Expo

## อัปเดต v0.2

เพิ่มครบ 6 ระบบ: XP จากเก็บเกี่ยวและส่งงาน, พืชใหม่ปลดล็อกเลเวล 2–4, งานเพื่อนบ้านหมุนเวียน, ของแต่งสวน 5 จุด, สมุดพืชพร้อมตราและรางวัล, แมวตั้งชื่อ/ลูบหัว/ปลดล็อกท่า และแจ้งเตือนรวมแบบเลือกเปิด ย้ายเซฟ schema 1 ไป 2 โดยเก็บสวนเดิมไว้ อ่าน [PROGRESSION-UPDATE.md](PROGRESSION-UPDATE.md) สำหรับกติกาและข้อจำกัด ภาพ preview เพิ่มหน้าแต่งสวน สมุดพืช แมว และสวนที่แต่งแล้ว

## สิ่งที่ทำแล้ว

- หน้าฟาร์มภาษาไทย แปลง 6 ช่อง พืช 7 ชนิด ร้านเมล็ด ตลาด กระเป๋า หมู่บ้าน งาน อัปเกรด แต่งสวน สมุดพืช แมว และตั้งค่า
- ไถ → ปลูก → รดน้ำ → รอ timestamp → เก็บ พร้อมปุ่มทดลองเวลา +5 นาที
- เซฟ JSON มี schema/validation; iOS ใช้ UserDefaults, Android ใช้ SharedPreferences
- กติกาเกม pure Kotlin แยกจาก UI/storage; มีชุดทดสอบเกม เซฟ บัญชี การซื้อขาย feedback และแจ้งเตือน รายงานรอบล่าสุดอยู่ใน [PROGRESSION-UPDATE.md](PROGRESSION-UPDATE.md)
- เลือกจำนวนซื้อ/ขาย แสดงยอดรวมและเหรียญที่ขาด จำแปลงเมื่อไปซื้อเมล็ด และแตะผักที่ยังแห้งเพื่อรดน้ำได้ทันที
- แสดงวัน/เลเวล/XP; ดนตรีและเสียงสังเคราะห์ต้นฉบับ พร้อมตัวเลือกเสียงและลดการเคลื่อนไหวที่บันทึกแยกจากเซฟสวน
- เอฟเฟกต์ไถ/ปลูก/รดน้ำ/เก็บเกี่ยว แมวขยับ และปุ่มตอบสนอง; ตัวหนังสือรองอย่างน้อย 13sp
- iOS host มี Xcode project และ scheme `LittleFarm`; Android ใช้ Android-KMP library plugin รุ่นใหม่
- ปรับภาพและ UI ตามสไตล์ Stitch: ฉากฟาร์ม/หมู่บ้านการ์ตูน, ป้ายไม้, ปุ่มมีมิติ, ภาพผักและแปลง Canvas ตาม state จริง, ฟอนต์ Noto Sans Thai
- เพิ่มหน้าบัญชี, Guest/UID-separated local saves, conflict confirmation, และเตรียม native Firebase Google/Apple + Firestore adapters แบบเปิดใช้ภายหลัง อ่าน [FIREBASE-SETUP.md](FIREBASE-SETUP.md)

นี่คือ MVP v0.2.0 ใช้ภาพต้นฉบับ Stitch ร่วมกับฉากเดิมและภาพวาด native ไม่ใช่การ export แบบ pixel-identical และยังไม่ใช่เกมพร้อมเผยแพร่ **Login/Cloud Save เตรียมโค้ดแล้วแต่ยังไม่ได้เชื่อม Firebase จริง** เสียงและการแจ้งเตือนยังต้องตรวจ lifecycle/การส่งจริงบนมือถือ ยังไม่มีฤดูกาลหรือการชำระเงินจริง

## สถานะ Simulator รอบก่อนเพิ่ม v0.2

22 กันยายน: ติดตั้ง iOS 26.3.1 arm64, build `.app` ดีไซน์ล่าสุด, install และ **เปิดเกมบน iPhone 17 Simulator สำเร็จแล้ว** ยืนยันหน้าสวนจาก [ภาพ Simulator จริง](verification/build/screenshots/ios-simulator-farm.png) ไม่ใช่ gallery/JVM render หลังหยุด build daemon พื้นที่คืนมาและรันได้ เหลือประมาณ 3.7 GiB ควรคืนพื้นที่เพิ่มก่อน build/ติดตั้งรอบต่อไป รายละเอียดใน [SETUP-STATUS.md](SETUP-STATUS.md)

## ผลตรวจดีไซน์รอบก่อนเพิ่ม v0.2

รอบเก็บดีไซน์ 22 กันยายน: ใช้ภาพต้นฉบับ Stitch ในหน้าเริ่ม/หมู่บ้าน/ร้านค้า เพิ่มแผนที่กดได้ เครื่องมือฟาร์ม กระดานไม้ ภาพอัปเกรดก่อน–หลัง และหน้าต่างเมล็ด/เติบโต อ่านรายการและข้อแตกต่างที่ยังเหลือใน [DESIGN-PARITY.md](DESIGN-PARITY.md) ไม่ใช่การยืนยัน pixel-identical หรือการรันบน iPhone จริง

- `./scripts/gradle.sh -p verification smokeUi renderScreens jvmTest`: ผ่าน ทั้งปุ่มของ Compose จริงและ 75 unit tests; auth/cloud/audio ในชุดทดสอบใช้ fake boundary ไม่ใช่การทดสอบบริการหรือเสียงบนมือถือจริง
- สร้างภาพ 13 หน้าจอ/สถานะ × 2 ขนาด (393×852 และ 320×640) รวมหน้าบัญชี และภาพหลังเลื่อนถึงท้ายหน้าอีก 10 ภาพ รวม 36 ภาพจาก shared Compose จริง ไม่ใช่ภาพ mockup HTML
- รูปอยู่ใน `verification/build/screenshots/`; เปิดไฟล์ PNG ใน Codex ได้โดยตรง แกลเลอรี `preview/index.html` ใช้ภาพเหล่านี้และเป็นภาพนิ่งเท่านั้น ไม่ควรเปิด HTTP server ครอบทั้งโฟลเดอร์โปรเจกต์ เพราะจะเปิดให้เข้าถึง source/config ที่ไม่ใช่ภาพด้วย
- โค้ด UI/เสียง iOS compile และ link ผ่านแล้ว; full `.app` รุ่นล่าสุด build ผ่านหลังแก้ callback Swift/Kotlin เป็น `Void` และเปิดหน้าสวนบน iPhone 17 Simulator ได้แล้ว ยังไม่ใช่การทดสอบบน iPhone เครื่องจริงหรือครบทุกระบบ

รายละเอียดรอบ MVP: [MVP-UPDATE.md](MVP-UPDATE.md) · ภาพ, prompt และขอบเขต QA เดิม: [VISUAL-UPDATE.md](VISUAL-UPDATE.md)

## ทดลองเล่น shared game บน Mac

```sh
./scripts/gradle.sh -p verification playDesktop --offline --no-daemon --console=plain
```

นี่คือหน้าต่างทดสอบ Compose/Desktop จาก UI และกติกาเดียวกับมือถือ **ไม่ใช่ iOS Simulator** และไม่ใช่ HTML gallery ใช้ Guest เท่านั้น; เซฟและตัวเลือกเสียงแยกไว้ใน `.tooling/desktop-preview/` ไม่อ่านหรือแก้เซฟของ iOS/Android ปิดหน้าต่างเพื่อหยุดโปรแกรม เปิดอีกครั้งเพื่อเล่นต่อ หากอุปกรณ์เสียงไม่พร้อมเกมยังเล่นได้และมีข้อความแจ้งในตั้งค่า

## สถานะเครื่อง / ก่อนเริ่ม build

**iOS app รุ่นก่อนเพิ่ม v0.2 build ผ่านแล้ว** วันที่ 22 กันยายน 2026: คอมไพล์และ link Kotlin/Native framework พร้อมประกอบ SwiftUI host เป็น `.tooling/ios-build/LittleFarm.app` สำหรับ iOS Simulator arm64 ด้วย Xcode 26.3 รวมดีไซน์และ Guest/account boundary ในขณะนั้น ยังไม่ได้ rebuild native binary ของส่วนขยาย v0.2 เพราะพื้นที่ต่ำกว่า guard 5 GiB

ก่อนติดตั้ง runtime มีพื้นที่ว่างประมาณ **18 GiB** หลัง first boot เคยเหลือไม่ถึง **0.5 GiB** จึงหยุดเครื่องจำลองและ build daemon ก่อน พื้นที่คืนเป็น 3.2–4.2 GiB แล้วบูต/เปิดเกมสำเร็จ เหลือประมาณ **3.7 GiB** ตัว launcher ยังมี guard หยุด native build เมื่อพื้นที่ต่ำกว่า 5 GiB

- Xcode 26.3 ผ่าน first-launch check และอ่าน target/scheme LittleFarm ได้แล้ว
- iOS Simulator runtime 26.3.1 arm64 ติดตั้งสำเร็จแล้ว การดาวน์โหลดรอบนี้ไม่มี networking error; ใช้ iPhone 17 ที่สร้างไว้ต่อได้ ไม่ต้องดาวน์โหลดซ้ำ
- ยังไม่พบ Android SDK ในตำแหน่งมาตรฐาน
- Android Studio ที่ตรวจพบเป็น 2024.3: ต้องอัปเดต IDE หากจะใช้ AGP 9.1 เช่น Panda 2 (2025.3.2) หรือใหม่กว่า แต่ JBR 21 ที่มากับตัวเดิมยังใช้รัน Gradle CLI ได้

ดูหลักฐานและขอบเขตการตรวจใน [SETUP-STATUS.md](SETUP-STATUS.md)

## เปิดโปรเจกต์

**iOS:** เปิด `iosApp/iosApp.xcodeproj` ใน Xcode เลือก scheme **LittleFarm**

**Kotlin/Android:** เปิดโฟลเดอร์ `kmp` นี้ใน Android Studio รุ่นที่รองรับ และติดตั้ง Kotlin Multiplatform plugin หากต้องการเครื่องมือ KMP ของ IDE

Android SDK ที่ต้องใช้: Android API 36, Build-Tools 36.0.0, Platform-Tools ติดตั้งผ่าน SDK Manager แล้วสร้าง `local.properties` ตามตัวอย่างด้วย SDK path จริง ไม่ commit ไฟล์นี้

ไม่ต้องมี Android SDK เพื่อ build iOS: script ของ Xcode เลือก profile `littlefarm.iosOnly=true` อัตโนมัติ

## คำสั่ง

```sh
cd /Users/ize/Desktop/old_system/after-hours-garden/kmp

# Compile shared UI + ทดสอบกติกาเกม/เซฟ โดยไม่ต้องมี mobile SDK
./scripts/gradle.sh -p verification jvmTest

# iPhone Simulator framework บน Apple Silicon
./scripts/gradle.sh -Plittlefarm.iosOnly=true :composeApp:linkDebugFrameworkIosSimulatorArm64

# iOS app: คำสั่งที่ตรวจผ่านบนเครื่องนี้ แม้ยังไม่มี Simulator runtime
# ใช้ SDK ที่ติดตั้งอยู่; ไม่ต้องเซ็นแอป แต่ยังต้องมีพื้นที่พอสำหรับ build
xcodebuild -project iosApp/iosApp.xcodeproj -target LittleFarm \
  -configuration Debug -sdk iphonesimulator26.2 ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CONFIGURATION_BUILD_DIR="$PWD/.tooling/ios-build" \
  CODE_SIGNING_ALLOWED=NO COMPILER_INDEX_STORE_ENABLE=NO build

# Android หลังติดตั้ง SDK
./scripts/gradle.sh :androidApp:assembleDebug
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

iOS Simulator app: `.tooling/ios-build/LittleFarm.app` — เป็น simulator build ไม่ใช่ไฟล์สำหรับติดตั้งลง iPhone จริง เมื่อมี runtime แล้วเปิด Xcode project เลือก scheme `LittleFarm` และ iPhone Simulator จากนั้นกด Run

`scripts/gradle.sh` ใช้ Java 21 ของ Android Studio พร้อม cache เฉพาะโปรเจกต์ใน `.tooling/` ไม่แก้ shell profile หรือ Java global ถ้า IDE อยู่ที่อื่นกำหนด `LITTLEFARM_JAVA_HOME` ไป JDK 21 ของคุณ ใช้ `./gradlew` โดยตรงได้เมื่อกำหนด Gradle JDK ใน IDE แล้ว

Script จะหยุดก่อนดาวน์โหลด native toolchain หากพื้นที่ต่ำกว่า 5 GiB เพื่อไม่ให้ดิสก์เต็มซ้ำ ค่านี้เป็น safety guard ของโปรเจกต์ ไม่ใช่ขั้นต่ำของ Kotlin; `LITTLEFARM_ALLOW_LOW_DISK=1` ข้าม guard ได้เฉพาะเมื่อคุณทราบว่าพื้นที่พอสำหรับงานนั้นแล้ว

## โครงสร้าง

```text
composeApp/src/commonMain/   UI, game engine, save codec, storage interface
composeApp/src/androidMain/  SharedPreferences + clock
composeApp/src/iosMain/      UserDefaults + clock + ComposeUIViewController
composeApp/src/commonTest/  GameEngineTest + SaveCodecTest
composeApp/src/commonMain/composeResources/ ภาพฉาก ฟอนต์ และใบอนุญาต
androidApp/                 Android entry point
iosApp/                     SwiftUI host + Xcode project
verification/               tests, screenshot renderer และ desktop QA harness ใช้ source เดียวกัน
preview/                    แกลเลอรีภาพจาก Compose ไม่ใช่เกมในเบราว์เซอร์
backend/                    Firestore rules + emulator tests ที่เตรียมไว้ ยังไม่ deploy
gradle/libs.versions.toml    เวอร์ชัน dependencies
```

## Versions / signing

Kotlin และ Compose compiler **2.4.10**, Compose Multiplatform **1.11.1**, AGP **9.1.0**, Gradle **9.3.1**, Java **21**; Android minSdk **24**, compileSdk/targetSdk **36**; iOS deployment **15.0**

อ้างอิง: [KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [Android-KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin), [IDE/AGP compatibility](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility)

Kotlin 2.4.10 อ้างอิง Xcode 26.4 ใน upstream ส่วนเครื่องนี้เป็น 26.3: ตรวจ simulator framework และ app build ผ่านแล้ว แต่ยังไม่ใช่การรับรองทุก target หรือ runtime เพิ่ม `CADisableMinimumFrameDurationOnPhone=true` ตามข้อกำหนดของ Compose iOS แล้ว

Linker มี warning ว่า object `libicu.icudtl_dat.o` ใน dependency ถูก build สำหรับ iOS Simulator 18.5 ขณะที่แอปตั้ง deployment target 15.0 จึงยังไม่อ้างว่าทำงานบน iOS รุ่นเก่าได้จนกว่าจะตรวจ runtime จริง ไม่ได้ซ่อน warning หรือเปลี่ยน minimum OS เพื่อให้ดูเหมือนผ่าน

Bundle ID `com.littlefarm.game` เป็นค่าเริ่มต้น ให้เปลี่ยนเป็น ID ของคุณและเลือก Apple Team ใน Signing & Capabilities ก่อนลง iPhone จริง ไม่มี Team ID, certificate หรือคีย์ส่วนตัวใน source ยังไม่ได้ตั้งระบบเผยแพร่ผ่าน Store/TestFlight

แบบภาพ: [Google Stitch](https://stitch.withgoogle.com/projects/11043770835758050762) · [PRD](../PRD.md)
