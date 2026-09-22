# บัญชีผู้เล่นและ Cloud Save — เริ่มตั้งค่าตรงนี้

## สถานะที่ทำไว้

เตรียมโค้ดแล้ว แต่ **ยังไม่มี Firebase project / provider configuration และยังล็อกอิน Google/Apple จริงไม่ได้** ตามที่ตกลงให้เตรียมโค้ดก่อน ไม่ได้สร้างบริการภายนอก เปิด billing หรือ deploy rules ใด ๆ

- เล่น Guest ต่อได้ เซฟเดิม `little_farm_v1` ไม่ถูกย้ายหรือลบเพียงเพราะเปิดหน้าบัญชี
- หน้า “บัญชีและสวนของเรา” เข้าได้จากหน้าเริ่มเกม ตั้งค่า และแถบสถานะเซฟ
- `GardenSession` เก็บเซฟแยก UID, เก็บ Guest แยกต่างหาก, คงเซฟบัญชีที่ยังไม่ซิงก์ไว้หลัง logout, ตรวจ revision และให้ยืนยันก่อนเลือกสวนเมื่อเกิด conflict
- ซิงก์หลังการบันทึกในเครื่องแบบหน่วง 2 วินาที และมีปุ่มซิงก์เอง ไม่มีวงจร retry ไม่สิ้นสุด เมื่อเน็ตหลุดยังเล่นและเซฟในเครื่องได้ แต่การซิงก์/ล็อกอินต้องออนไลน์
- Google/Apple ใช้ native SDK ของแต่ละระบบ ผ่าน interface กลาง ไม่เก็บ token หรือ private key ในเซฟเกม
- มี flow ยืนยันลบบัญชีและกลับสู่ Guest, fresh reauthentication, และ tombstone ป้องกันอุปกรณ์เก่าซิงก์สวนที่ลบกลับมา

นี่คือ **personal cloud backup สำหรับเกมเล่นคนเดียว** ไม่ใช่ระบบป้องกันโกงหรือกระเป๋าเงินซื้อจริง ปุ่มทดลองเวลา +5 นาทียังอยู่ ห้ามใช้ยอดเหรียญใน JSON เป็นหลักฐานสำหรับ IAP/การแลกของ

## ขั้นแรกที่ทำด้วยกันได้

