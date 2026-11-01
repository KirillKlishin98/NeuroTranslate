package org.school.neurotranslate.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {
    @Test
    fun preferencesArePersistedAndCanBeReadBack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository.from(context)
        val previousSettings = repository.settings.first()
        try {
            repository.setThemeMode("Dark")
            repository.setMaxOutputLength(333)

            assertEquals(
                AppSettings(themeModeName = "Dark", maxOutputLength = 336),
                repository.settings.first()
            )
        } finally {
            repository.setThemeMode(previousSettings.themeModeName)
            repository.setMaxOutputLength(previousSettings.maxOutputLength)
        }
    }
}
