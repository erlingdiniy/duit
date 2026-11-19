package com.diniy.duit.update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class BetaInstallStatusReceiverTest {
    private val app = RuntimeEnvironment.getApplication()
    private val installer = app.packageManager.packageInstaller
    private val receiver = BetaInstallStatusReceiver()

    @Test
    fun ignoresCallbacksWithWrongActionOrSession() {
        val id = createSession()
        receiver.onReceive(app, status(id).setAction(Intent.ACTION_VIEW))
        receiver.onReceive(app, status(id).putExtra(PackageInstaller.EXTRA_SESSION_ID, id + 1))
        assertNotNull(installer.getSessionInfo(id))
    }

    @Test
    fun abandonsOrphanedPendingConfirmationForOwnPackage() {
        val id = createSession()
        receiver.onReceive(app, status(id))
        assertNull(installer.getSessionInfo(id))
    }

    @Test
    fun leavesOtherOwnedSessionsAndTerminalUpdatesAlone() {
        val otherId = createSession("com.example.other")
        receiver.onReceive(app, status(otherId))
        assertNotNull(installer.getSessionInfo(otherId))

        val id = createSession()
        receiver.onReceive(app, status(id).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS))
        assertNotNull(installer.getSessionInfo(id))
    }

    private fun createSession(packageName: String = app.packageName): Int =
        installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
            }
        )

    private fun status(sessionId: Int): Intent =
        Intent("com.diniy.duit.BETA_INSTALL_STATUS")
            .setData(Uri.parse("duit-beta-install://${app.packageName}/$sessionId/test"))
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
}
