package my.github.MrxSiN.pixellockscreenevolved.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockOptionTest {

    private val option = ClockOption("KEY", "Hours", listOf("Solid", "Outline", "Glass"), defaultStep = 1)

    @Test
    fun aClockThatNeverStoredTheOptionIsAtItsDefault() {
        assertEquals(1, option.stepOf(null))
    }

    @Test
    fun aStoredValueIsRoundedToAStepWithinRange() {
        assertEquals(2, option.stepOf(1.6f))
        assertEquals(0, option.stepOf(-3f))
        assertEquals(2, option.stepOf(9f))
    }

    @Test
    fun theLabelNamesTheStep() {
        assertEquals("Hours: Glass", option.describe(2))
        assertEquals("Hours: Glass", option.describe(7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aDefaultOutsideTheValuesIsRejected() {
        ClockOption("KEY", "Hours", listOf("a", "b"), defaultStep = 2)
    }
}
