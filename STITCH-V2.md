# สวนหลังเลิกงาน: Little Farm — V2

Google Stitch: https://stitch.withgoogle.com/projects/11043770835758050762

สถานะ: สร้างภาพครบ 12 หน้าและเพิ่มเข้าโหมด Prototype ใน Stitch แล้ว; มีต้นแบบเล่นจริงแยกใน `playable/` สำหรับทดสอบข้อมูลร่วมกันทั้งเกม

## Design decision

ปรับจากสวนดาดฟ้าเน้นของตกแต่งเป็นฟาร์มผักจริงในโลกการ์ตูน บรรยากาศชีวิตฟาร์มแบบเรียบง่าย มีบ้านหลังเล็ก แปลงดิน รั้วไม้ สระน้ำ ร้านค้า และแมวส้ม

แกนเล่น: ไถ → ปลูก → รดน้ำ → รอเจริญเติบโต → เก็บ → ขาย/ส่งงาน → ซื้อเมล็ดและขยายฟาร์ม

ภาพต้นฉบับและตัวละครใหม่ทั้งหมด ใช้ความอบอุ่นของเกมชีวิตฟาร์มเป็นทิศทาง ไม่ใช้ตัวละคร แผนที่ หรือทรัพยากรภาพจากเกมอื่น

## Main generation brief

- Twelve coherent 393 × 852 portrait mobile screens, Thai text.
- Original 2.5D cartoon farm diorama; toy-like proportions and tactile cream/wood controls.
- Leaf green `#3D6B45`, cream `#FFF4D8`, butter `#F4C867`, coral `#DC8A62`, sky `#A8D7DF`.
- Six visibly tappable plots in a 3 × 2 grid; distinguish untilled soil, tilled soil, dry planted seed, wet sprout, growing crop and ripe crop.
- The farm scene occupies approximately 65–75% of the gameplay screen.
- Consistent farmer with straw hat/denim overalls and orange cat with sage neckerchief.
- Start120 coins, six plots and seed bag as specified in PROTOTYPE-QA.md.
- One crop per seed; start growth after first watering; save readyAt timestamps; no crop death or energy bar.
- Prototype-only +5-minute virtual clock advances all planted, watered crops without granting coins.
- All 12 screens and primary/secondary/back/close routes must be connected.
- Shared saved state where supported; prevent negative inventory, repeated harvest rewards and repeated order rewards.

## Screens

01 Title/Resume; 02 Farm; 03 Seed Picker; 04 Crop Growth; 05 Harvest Result; 06 Bag; 07 Seed Shop; 08 Produce Market; 09 Orders; 10 Upgrades; 11 Village Map; 12 Settings/Save.

Detailed requirements and economy: PRD.md. Route and functional acceptance matrix: PROTOTYPE-QA.md.

## Scope note

งานนี้คือ game design และ prototype สำหรับทดลองการใช้งาน การเปลี่ยนหน้าได้ไม่เท่ากับระบบเกม Production; รายงานผลต้องระบุสิ่งที่ทดสอบสำเร็จจริงและข้อจำกัดที่พบ

## Stitch verification — 20 September 2026

- เพิ่มหน้าจอทั้ง 12 เข้าโหมด Prototype โดยเลือก Farm และ Crop Detail เวอร์ชันปรับปรุงล่าสุด; เก็บ variants เดิมใน canvas ไม่ลบทิ้ง
- ทดสอบผ่าน UI: Welcome → Farm → Seed Picker → Farm; Farm → Bag → Village → Seed Shop → Farm; Farm → Harvest Result → Market → Orders → Farm; Farm → Upgrades → Farm → Settings → Welcome
- แตะแปลงหัวไชเท้าเปิด Crop Detail ได้ และปุ่มทดลองเวลา +5 นาทีเปลี่ยนภาพ/ข้อความเป็นพร้อมเก็บเกี่ยว
- ข้อจำกัดที่พบจริง: state ของหลายหน้าสร้างแยกกัน ตัวเลขเลเวล/ผลผลิตบางจุดยังเป็นข้อมูลตัวอย่าง; Crop Detail ใช้ปุ่มเดียวสลับระหว่างกลับฟาร์มและเก็บเกี่ยว ทำให้ hotspot เดิมยังกลับฟาร์มแม้ข้อความเปลี่ยน
- ดังนั้น Stitch ใช้ตรวจ art direction และ screen flow; `playable/index.html` เป็นแหล่งอ้างอิงพฤติกรรมเกมและเศรษฐกิจ ไม่ควรนำ JavaScript จากแต่ละเฟรม Stitch มาต่อกันโดยไม่เขียน state กลางใหม่
