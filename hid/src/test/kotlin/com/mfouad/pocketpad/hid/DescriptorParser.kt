package com.mfouad.pocketpad.hid

/**
 * Minimal HID 1.11 short-item parser used only to validate the descriptor in tests.
 *
 * Item prefix: bTag bits 7..4, bType bits 3..2, bSize bits 1..0 (0, 1, 2 or 4 data bytes).
 * Item data is little-endian. Logical Minimum/Maximum are signed; other items unsigned.
 */
class DescriptorParser(
    private val bytes: ByteArray,
) {
    enum class MainKind { INPUT, OUTPUT, FEATURE }

    data class Field(
        val kind: MainKind,
        val reportId: Int,
        val usagePage: Int,
        val reportSize: Int,
        val reportCount: Int,
        val logicalMin: Long,
        val logicalMax: Long,
        val flags: Int,
    ) {
        val isConstant get() = flags and 0x01 != 0
        val isVariable get() = flags and 0x02 != 0
        val isRelative get() = flags and 0x04 != 0
    }

    data class Result(
        val fields: List<Field>,
        val topLevelCollections: List<Int>,
    ) {
        /** Bits per report, keyed by report ID (excluding the ID byte). */
        fun reportBits(kind: MainKind): Map<Int, Int> =
            fields
                .filter { it.kind == kind }
                .groupBy { it.reportId }
                .mapValues { (_, fs) -> fs.sumOf { it.reportSize * it.reportCount } }
    }

    private data class Globals(
        var usagePage: Int = 0,
        var logicalMin: Long = 0,
        var logicalMax: Long = 0,
        var reportSize: Int = 0,
        var reportCount: Int = 0,
        var reportId: Int = 0,
    )

    private data class Item(
        val offset: Int,
        val type: Int,
        val tag: Int,
        val size: Int,
        val unsigned: Long,
        val signed: Long,
    )

    private var globals = Globals()
    private val stack = ArrayDeque<Globals>()
    private val fields = mutableListOf<Field>()
    private val topLevel = mutableListOf<Int>()
    private var depth = 0

    fun parse(): Result {
        var i = 0
        while (i < bytes.size) {
            val item = readItem(i)
            when (item.type) {
                MAIN -> handleMain(item)

                GLOBAL -> handleGlobal(item)

                LOCAL -> Unit

                // usages are not needed for sizing
                else -> error("Reserved item type at offset $i")
            }
            i += 1 + item.size
        }
        require(i == bytes.size) { "Descriptor does not end on an item boundary" }
        require(depth == 0) { "Unclosed collection" }
        return Result(fields.toList(), topLevel.toList())
    }

    private fun readItem(offset: Int): Item {
        val prefix = bytes[offset].toInt() and 0xFF
        require(prefix != LONG_ITEM_PREFIX) { "Long items are not expected at offset $offset" }
        val size = intArrayOf(0, 1, 2, 4)[prefix and 0x03]
        require(offset + 1 + size <= bytes.size) { "Item at offset $offset runs past the end" }
        var v = 0L
        for (k in 0 until size) v = v or ((bytes[offset + 1 + k].toLong() and 0xFF) shl (8 * k))
        val signed =
            if (size == 0) {
                0L
            } else {
                val bits = size * 8
                if (v and (1L shl (bits - 1)) != 0L) v - (1L shl bits) else v
            }
        return Item(offset, (prefix shr 2) and 0x03, (prefix shr 4) and 0x0F, size, v, signed)
    }

    private fun handleMain(item: Item) {
        when (item.tag) {
            TAG_INPUT -> {
                addField(MainKind.INPUT, item)
            }

            TAG_OUTPUT -> {
                addField(MainKind.OUTPUT, item)
            }

            TAG_FEATURE -> {
                addField(MainKind.FEATURE, item)
            }

            TAG_COLLECTION -> {
                if (depth == 0) topLevel += item.unsigned.toInt()
                depth++
            }

            TAG_END_COLLECTION -> {
                require(depth > 0) { "Unbalanced End Collection at offset ${item.offset}" }
                depth--
            }

            else -> {
                error("Unknown main item tag ${item.tag} at offset ${item.offset}")
            }
        }
    }

    private fun addField(
        kind: MainKind,
        item: Item,
    ) {
        require(depth > 0) { "Main data item outside a collection at offset ${item.offset}" }
        val g = globals
        fields +=
            Field(
                kind,
                g.reportId,
                g.usagePage,
                g.reportSize,
                g.reportCount,
                g.logicalMin,
                g.logicalMax,
                item.unsigned.toInt(),
            )
    }

    private fun handleGlobal(item: Item) {
        when (item.tag) {
            0x0 -> globals.usagePage = item.unsigned.toInt()
            0x1 -> globals.logicalMin = item.signed
            0x2 -> globals.logicalMax = item.signed
            0x7 -> globals.reportSize = item.unsigned.toInt()
            0x8 -> globals.reportId = item.unsigned.toInt()
            0x9 -> globals.reportCount = item.unsigned.toInt()
            0xA -> stack.addLast(globals.copy())
            0xB -> globals = stack.removeLast()
            else -> Unit // physical min/max, unit, unit exponent: not used for sizing
        }
    }

    private companion object {
        const val LONG_ITEM_PREFIX = 0xFE
        const val MAIN = 0
        const val GLOBAL = 1
        const val LOCAL = 2
        const val TAG_INPUT = 0x8
        const val TAG_OUTPUT = 0x9
        const val TAG_COLLECTION = 0xA
        const val TAG_FEATURE = 0xB
        const val TAG_END_COLLECTION = 0xC
    }
}
