package my.github.MrxSiN.pixellockscreenevolved.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFrameTest {

    @Test
    fun aClockThatNeverStoredAFrameIsNatural() {
        assertEquals(ClockFrame.NATURAL, ClockFrame.of(null, null, null))
    }

    @Test
    fun aStoredFrameIsKeptWithinWhatAFaceCanShow() {
        assertEquals(ClockFrame(ClockFrame.MAX_SCALE, ClockFrame.MIN_SCALE, -ClockFrame.MAX_SHIFT), ClockFrame.of(9f, 0f, -3f))
        assertEquals(ClockFrame(1.3f, 0.8f, 0.1f), ClockFrame.of(1.3f, 0.8f, 0.1f))
    }
}