1. เปิด [Firebase Console](https://console.firebase.google.com/) ด้วยบัญชี Google ของเจ้าของเกม แล้วเลือกสร้างโปรเจกต์ใหม่ ตั้งชื่อที่ต้องการ เช่น `Little Farm` — ชื่อนี้เป็นเพียงตัวอย่าง ยังไม่ได้สร้างให้
2. Google Analytics ไม่จำเป็นสำหรับฟีเจอร์บัญชีชุดนี้ จะไม่เปิดก่อนก็ได้ **ยังไม่ต้องเปิด billing หรือเพิ่มบริการอื่น** หาก console เสนอแผนค่าใช้จ่าย ให้ตรวจเงื่อนไขก่อนยอมรับ
3. หลังสร้างเสร็จ ส่งเฉพาะ **Project ID** มาเพื่อเชื่อมขั้นต่อไป ไม่ต้องส่งรหัสผ่าน, OAuth client secret, service-account JSON หรือ Apple `.p8` private key
4. ตัดสินใจ Bundle ID ถาวรก่อนลงทะเบียน iOS app ค่าในโค้ด `com.littlefarm.game` ยังเป็น placeholder ใช้ Project เดียวกันลงทะเบียน iOS และ Android เพื่อให้บัญชีเดียวกันเข้าถึงเซฟเดียวกัน
5. สร้าง Cloud Firestore **default database** ในโหมด production/locked เลือก region ให้เหมาะก่อนยืนยัน และเปิด Authentication → Google ด้วย support email จริง
6. ทดสอบและ deploy [backend/firestore.rules](backend/firestore.rules) ไปยัง Project ID ที่ตรวจแล้ว **ก่อนเปิดบัญชีจริง** ห้ามใช้ allow read/write ทั้งฐานข้อมูลหรือ test-mode rules
7. ทำตามคู่มือแพลตฟอร์ม:
   - [iOS / Xcode](iosApp/FIREBASE-SETUP.md): Firebase/Google SPM dependencies, `GoogleService-Info.plist`, URL scheme, Signing Team และ Apple capability
   - [Android](androidApp/FIREBASE-SETUP.md): `google-services.json`, signing fingerprints, Web client ID และ Firebase build flag

ไฟล์ mobile Firebase configuration ไม่ใช่ private key แต่แยกเป็นไฟล์ local ที่ Git ignore ไว้เพื่อป้องกันการใช้ผิดโปรเจกต์ ความปลอดภัยจริงยังต้องอาศัย Authentication และ Security Rules

## สวนของใครอยู่ที่ไหน

| ข้อมูล | ที่เก็บ / กติกา |
|---|---|
| สวน Guest เดิม | local SaveStore เดิม ไม่อัปโหลดก่อนล็อกอิน |
| สำเนาเซฟแต่ละบัญชี | local `account_save_v1_<uid>` ภายใต้ namespace ของแพลตฟอร์ม |
| เซฟคลาวด์ | Firestore `players/{uid}`: `payload`, `revision`, `updatedAt` |
| ตัวตน / token | Firebase Auth และ provider SDK; shared Kotlin เห็นเฉพาะ UID, ชื่อแสดงผล และ provider |
| ลบบัญชีที่ยังไม่จบ | local guard + `accountDeletions/{uid}` ที่ไม่มีข้อมูลสวน |

Google กับ Apple **ไม่ได้เชื่อมเป็นบัญชีเดียวกันอัตโนมัติ** และรอบนี้ยังไม่มีปุ่มผูก provider เพิ่มเข้าบัญชีเดิม ต้องใช้วิธีล็อกอินเดิมบนอีกเครื่อง การเล่น Guest แล้วนำสวนไปเข้าบัญชีไม่ใช่การเชื่อม Google/Apple เข้าหากัน

เมื่อพบสองสวน ผู้เล่นเลือก “สวนในเครื่อง” หรือ “สวนบนคลาวด์” พร้อมยืนยันอีกครั้ง ไม่รวมเหรียญ/ของ การเลือกสวนในเครื่องใช้ transaction ตรวจ revision ปัจจุบัน; ถ้ามีอีกเครื่องแก้ระหว่างนั้น ต้องเลือกใหม่ ก่อนเลือกสวนคลาวด์ก็ตรวจว่าเป็นเวอร์ชันที่แสดงจริง

## ผลตรวจและข้อจำกัด

- JVM tests ผ่าน **49 tests**: game/codec เดิม 25 + session/ownership/conflict/failure 24
- Compose semantics smoke ผ่านเส้นทาง Guest/unconfigured และเกมเดิม; fixture เพิ่มทดสอบ conflict consent, logout isolation และ delete confirmation โดยใช้ native boundary จำลองเฉพาะชุดทดสอบ
- ภาพจาก Compose จริง 13 สถานะ × 2 ขนาด ตรวจหน้าบัญชีและหน้าที่ได้รับผลจากแถบเซฟแล้ว
- JavaScript rules tests ผ่าน syntax check แต่ **ยังไม่ได้รัน Firestore Emulator** เพราะไม่มี dependencies/emulator ในเครื่องและพื้นที่เหลือน้อยกว่า 1 GiB ดู [backend/README.md](backend/README.md)
- Android Guest Gradle configuration ผ่าน และ Firebase flag ที่ไม่มี config หยุดด้วยข้อความที่ตั้งใจไว้; **ยังไม่ได้ compile Android source/APK**
- Swift source ผ่าน parser และ plist/project ผ่าน lint; **Firebase-enabled Swift/Kotlin Android ยังไม่ได้ type-check กับ SDK ที่ดาวน์โหลดจริง**
- ยังไม่ได้ลงชื่อเข้าใช้จริง, อ่าน/เขียนคลาวด์จริง, deploy rules, เปิดแอปบน iPhone/Simulator หรือทดสอบสองเครื่อง ดูผล native ล่าสุดใน [SETUP-STATUS.md](SETUP-STATUS.md)

## ก่อนเผยแพร่จริง

- เพิ่มพื้นที่ดิสก์ก่อนดาวน์โหลด SDK/runtime และ build แอปเต็ม อย่าใช้ low-disk override เพื่อฝืนดาวน์โหลดเพิ่ม
- รัน emulator rules tests และ device tests ครบทั้ง Google/Apple, เปลี่ยนบัญชี, reinstall, ออฟไลน์, สองเครื่องชนกัน และลบบัญชีทุกจุดที่เน็ตขาด
- ตรวจปุ่ม provider ตาม brand guidelines และจัดทำนโยบายความเป็นส่วนตัวที่ตรงกับบริการ/ข้อมูลที่เปิดจริง
- การลบ Firestore, Firebase Auth และการ revoke Apple token ไม่ใช่ transaction เดียวกัน: มีระบบล็อกและ retry แต่กรณี server ลบสำเร็จแล้ว response หายอาจต้องตรวจด้วยผู้ดูแล ห้ามแก้ด้วยการอัปโหลดสวนเก่ากลับ
- Tombstone ที่มี UID/timestamp ถูกเก็บเพื่อกันข้อมูลกลับมา ต้องมีนโยบาย retention และ privileged cleanup/reconciliation ก่อน production รวมถึงจัดการสำเนา local ที่ล้างไม่สำเร็จ ไม่กล่าวว่าไม่มีข้อมูลหลงเหลือทุกระบบ
- ถ้ามี Google Login บน App Store ให้จัดทางเลือกที่เข้าเงื่อนไขข้อ 4.8 (ในแผนนี้คือ Sign in with Apple) และเมื่อให้สร้างบัญชี ต้องมีช่องทางลบบัญชีในแอป ตาม [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)

อ้างอิง: [Firebase Google Login](https://firebase.google.com/docs/auth/ios/google-signin), [Firestore owner rules](https://firebase.google.com/docs/firestore/security/rules-conditions), [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions)
