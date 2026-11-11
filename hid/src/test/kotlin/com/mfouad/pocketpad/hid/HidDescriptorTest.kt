package com.mfouad.pocketpad.hid

import com.mfouad.pocketpad.hid.DescriptorParser.MainKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HidDescriptorTest {
    private val parsed = DescriptorParser(HidDescriptor.bytes()).parse()

    @Test
    fun lengthMatchesDocumentationAndSdpLimit() {
        // docs/HID.md section 3: keyboard 66 + mouse 75 + consumer 25.
        assertEquals(166, HidDescriptor.bytes().size)
        assertTrue(HidDescriptor.bytes().size <= HidDescriptor.MAX_SDP_DESCRIPTOR_LENGTH)
    }

    @Test
    fun hasThreeApplicationCollections() {
        val application = 0x01
        assertEquals(listOf(application, application, application), parsed.topLevelCollections)
    }

    @Test
    fun everyDataItemHasANonZeroReportId() {
        assertTrue(parsed.fields.all { it.reportId != 0 }, "Report ID 0 is reserved and every item needs an ID")
    }

    @Test
    fun inputReportSizesMatchEncoders() {
        val bits = parsed.reportBits(MainKind.INPUT)
        assertEquals(
            mapOf(
                HidDescriptor.REPORT_ID_KEYBOARD to HidDescriptor.KEYBOARD_INPUT_SIZE * 8,
                HidDescriptor.REPORT_ID_MOUSE to HidDescriptor.MOUSE_INPUT_SIZE * 8,
                HidDescriptor.REPORT_ID_CONSUMER to HidDescriptor.CONSUMER_INPUT_SIZE * 8,
            ),
            bits,
        )
        assertEquals(bits.getValue(1), ReportEncoder.keyboard(KeyboardState.NEUTRAL).size * 8)
        assertEquals(bits.getValue(2), ReportEncoder.mouse(0, 0, 0).size * 8)
        assertEquals(bits.getValue(3), ReportEncoder.consumer(0).size * 8)
    }

    @Test
    fun outputAndFeatureReports() {
        assertEquals(
            mapOf(HidDescriptor.REPORT_ID_KEYBOARD to HidDescriptor.KEYBOARD_OUTPUT_SIZE * 8),
            parsed.reportBits(MainKind.OUTPUT),
        )
        assertEquals(emptyMap(), parsed.reportBits(MainKind.FEATURE))
    }

    @Test
    fun everyReportIsByteAligned() {
        for (kind in MainKind.entries) {
            parsed.reportBits(kind).forEach { (id, bits) -> assertEquals(0, bits % 8, "$kind report $id") }
        }
    }

    @Test
    fun noFieldSpansMoreThanFourBytes() {
        assertTrue(parsed.fields.all { it.reportSize <= 32 })
    }

    @Test
    fun variableFieldRangesFitTheirSize() {
        parsed.fields.filter { !it.isConstant }.forEach { f ->
            if (f.logicalMin < 0) {
                val limit = 1L shl (f.reportSize - 1)
                assertTrue(f.logicalMin >= -limit && f.logicalMax < limit, "signed range does not fit: $f")
            } else {
                assertTrue(f.logicalMax < (1L shl f.reportSize), "unsigned range does not fit: $f")
            }
        }
    }

    @Test
    fun mouseFieldsHaveTheDocumentedRangesAndAreRelative() {
        val mouse = parsed.fields.filter { it.reportId == HidDescriptor.REPORT_ID_MOUSE && !it.isConstant }
        val buttons = mouse[0]
        assertEquals(
            Triple(1, 5, 0L..1L),
            Triple(
                buttons.reportSize,
                buttons.reportCount,
                buttons.logicalMin..buttons.logicalMax,
            ),
        )
        val xy = mouse[1]
        assertEquals(
            Triple(16, 2, -32767L..32767L),
            Triple(xy.reportSize, xy.reportCount, xy.logicalMin..xy.logicalMax),
        )
        assertTrue(xy.isRelative && xy.isVariable)
        val wheel = mouse[2]
        assertEquals(
            Triple(8, 1, -127L..127L),
            Triple(
                wheel.reportSize,
                wheel.reportCount,
                wheel.logicalMin..wheel.logicalMax,
            ),
        )
        val pan = mouse[3]
        assertEquals(0x0C, pan.usagePage)
        assertEquals(Triple(8, 1, -127L..127L), Triple(pan.reportSize, pan.reportCount, pan.logicalMin..pan.logicalMax))
        assertTrue(wheel.isRelative && pan.isRelative)
    }

    @Test
    fun keyboardArrayCoversAllUsagesAndConsumerArrayMatchesEncoderLimit() {
        val keyArray =
            parsed.fields.last {
                it.reportId == HidDescriptor.REPORT_ID_KEYBOARD && it.kind == MainKind.INPUT
            }
        assertEquals(0L..255L, keyArray.logicalMin..keyArray.logicalMax)
        val consumer = parsed.fields.single { it.reportId == HidDescriptor.REPORT_ID_CONSUMER }
        assertEquals(0L..ConsumerUsage.MAX.toLong(), consumer.logicalMin..consumer.logicalMax)
        assertEquals(false, consumer.isVariable)
    }

    @Test
    fun bytesAreACopy() {
        val a = HidDescriptor.bytes()
        a[0] = 0
        assertEquals(0x05, HidDescriptor.bytes()[0].toInt())
    }
}
