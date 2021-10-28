package de.jepfa.yapm.usecase.label

import de.jepfa.yapm.service.label.LabelFilter
import de.jepfa.yapm.service.label.LabelService
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.ui.label.Label
import de.jepfa.yapm.usecase.InputUseCase
import de.jepfa.yapm.util.observeOnce


object DeleteLabelUseCase: InputUseCase<Label, SecureActivity>() {

    override fun doExecute(label: Label, activity: SecureActivity): Boolean {
        val key = activity.masterSecretKey
        val labelId = label.labelId
        if (key != null && labelId != null) {
            val credentialsToUpdate = LabelService.getCredentialIdsForLabelId(labelId)
            credentialsToUpdate?.forEach { credentialId ->
                activity.credentialViewModel.getById(credentialId).observeOnce(activity) { credential ->
                    credential?.let {
                        val labels = LabelService.decryptLabelsForCredential(key, credential)

                        val remainingLabelChips = labels
                            .filterNot { it.labelId == labelId}
                            .map { it.name }
                        LabelService.encryptLabelIds(key, remainingLabelChips)
                        LabelService.updateLabelsForCredential(key, credential)
                    }
                }

            }
            LabelService.removeLabel(label)
            LabelFilter.unsetFilterFor(label)
            activity.labelViewModel.deleteById(label.labelId)
        }

        return true
    }

}