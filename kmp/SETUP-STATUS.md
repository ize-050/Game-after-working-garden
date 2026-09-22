# Setup verification — 22 September 2026

## ลองเปิด iOS Simulator — หลังผู้ใช้คืนพื้นที่

- เริ่มรอบนี้ว่างประมาณ 18 GiB; ติดตั้ง iOS 26.3.1 arm64 (23D8133) ด้วย `xcodebuild -downloadPlatform iOS -architectureVariant arm64` สำเร็จ ดาวน์โหลด 8.39 GB จาก Apple
- `:composeApp:linkDebugFrameworkIosSimulatorArm64 --offline` ผ่าน 32s; full Xcode Simulator app build รอบแรกพบ `KotlinUnit` ไม่อยู่ใน exported header แก้ Swift `NativeAccountPlatform.request` callback เป็น `(String) -> Void` ให้ตรงกับ generated `Shared.h`
- Full Xcode target build รอบแก้ **exit 0**; `.tooling/ios-build/LittleFarm.app` เป็นรุ่นใหม่แล้ว (22 กันยายน 19:57 เวลาไทย) ประมาณ 54 MiB รวมภาพและฟอนต์ครบ source; ไม่ต้องเพิ่ม resource-copy phase
- บูต **iPhone 17 / iOS 26.3.1** สำเร็จ (`bootstatus` Finished 1m53s), UDID `709990DB-4E13-4A47-B968-956759A28912`; เปิดหน้าต่าง Simulator ได้จริง
- หลัง first boot พื้นที่ลดเหลือประมาณ 140–428 MiB จึง shutdown เฉพาะ iPhone จำลองนี้และ stop Gradle daemon ของโปรเจกต์เพื่อหยุดการใช้ทรัพยากร ไม่ erase/reset/delete ข้อมูลใด จากนั้นพื้นที่คืนเป็น 3.2–4.2 GiB
- ตรวจขนาดพบ installed runtime asset 7.8 GiB, CoreSimulator dyld cache 3.8 GiB, device data รวมประมาณ 1.5 GiB; ไม่มี installer สำรองขนาดใหญ่ใน Xcode download cache ที่ลบได้อย่างปลอดภัย ไม่ลบ installed runtime หรือไฟล์ส่วนตัวเพื่อฝืนรันต่อ
- ยังมี linker warning ของ ICU minimum iOS Simulator 18.5 เทียบ deployment 15.0 ตามเดิม; runtime ใหม่เป็น 26.3.1 แต่ยังไม่ได้รับรองอุปกรณ์/OS เก่า, Firebase จริง หรือเสียงจริง
- บูตรอบที่สองสำเร็จ 11s โดยไม่ดาวน์โหลด/build เพิ่ม; `simctl install` และ `simctl launch ... com.littlefarm.game` **สำเร็จ exit 0**, process PID 56296
- **ยืนยันเกมรันจริงบน iPhone 17 Simulator แล้ว**: ภาพจาก `simctl io screenshot` แสดงหน้าสวน 6 แปลง, 120 เหรียญ, timer ที่เดินอยู่, toolbar และเมนู native; ภาพหลักฐาน `verification/build/screenshots/ios-simulator-farm.png` (1206×2622) ไม่ใช่ JVM render
- เปิด Simulator และเกมค้างไว้ให้ผู้ใช้เล่นต่อ พื้นที่หลัง launch ประมาณ **3.7 GiB** แนะนำคืนพื้นที่เพิ่มก่อน build/ติดตั้ง component รอบต่อไป ไม่มีการทดสอบกติกาครบทุกเส้นทางหรือฟังเสียงบน Simulator ในรอบนี้

## เก็บดีไซน์ตาม Stitch — รอบล่าสุด 22 กันยายน

