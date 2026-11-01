package org.school.neurotranslate.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.school.neurotranslate.settings.normalizeMaxOutputLength

class SettingsValueTest {
    @Test
    fun outputLengthIsRoundedToSupportedStepsAndRange() {
        assertEquals(64, normalizeMaxOutputLength(63f))
        assertEquals(112, normalizeMaxOutputLength(111f))
        assertEquals(400, normalizeMaxOutputLength(401f))
    }
}
