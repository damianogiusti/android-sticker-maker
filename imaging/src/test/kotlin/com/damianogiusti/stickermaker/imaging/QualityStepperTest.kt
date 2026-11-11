package com.damianogiusti.stickermaker.imaging

import arrow.core.Either
import com.damianogiusti.stickermaker.imaging.export.QualityStepper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QualityStepperTest {

    @Test
    fun `starts at 80 and returns at once when it fits`() {
        val tried = mutableListOf<Int>()
        val result = QualityStepper.encodeWithinLimit(100) { quality -> tried += quality; ByteArray(50) }
        assertEquals(listOf(80), tried)
        assertIs<Either.Right<ByteArray>>(result)
    }

    @Test
    fun `steps down by 10 until the size fits`() {
        val tried = mutableListOf<Int>()
        val result = QualityStepper.encodeWithinLimit(100) { quality ->
            tried += quality
            ByteArray(quality * 2)
        }
        // 160, 140, 120 are too big; 100 fits at quality 50.
        assertEquals(listOf(80, 70, 60, 50), tried)
        assertEquals(100, result.getOrNull()?.size)
    }

    @Test
    fun `fails with the last size when even quality 30 is too big`() {
        val tried = mutableListOf<Int>()
        val result = QualityStepper.encodeWithinLimit(10) { quality -> tried += quality; ByteArray(quality) }
        assertEquals(listOf(80, 70, 60, 50, 40, 30), tried)
        val error = assertIs<Either.Left<ExportError>>(result).value
        assertEquals(ExportError.StickerTooLarge(lastSizeBytes = 30, limitBytes = 10), error)
    }

    @Test
    fun `a result exactly at the limit is accepted`() {
        val result = QualityStepper.encodeWithinLimit(64) { ByteArray(64) }
        assertEquals(64, result.getOrNull()?.size)
    }
}