- ปรับหน้าเริ่ม/สวน/ร้านเมล็ด/ตลาด/งาน/อัปเกรด/หมู่บ้าน/กระเป๋า/ตั้งค่าและ overlay; ใช้ภาพต้นฉบับ Stitch ในส่วนที่ดึงได้ รักษา engine และ save schema เดิม รายละเอียดและส่วนที่ยังต่าง: [DESIGN-PARITY.md](DESIGN-PARITY.md)
- คำสั่ง `jvmTest smokeUi renderScreens --offline` รอบสุดท้าย **BUILD SUCCESSFUL 2m04s**; **75 unit tests**, failures/errors/skips 0; XML เวลา `2026-09-22T12:48:21Z`
- UI smoke ผ่าน flow เกมเดิม, account fixture, commerce receipt และ design-parity fixture ใหม่ รวม toolbar ทั้ง 4, first-eligible plot, รดน้ำหนึ่ง/ทุกแปลง, disabled, 6 แปลง+เครื่องมืออยู่ในจอ 320dp, reset cancel/confirm, เงินขาดและอัปเกรดครั้งเดียว, map hotspots ทั้ง 4 กับ back navigation
- สร้าง **36 PNGs**: 13 หน้า/สถานะ × 2 ขนาด และภาพหลังเลื่อนไปท้ายหน้า shop/market/orders/upgrades/settings อีก 10 ภาพ ตรวจภาพที่ปรับและแก้ข้อความร้านซ้อนภาพกับปุ่ม growth ถูกตัดแล้ว
- `:composeApp:compileKotlinIosSimulatorArm64 --offline`: **BUILD SUCCESSFUL 25s** รวมภาพใหม่/หน้าจอใหม่ ไม่ใช้ low-disk override และไม่ดาวน์โหลด runtime; **ยังไม่ link/rebuild `.app` หรือทดสอบบน iPhone/Simulator**
- ตรวจ `xcrun simctl list runtimes --json` อีกครั้งยังเป็น `runtimes: []`; พื้นที่ที่ตรวจรอบนี้ประมาณ **19 GiB**
- การเปิด HTTP server จากทั้ง project root ถูก auto-review ปฏิเสธเพราะอาจเปิด source/config ด้วย จึงไม่ได้เปิด server; ใช้ภาพ PNG ใน Codex โดยตรง ไม่ได้พยายามข้ามข้อจำกัด
- ไม่สร้าง Firebase project, ไม่ deploy, ไม่เปิด real login/billing; ทุก smoke ใช้เซฟและ account/audio fixture แยก ไม่แตะเซฟผู้เล่นจริง ไม่อ้าง pixel parity หรือ QA มือถือจาก screenshot/JVM tests

## เติม MVP — รอบก่อน

- เพิ่ม quantity commerce/ราคารวม/เงินขาด, จำแปลงต้นทาง, รดน้ำตรงจากฟาร์ม, วัน/เลเวล/XP, persistent device preferences, original synthesized BGM/SFX และ reduced-motion-aware animations
- JVM verification + Compose semantics smoke + render: **BUILD SUCCESSFUL 1m54s**, unit tests **75** ผ่าน (failures/errors/skips 0); XML เวลา `2026-09-21T16:44:59Z`
- ตรวจรอบแก้สุดท้าย `jvmTest smokeUi --offline`: **BUILD SUCCESSFUL 44s** รวม regression กดทดลอง +5 นาทีและ manual save ภายใน frame เดียวกัน โดยเซฟไม่ย้อนกลับเป็นข้อมูลเก่า
- UI fixture เพิ่ม 320dp buy/sell/return-to-plot/direct-water/preferences ตรวจผลเกมและ JSON save จริงใน harness; audio และ account boundary เป็น fixture ไม่ใช่ real mobile playback/OAuth
- Render 26 ภาพและตรวจหน้าที่เปลี่ยน รวม seed picker/harvest ที่ 320×640; gallery เป็นภาพ ไม่ใช่เว็บเกม
- Kotlin iOS simulator compile แบบ offline รอบสุดท้าย: **BUILD SUCCESSFUL 13s** รวม audio/preferences ใหม่ และไม่มี warning ของ audio interop แล้ว ยังไม่ link/rebuild app และยังไม่รันบน Simulator/iPhone
- เพิ่ม `verification:playDesktop` แบบ standalone verification build เพื่อทดลอง UI/กติกาเดียวกันบน Mac; เซฟแยก `.tooling/desktop-preview` และไม่เปิด cloud/auth จริง
- สั่ง `playDesktop` แล้ว process เริ่มและรายงาน isolated save path; ตรวจ log ไม่พบ error แต่ native UI tool แจ้ง **Mac locked** จึงยังไม่ได้ตรวจการกดจริง/การฟังเสียงหรือ reopen persistence บนหน้าต่าง Mac
- ตรวจ `xcrun simctl list runtimes --json` แบบนอก sandbox: `runtimes: []`; พื้นที่ที่ตรวจหลัง JVM verification ประมาณ 1.7 GiB ไม่มีการดาวน์โหลด runtime/SDK เพิ่ม
- ขอบเขตและรายละเอียด: [MVP-UPDATE.md](MVP-UPDATE.md)

