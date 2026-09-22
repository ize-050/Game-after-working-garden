# สวนหลังเลิกงาน — Google Stitch design brief

สถานะ: กำลังสร้างชุดภาพและหน้าจอเกมใน Google Stitch

โปรเจกต์: https://stitch.withgoogle.com/projects/17431132075743423623

## คอนเซปต์

เกมสวนดาดฟ้าสำหรับช่วงพักหลังเลิกงาน เล่นรอบละ 5–10 นาที ปลูกต้นไม้ รดน้ำ แต่งมุมเล็ก ๆ และเก็บภาพสวนเป็นโปสการ์ด มีแมวส้มผูกผ้าสีเขียวเป็นเพื่อนประจำสวน

## Art direction

Original cozy cartoon 2.5D/isometric rooftop diorama at golden hour. Clay-like rounded shapes, soft hand-painted textures, chunky ceramic pots, plump expressive plants, lush layered leaves, warm string lights, wooden furniture, peach-to-lilac city skyline. A small orange cat with a cream muzzle and green neckerchief recurs consistently across screens.

ภาพสวนต้องเป็นส่วนหลักของหน้า Home อย่างน้อยประมาณ 60% สร้างความรู้สึกอยากแตะ อยากปลูก และอยากสะสม ใช้ภาพประกอบเกมเต็มฉากและภาพสิ่งของที่มีรายละเอียด พร้อมปุ่มทรงนุ่มมีมิติ

| Token | Color |
|---|---|
| Warm cream | `#FFF5DF` |
| Leaf green | `#355640` |
| Mint | `#A9CDA0` |
| Sunny butter | `#F6D77E` |
| Terracotta | `#D88964` |
| Dusk lilac | `#A69AC8` |

## Screens — 393 × 852

### 1. สวนของฉัน

- Full-screen cartoon rooftop garden at sunset: six planting spots, three growing or blooming plants, one inviting empty pot, a bench, the orange cat, string lights and city silhouettes.
- Floating compact HUD: สวนหลังเลิกงาน, leaf balance 240, Settings.
- Greeting: กลับถึงสวนแล้ว
- In-scene bubbles: รดน้ำหน่อย / ปลูกอะไรดี
- Chunky action tray: ปลูก / รดน้ำ / แต่งสวน
- Small line: ต้นไม้รอคุณได้ ไม่ต้องรีบ
- Bottom tabs: สวน / ของแต่ง / บันทึก / ร้าน

### 2. ปลูกอะไรดี

- Same garden behind a rounded cream nursery bottom sheet.
- Large smiling daisy in a terracotta pot, three illustrated seed packets: เดซี่ / โหระพา / ชมจันทร์.
- Daisy selected; title เดซี่กระถางแรก; care text ดูแลอีก 3 ครั้ง แล้วมารอดอกแรกกัน.
- Cost 40 ใบไม้; balance 240; CTA ปลูกเดซี่ · 40 ใบไม้.
- Tiny orange cat peeking over the sheet edge, obvious Close and Back.

### 3. จัดมุมโปรด

- Same garden scene with a translucent café-chair placement preview and subtle tile grid.
- Top actions ย้อนกลับ / จัดเสร็จแล้ว; nearby object actions หมุน / เลิกทำ.
- Inventory tabs กระถาง / ที่นั่ง / แสงไฟ / ของเล็ก ๆ.
- Illustrated coffee-mug planter, folding chair, string lights and พักก่อน sign; selected chair label มีแล้ว.

### 4. เย็นนี้ในสวน

- A collectible paper postcard dominates, showing the same garden at deeper sunset, orange cat and first daisy bloom.
- วันนี้ เดซี่ดอกแรกบานแล้ว; date 20 ก.ย.; pressed-leaf and cat stickers.
- Optional note อยากจดอะไรไว้ไหม; three sticker choices.
- เก็บเย็นนี้ไว้ / แชร์โปสการ์ด; a peek of previous postcards.

### 5. ร้านของแต่ง

- Tiny illustrated garden stall, striped awning and orange-cat shopkeeper.
- Tabs แลกใบไม้ / ชุดตกแต่ง.
- Featured คาเฟ่หลังหกโมง set: coffee-cup planter, round table, string lights, woven mat.
- ได้ครบ 4 ชิ้น, ฿99 — ราคา mockup สำหรับทดลองแนวคิด ไม่ใช่รายการขายจริง.
- ลองวางในสวน / ดูชุดนี้; one supporting item purchased with leaves.

## Game flow and prototype

สวน → ปลูก → เลือกเดซี่ → ปลูก → รดน้ำ → แต่งสวน → เก็บโปสการ์ด

- Starting balance 240 leaves, Daisy costs 40, first watering grants 15 leaves.
- Home Plant → Nursery; Home Decorate / ของแต่ง → Decorate.
- Bottom บันทึก → Postcard; ร้าน → Shop; Shop preview → Decorate.
- Plants do not die while absent; no energy bar, streak, forced ads or countdown pressure.
- Cosmetic purchases provide specified items; no random loot boxes.
- Primary design goal: gorgeous, coherent cartoon game scenes and readable Thai controls.

## Visual QA criteria

- ห้าหน้าดูเป็นเกมเดียวกัน ทั้งฉาก แมว สี และปุ่ม
- ภาพสวนเด่นเต็มจอ มีพื้นที่ให้มองเห็นของที่ปลูกและแต่ง
- ปุ่มหลักแตะง่ายและข้อความไทยไม่ถูกตัด
- ภาพต้นไม้/เฟอร์นิเจอร์โหลดครบและไม่มีภาพ placeholder
- เส้นทางหลักของ prototype เปิดไปยังหน้าถัดไปได้
- การซื้อและการแชร์ในงานออกแบบเป็นตัวอย่างเท่านั้น
