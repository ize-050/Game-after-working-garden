# สวนหลังเลิกงาน: Little Farm

เกมฟาร์มการ์ตูนสำหรับเล่นสั้น ๆ: ไถ → ปลูก → รดน้ำ → รอ → เก็บ → ขายหรือส่งงาน → ขยายฟาร์ม

แอป KMP รุ่น v0.2 เพิ่มเลเวลปลดล็อกพืช 7 ชนิด งานเพื่อนบ้านต่อเนื่อง แต่งสวน สมุดสะสมพืช แมวผูกพัน และแจ้งเตือนรวมแบบเลือกเปิด อ่าน [รายละเอียดระบบใหม่และขอบเขตการตรวจ](kmp/PROGRESSION-UPDATE.md) ระบบเหล่านี้อยู่ในแอป native ไม่ใช่ prototype HTML ด้านล่าง

## ผลงาน

- [Native KMP project](kmp/README.md): Kotlin Multiplatform + Compose สำหรับเริ่มพัฒนา iOS/Android
- [Google Stitch V2](https://stitch.withgoogle.com/projects/11043770835758050762): 12 หน้าสำหรับทิศทางภาพและ navigation prototype
- [ต้นแบบเล่นจริง](playable/index.html): เกม HTML แบบ standalone ใช้ข้อมูลร่วมกันทั้ง 12 หน้า
- [PRD](PRD.md): แนวคิด ขอบเขต กติกา เศรษฐกิจ UX และ roadmap
- [ผลการทดสอบ](PROTOTYPE-QA.md): แยกการตรวจการเชื่อมหน้าออกจากความถูกต้องของระบบเกม
- [รายละเอียด Stitch](STITCH-V2.md): เวอร์ชันภาพและข้อจำกัดของเฟรมที่สร้างโดย AI

## เริ่มรันโปรเจกต์

ต้องมี Git และ **JDK 21** สำหรับแอป KMP ใช้ JDK ที่มากับ Android Studio ในตำแหน่งมาตรฐานบน Mac ได้ ส่วน iOS ต้องมี Xcode และ Simulator runtime เพิ่ม อ่าน [การเตรียมเครื่องและวิธีรันละเอียด](kmp/README.md)

### 1. ดาวน์โหลดโค้ด

```sh
git clone https://github.com/ize-050/Game-after-working-garden.git
cd Game-after-working-garden
```

ถ้ามีโค้ดอยู่แล้ว ไม่ต้อง clone ซ้ำ ให้เปิด Terminal ในโฟลเดอร์เกมที่มีไฟล์ README นี้ คำสั่งด้านล่างรันจากโฟลเดอร์นั้น

### 2. เลือกวิธีเล่น

| วิธี | สิ่งที่ได้ | สิ่งที่ต้องมีเพิ่ม |
| --- | --- | --- |
| หน้าต่างเกมบน Mac | UI และกติกา KMP v0.2 เล่นได้จริง ใช้เซฟทดสอบแยก | JDK 21; ไม่ต้องมี mobile SDK |
| iOS Simulator | แอป iOS native | Mac Apple Silicon, Xcode, JDK 21, Simulator runtime |
| Android | แอป Android native | JDK 21, Android SDK API 36 และ emulator/มือถือ |
| HTML prototype | ต้นแบบเก่า 4 พืช / 1 งาน ไม่ใช่ KMP v0.2 | Python 3 และเว็บเบราว์เซอร์ |

**เปิดหน้าต่างเกมบน Mac ได้เร็วที่สุด:**

```sh
./kmp/scripts/gradle.sh -p verification playDesktop --no-daemon --console=plain
```

ครั้งแรกต้องต่ออินเทอร์เน็ตเพื่อดาวน์โหลด Gradle และ dependencies แล้วหน้าต่างเกมจะเปิดขึ้น ปิดหน้าต่างเพื่อจบการเล่น; เซฟอยู่ใน `kmp/.tooling/desktop-preview/` ไม่ใช่เซฟของ Simulator และไม่มี Google Login หรือแจ้งเตือน OS จริงใน desktop preview

**เปิดใน Xcode เพื่อรัน iOS Simulator:**

```sh
open kmp/iosApp/iosApp.xcodeproj
```

เลือก scheme **LittleFarm** → เลือก iPhone Simulator ที่ติดตั้งไว้ → กด **Run (⌘R)** ไม่ต้องตั้ง Firebase เพื่อเล่น Guest และไม่ต้องติดตั้ง Android SDK สำหรับ iOS ดู [ขั้นตอน Xcode และคำสั่ง build/install](kmp/README.md#ios-simulator)

**Android:** เปิดโฟลเดอร์ `kmp` ใน Android Studio ตั้ง SDK path แล้วเลือก `androidApp` และ emulator/มือถือ กด Run หรือทำตาม [คำสั่ง Android](kmp/README.md#android)

ควรมีพื้นที่สำหรับ SDK/runtime/build ประมาณ **10–20 GB หรือมากกว่านั้น** ตัว launcher หยุด iOS build เมื่อพื้นที่ต่ำกว่า 5 GiB อย่าฝืนปิด guard เพียงเพื่อให้คำสั่งเดินต่อ

### 3. ตรวจเกมและสร้างภาพ preview

```sh
./kmp/scripts/gradle.sh -p verification jvmTest smokeUi renderScreens --no-daemon --console=plain
open kmp/preview/index.html
```

แกลเลอรีเป็น **ภาพนิ่ง** จาก Compose ไม่ใช่เกมที่กดเล่นได้ ถ้าต้องการเล่นให้ใช้ `playDesktop` หรือแอปมือถือ ไม่ใส่ `--offline` ในการรันครั้งแรก; ใช้ได้เมื่อดาวน์โหลด dependencies ครบแล้วเท่านั้น

## เปิด HTML prototype รุ่นแรก

```sh
python3 -m http.server 4173 --bind 127.0.0.1 --directory playable
```

รันจากโฟลเดอร์ repository แล้วเปิด [HTML prototype ในเครื่อง](http://127.0.0.1:4173/) ในเบราว์เซอร์เดียวกันเพื่อใช้เซฟเดิม ไม่มี package ที่ต้องติดตั้ง เซิร์ฟเวอร์นี้รับการเชื่อมต่อเฉพาะเครื่องนี้ ไม่ใช่การเผยแพร่เว็บสู่สาธารณะ กด Ctrl+C ใน Terminal เพื่อหยุด

สำหรับแอป iOS/Android ให้เริ่มที่ [คู่มือ KMP](kmp/README.md) ไฟล์ build, Simulator app, cache และ Firebase config จริงไม่รวมใน repository แกลเลอรี `kmp/preview/index.html` ต้องสร้างภาพด้วยคำสั่ง `renderScreens` ในคู่มือก่อนจึงจะแสดงผลได้

## สถานะที่ตรวจแล้ว

รอบ v0.2: shared code ผ่าน 104 unit tests และการทดสอบกด UI พร้อมภาพ 50 ภาพ ส่วน native binary รุ่น v0.2 ยังไม่ได้ rebuild เพราะพื้นที่เครื่องไม่พอ; แอปเดิมใน Simulator ยังไม่มีฟีเจอร์ใหม่นี้ Android SDK ยังไม่พร้อม และแจ้งเตือนบนมือถือจริงยังต้องทดสอบ อ่าน [ผลตรวจและข้อจำกัด](kmp/PROGRESSION-UPDATE.md)

## ลองรอบแรก

1. เก็บผักกาดที่พร้อมเก็บในแปลง 6 แล้วดูว่าผักเพิ่มในกระเป๋า แต่เงินยังไม่เพิ่ม
2. รดน้ำผักกาดแปลง 4 และปลูกเมล็ดในแปลง 3
3. ใช้ “ทดลองเวลา +5 นาที” เพื่อเห็นผักโตโดยไม่ต้องรอจริง
4. เลือกขายผักหรือเก็บผักกาด 2 หัวไปส่งงานหมู่บ้าน
5. ซื้อเมล็ดเพิ่มและค่อย ๆ เก็บเงินขยาย 6 → 9 แปลง

ปุ่มข้ามเวลาเป็นเครื่องมือทดสอบ ไม่ใช่ระบบเร่งเวลาที่ต้องซื้อ ผักไม่ตาย ไม่มี energy และไม่มีการชำระเงินจริง

## ขอบเขต

prototype HTML เป็น vertical slice รุ่นแรก มีผัก 4 ชนิด งานตัวอย่าง 1 งาน และอัปเกรด 2 แบบ ส่วนแอป KMP มีระบบเพิ่มเติมตาม v0.2 ข้างต้น ทั้งสองยังไม่ใช่เกมพร้อมขาย ฤดูกาล NPC เชิงลึก คราฟต์ ตกปลา และบริการ cloud จริงยังไม่อยู่ในรอบนี้

งานภาพใน Stitch และภาพ SVG ในต้นแบบเล่นจริงเป็นคนละชุด: Stitch ใช้เป็นภาพเป้าหมาย ส่วน SVG ทำให้ต้นแบบเล่นออฟไลน์ได้โดยไม่พึ่งภาพหรือบริการภายนอก