## เพิ่มบัญชีและ Cloud Save — รอบก่อน

- ผู้ใช้เลือก **เตรียมโค้ดก่อน ยังไม่มี Firebase project**: ไม่ได้สร้างโปรเจกต์ เปิด billing ผูก provider หรือ deploy rules จริง
- เพิ่ม `GardenSession`, local cache แยก UID/Guest, revision conflict + explicit choice, account switch, pending-deletion guard และ Account UI ภาษาไทย
- เตรียม native Firebase Google/Apple + Firestore adapters: iOS ใช้ conditional imports/SDK activation ภายหลัง; Android ใช้ optional Firebase source set/flag; default Guest ไม่มี mocked login/cloud success
- `./scripts/gradle.sh -p verification jvmTest smokeUi renderScreens --offline --no-daemon --console=plain '-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8'`: **BUILD SUCCESSFUL 57s**
- **49 unit tests ผ่าน** (GameEngine 13 + SaveCodec 12 + GardenSession 24), failures/errors/skips 0; XML เวลา `2026-09-21T16:02:34Z`
- UI semantics smoke ผ่านเกมเดิม, Guest/unconfigured routes และ account fixture สำหรับ conflict consent, logout isolation, delete confirmation; fixture ใช้ fake native boundary ไม่ใช่ real OAuth/network
- Render 26 ภาพ: 13 สถานะ × 2 ขนาด; ตรวจ Account/Welcome/Settings และ narrow Farm แล้ว ไม่มี layout regression ที่ตรวจพบ
- `:composeApp:compileKotlinIosSimulatorArm64 --offline` พร้อม Gradle heap 1 GiB และ low-disk override เฉพาะครั้ง: **BUILD SUCCESSFUL 15s** รวม common account/UI และ iOS Kotlin storage/bridge entry point ใหม่ ไม่ได้ link framework/app
- Swift sources ผ่าน `swiftc -frontend -parse`; Info/entitlements/pbxproj ผ่าน `plutil -lint` (parser/lint ไม่ใช่การ type-check กับ Firebase SDK)
- Android Guest `:androidApp:tasks --offline` ผ่าน; เปิด Firebase flag โดยไม่มี `google-services.json` หยุดด้วย setup error ตามที่ตั้งใจ **ยังไม่ได้ compile Android source/APK**
- Firestore rules + 9 emulator test cases เตรียมแล้ว, Node syntax check ผ่าน; **ยังไม่ได้ติดตั้ง npm dependencies หรือรัน Firestore Emulator**
- พื้นที่หลัง JVM verification ประมาณ 911 MiB จึงไม่ดาวน์โหลด Firebase/Google SDK, Android SDK หรือ Simulator runtime เพิ่ม
- `.tooling/ios-build/LittleFarm.app` ยังเป็นแอปก่อนปรับภาพและก่อนเพิ่มบัญชี ต้อง full rebuild เมื่อมีพื้นที่และ SDK/config พร้อม ยังไม่เคยเปิดเกมบน iPhone/Simulator
- ขั้นตั้งค่าและขอบเขตที่เหลือ: [FIREBASE-SETUP.md](FIREBASE-SETUP.md), [backend/README.md](backend/README.md), คู่มือใน `iosApp/` และ `androidApp/`

