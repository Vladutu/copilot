package com.vladutu.copilot.bubble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BubbleControllerTest {

    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BubbleController.clear(context)
    }

    @Test fun `request while Copilot is backgrounded shows the bubble immediately`() {
        // Remote launch (phone -> Pilot -> Copilot) while Waze/launcher is on screen:
        // no activity pause follows, so the request itself must mark the bubble visible
        // or the BACK grabber passes the press through to Waze.
        BubbleController.requestShow(context)
        assertTrue(BubbleController.isVisible())
    }

    @Test fun `request arriving after the launched app already paused Copilot shows the bubble`() {
        // Phone -> Pilot -> Copilot with Copilot in the foreground: ListenerService starts
        // Waze on Main, then saves history on IO before requesting the bubble. Waze pauses
        // MainActivity in that gap (no request pending yet), so the request lands on HIDDEN.
        BubbleController.onActivityResumed(context)
        BubbleController.onActivityPaused(context)
        assertFalse(BubbleController.isVisible())

        BubbleController.requestShow(context)
        assertTrue(BubbleController.isVisible())
    }

    @Test fun `request while Copilot is foreground defers until the activity pauses`() {
        BubbleController.onActivityResumed(context)
        BubbleController.requestShow(context)
        assertFalse(BubbleController.isVisible())

        BubbleController.onActivityPaused(context)
        assertTrue(BubbleController.isVisible())
    }

    @Test fun `resuming Copilot hides the bubble`() {
        BubbleController.requestShow(context)
        BubbleController.onActivityResumed(context)
        assertFalse(BubbleController.isVisible())
    }

    @Test fun `dismissing from the notification hides the bubble and forgets the request`() {
        BubbleController.requestShow(context)
        BubbleController.onDismissed()
        assertFalse(BubbleController.isVisible())

        // A later pause must not resurrect a bubble the driver explicitly dismissed.
        BubbleController.onActivityResumed(context)
        BubbleController.onActivityPaused(context)
        assertFalse(BubbleController.isVisible())
    }
}
