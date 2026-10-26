package com.vladutu.copilot.update

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerTest {

    // Eager dispatcher: launch {} runs to its first suspension inline, so state is observable
    // right after checkNow()/install() without scheduler choreography.
    private fun TestScope.eager() = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private val update = UpdateChecker.Update("0.31.0", "https://x/copilot-0.31.0.apk")

    @Test fun `starts idle and becomes available after a successful check`() = runTest {
        val manager = UpdateManager(eager(), check = { update }, download = { File("unused") }, install = {})
        manager.state.test {
            assertEquals(UpdateManager.State.Idle, awaitItem())
            manager.checkNow()
            assertEquals(UpdateManager.State.Available(update), awaitItem())
        }
    }

    @Test fun `stays idle when there is nothing newer`() = runTest {
        val manager = UpdateManager(eager(), check = { null }, download = { File("unused") }, install = {})
        manager.checkNow()
        assertEquals(UpdateManager.State.Idle, manager.state.value)
    }

    @Test fun `install downloads then hands the file to the installer`() = runTest {
        val apk = File("copilot-0.31.0.apk")
        var installed: File? = null
        val gate = CompletableDeferred<Unit>()
        val manager = UpdateManager(
            eager(),
            check = { update },
            download = { gate.await(); apk },
            install = { installed = it },
        )
        manager.checkNow()
        manager.state.test {
            assertEquals(UpdateManager.State.Available(update), awaitItem())
            manager.install()
            assertEquals(UpdateManager.State.Downloading(update), awaitItem())
            gate.complete(Unit)
            assertEquals(UpdateManager.State.Available(update), awaitItem())
            assertEquals(apk, installed)
        }
    }

    @Test fun `a failed download goes back to available with an error`() = runTest {
        val manager = UpdateManager(
            eager(),
            check = { update },
            download = { throw IOException("no network") },
            install = {},
        )
        manager.checkNow()
        manager.install()
        val state = manager.state.value
        assertTrue(state is UpdateManager.State.Available)
        assertEquals("no network", (state as UpdateManager.State.Available).error)
    }

    @Test fun `install is ignored while already downloading`() = runTest {
        var downloads = 0
        val gate = CompletableDeferred<Unit>()
        val manager = UpdateManager(
            eager(),
            check = { update },
            download = { downloads++; gate.await(); File("x") },
            install = {},
        )
        manager.checkNow()
        manager.install()
        manager.install()
        gate.complete(Unit)
        assertEquals(1, downloads)
    }

    @Test fun `install does nothing when no update is known`() = runTest {
        var downloads = 0
        val manager = UpdateManager(eager(), check = { null }, download = { downloads++; File("x") }, install = {})
        manager.install()
        assertEquals(0, downloads)
    }
}