## ผลล่าสุดหลังปรับภาพตาม Stitch

- UI ทั้ง 9 หน้า + overlay เลือกเมล็ด/เติบโต/เก็บเกี่ยว/รีเซ็ต ปรับสไตล์แล้ว โดยไม่เปลี่ยนกติกาเกมหรือ save schema
- `./scripts/gradle.sh -p verification smokeUi renderScreens jvmTest --no-daemon --console=plain`: exit 0
- Unit tests 25 ผ่าน (13 engine + 12 codec); XML ล่าสุดเวลา `2026-09-21T15:34:09Z`
- Semantics-level UI smoke ผ่านการนำทาง พรวนดิน ปลูก รดน้ำ เร่งเวลาเดโม เก็บ ขาย ซื้อ และส่งงาน ตรวจเหรียญ/ของ/เซฟหลังทำจริงใน shared Compose
- สร้างและตรวจภาพ 24 รูป: 12 หน้า/สถานะบน 393×852 และ 320×640; แก้ contrast และปุ่มเก็บเกี่ยวที่เคยถูกตัดแล้ว
- `:composeApp:compileKotlinIosSimulatorArm64`: UI และ resource accessors ใหม่ compile ผ่าน (24 วินาที)
- Native compile รอบนี้ใช้ toolchain เดิม จำกัด Gradle heap 1 GiB และ low-disk override เฉพาะครั้ง ไม่ link framework/app เต็ม และไม่ติดตั้ง runtime
- พื้นที่ล่าสุดประมาณ 1.0 GiB; **`.tooling/ios-build/LittleFarm.app` ยังเป็นผล build ก่อนปรับภาพ ต้อง rebuild เมื่อมีพื้นที่**
- Screenshot/semantics smoke บน JVM ไม่ใช่การยืนยัน touch, safe-area, font rendering หรือ persistence บน iPhone จริง

## หลักฐาน setup และ app build ก่อนปรับภาพ

- ตรวจ Gradle 9.3.1 ด้วย official SHA-256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`
- สร้าง standard Gradle Wrapper พร้อม distribution checksum
- Shared Compose UI, game engine และ storage interface คอมไพล์ผ่านบน JVM
- `./scripts/gradle.sh -p verification jvmTest`: 25 tests, 0 failures, 0 errors
  - GameEngineTest: 13 tests
  - SaveCodecTest: 12 tests
- Xcode project/Info.plist syntax และ shared scheme XML ตรวจผ่าน
- `xcodebuild -list`: พบ LittleFarm target และ shared scheme
- `xcodebuild -checkFirstLaunchStatus`: exit 0
- `:composeApp:linkDebugFrameworkIosSimulatorArm64`: compile/link ผ่านด้วย Kotlin 2.4.10, Compose 1.11.1 และ Xcode 26.3
- `xcodebuild -target LittleFarm -sdk iphonesimulator26.2 ARCHS=arm64 ... build`: `BUILD SUCCEEDED`, exit 0 รวม SwiftUI host และ Kotlin framework
- ตรวจ artifact `.tooling/ios-build/LittleFarm.app/LittleFarm`: Mach-O arm64 executable; bundled Info.plist ระบุ `iPhoneSimulator`, SDK 26.2 และชื่อแอป “สวนหลังเลิกงาน”
- เพิ่ม `CADisableMinimumFrameDurationOnPhone=true` ใน source Info.plist และตรวจว่าค่านี้อยู่ใน app bundle แล้ว ตามข้อกำหนด [Compose iOS](https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html#disabling-minimum-frame-duration-on-ios-is-mandatory)

## คำสั่ง native ที่ตรวจผ่าน

```sh
./scripts/gradle.sh -Plittlefarm.iosOnly=true :composeApp:linkDebugFrameworkIosSimulatorArm64 --no-daemon --console=plain

