package com.littlefarm.game

/** Fixed garden slots make decoration immediate without a difficult placement editor. */
enum class DecorationSlot(val thaiName: String) {
    FENCE("รั้ว"), PATH("ทางเดิน"), LAMP("โคมไฟ"), BENCH("ม้านั่ง"), HOUSE("สีบ้าน"),
}

enum class Decoration(
    val thaiName: String,
    val slot: DecorationSlot,
    val price: Int,
    val rewardOnly: Boolean = false,
) {
    WOOD_FENCE("รั้วไม้แสนอบอุ่น", DecorationSlot.FENCE, 80),
    STONE_PATH("ทางเดินหิน", DecorationSlot.PATH, 65),
    WARM_LAMP("โคมไฟแสงอุ่น", DecorationSlot.LAMP, 110),
    GARDEN_BENCH("ม้านั่งพักใจ", DecorationSlot.BENCH, 140),
    HOUSE_MINT("บ้านสีมิ้นต์", DecorationSlot.HOUSE, 120),
    HOUSE_PEACH("บ้านสีพีช", DecorationSlot.HOUSE, 120),
    FLOWER_FENCE("รั้วดอกไม้รางวัลนักปลูก", DecorationSlot.FENCE, 0, true),
    STAR_LAMP("โคมดาวรางวัลชาวสวน", DecorationSlot.LAMP, 0, true),
}

data class CollectionBadge(val crop: CropType, val threshold: Int, val reward: Decoration) {
    val thaiName: String get() = "ชาวสวน${crop.thaiName} $threshold ต้น"

    companion object {
        /** Each crop has its own badges; shared cosmetic rewards are granted once, never sold. */
        fun forCrop(crop: CropType): List<CollectionBadge> = listOf(
            CollectionBadge(crop, 10, Decoration.FLOWER_FENCE),
            CollectionBadge(crop, 50, Decoration.STAR_LAMP),
        )
    }
}

data class NeighborOrder(
    val crop: CropType,
    val quantity: Int,
    val coins: Int,
    val xp: Int,
    val npc: String,
    val title: String,
) {
    companion object {
        // Order XP alone unlocks every new crop before its first request; harvesting helps sooner.
        // Selection uses the saved order index only, so leveling up never changes an active request.
        private val rotation = listOf(
            NeighborOrder(CropType.LETTUCE, 2, 45, 20, "คุณยาย", "แกงจืดอุ่น ๆ ของคุณยาย"),
            NeighborOrder(CropType.CARROT, 2, 170, 30, "พี่ต้น", "ซุปแครอตร้านอาหาร"),
            NeighborOrder(CropType.RADISH, 3, 140, 25, "ป้าพร", "หัวไชเท้าดองแบ่งเพื่อน"),
            NeighborOrder(CropType.PUMPKIN, 1, 155, 35, "น้องเมย์", "ขนมฟักทองยามบ่าย"),
            NeighborOrder(CropType.LETTUCE, 3, 70, 30, "คุณยาย", "ตะกร้าผักให้เพื่อนบ้าน"),
            NeighborOrder(CropType.TOMATO, 2, 225, 40, "พี่ต้น", "ซอสมะเขือเทศโฮมเมด"),
            NeighborOrder(CropType.RADISH, 4, 190, 30, "ป้าพร", "แบ่งความอร่อยทั้งซอย"),
            NeighborOrder(CropType.STRAWBERRY, 2, 390, 50, "น้องเมย์", "แยมสตรอว์เบอร์รี"),
            NeighborOrder(CropType.PUMPKIN, 2, 310, 50, "พี่ต้น", "ซุปฟักทองหม้อใหญ่"),
            NeighborOrder(CropType.FLOWER, 2, 480, 60, "คุณยาย", "ดอกไม้ประดับโต๊ะน้ำชา"),
        )

        fun at(completedOrders: Int): NeighborOrder = rotation[completedOrders.coerceAtLeast(0) % rotation.size]
    }
}

enum class CatPose(val thaiName: String, val requiredBond: Int) {
    SITTING("นั่งเป็นเพื่อน", 0), PURRING("เคลิ้มตอนลูบหัว", 5), ROLLING("กลิ้งอ้อนโชว์พุง", 15),
}

data class CatState(
    val name: String = "มะลิ",
    val bond: Int = 0,
    val lastPettedAtMillis: Long? = null,
) {
    val unlockedPoses: List<CatPose> get() = CatPose.entries.filter { bond >= it.requiredBond }
    val pose: CatPose get() = unlockedPoses.lastOrNull() ?: CatPose.SITTING

    fun petCooldownRemaining(nowMillis: Long): Long {
        val last = lastPettedAtMillis ?: return 0L
        if (nowMillis < last) return PET_COOLDOWN_MILLIS // Clock rollback cannot farm affection.
        val elapsed = nowMillis - last
        return if (elapsed >= PET_COOLDOWN_MILLIS) 0L else PET_COOLDOWN_MILLIS - elapsed
    }

    fun canPet(nowMillis: Long): Boolean = nowMillis >= 0 && petCooldownRemaining(nowMillis) == 0L

    companion object {
        const val PET_COOLDOWN_MILLIS = 60_000L
        const val MAX_NAME_LENGTH = 24

        fun isValidName(name: String): Boolean = name.isNotBlank() &&
            name == name.trim() && name.length <= MAX_NAME_LENGTH && name.none { it.isISOControl() }
    }
}
