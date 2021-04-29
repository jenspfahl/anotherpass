package de.jepfa.yapm.usecase

import de.jepfa.yapm.model.Session
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.util.ClipboardUtil

object LockVaultUseCase: SecureActivityUseCase {

    override fun execute(activity: SecureActivity): Boolean {
        Session.lock()
        activity.closeOverlayDialogs()
        SecureActivity.SecretChecker.getOrAskForSecret(activity)
        ClipboardUtil.clearClips(activity)

        return true
    }
}