env LITTLEFARM_ALLOW_LOW_DISK=1 xcodebuild \
  -project iosApp/iosApp.xcodeproj -target LittleFarm \
  -configuration Debug -sdk iphonesimulator26.2 \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CONFIGURATION_BUILD_DIR="$PWD/.tooling/ios-build" \
  CODE_SIGNING_ALLOWED=NO COMPILER_INDEX_STORE_ENABLE=NO build
```

ใช้ low-disk override เฉพาะคำสั่งประกอบแอปครั้งนี้ หลัง framework/toolchain พร้อมแล้ว ไม่ได้ปิด guard ถาวรหรือดาวน์โหลด Simulator เพิ่ม ไม่ควรใช้ override กับ cold build บนดิสก์ใกล้เต็ม

## ยังไม่ผ่าน / ข้อจำกัดจริง

- ไม่มี iOS Simulator runtime (`xcrun simctl list runtimes` ว่าง) จึงยังไม่ได้เปิดแอปหรือทดสอบ interactive UI/native storage จริง
- หน้าดาวน์โหลด Xcode แสดง iOS 26.3.1 Simulator ล้มเหลวด้วย “Download failed due to a general networking error.” ไม่ได้เริ่มดาวน์โหลดซ้ำ
- Scheme build ด้วย `-destination 'generic/platform=iOS Simulator'` ไม่พบ eligible destination; target + installed SDK build ผ่าน จึงไม่ใช่ source compilation failure
- หลังตรวจ UI ใหม่พื้นที่เหลือประมาณ 1.0 GiB; ควรเพิ่มพื้นที่ว่างเป็นประมาณ 15–20 GB ก่อนติดตั้ง runtime และ build ต่อ (พื้นที่เผื่อ ไม่ใช่ minimum ทางการ)
- Linker warning: `libicu.icudtl_dat.o` built for iOS Simulator 18.5 แต่ app deployment target 15.0; การรองรับ OS รุ่นเก่ายังไม่ผ่าน runtime QA
- ยังไม่พบ Android SDK ใน standard paths; APK/Android storage integration ยังไม่ได้ build หรือทดสอบ
- ไม่ได้ตั้ง Apple signing team, ลง iPhone จริง หรือเผยแพร่แอป

## ประวัติการลองครั้งก่อน

ครั้งแรกหยุดระหว่างแตก Kotlin/Native เพราะดิสก์เหลือ 104 MiB มีการลบเฉพาะ partial compiler และไฟล์ดาวน์โหลด/cache ซ้ำที่สร้างขึ้นใน `.tooling/` ไม่ลบ source หรือข้อมูลผู้ใช้ ครั้งล่าสุดเริ่มด้วยพื้นที่ 8.4 GiB และผ่าน native compilation รวมถึง app build แล้ว ตัว Gradle launcher ยังคงหยุด native task เมื่อพื้นที่ต่ำกว่า 5 GiB เป็นค่าเริ่มต้น

## สิ่งที่ไม่เปลี่ยน

- ไม่แก้ global Java, shell profile, Xcode selection, account, signing หรือ IDE installation
- ใช้ JBR 21 ที่มีอยู่ และ cache/toolchain downloads เฉพาะโปรเจกต์ใน `.tooling/`
- เก็บ HTML prototype, Stitch และไฟล์อื่นใน workspace ไว้

ผลปัจจุบัน: UI ใหม่ผ่าน JVM tests, semantics smoke, screenshot QA และ iOS Kotlin compilation; framework/app build เต็มที่เคยผ่านเป็น UI รุ่นก่อน ยังไม่ใช่ผลการเปิดเล่นหรือ device QA และไม่ใช่ signed build สำหรับ iPhone จริง
