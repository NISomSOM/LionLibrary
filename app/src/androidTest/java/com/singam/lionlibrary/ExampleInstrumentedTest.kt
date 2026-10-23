package com.singam.lionlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test running on an Android device or emulator.
 *
 * Reference: [Android Testing Documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Retrieve application context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.singam.lionlibrary", appContext.packageName)
    }
}
