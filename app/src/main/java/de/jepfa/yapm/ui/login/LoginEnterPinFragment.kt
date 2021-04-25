package de.jepfa.yapm.ui.login

import android.app.assist.AssistStructure
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.autofill.AutofillManager.EXTRA_ASSIST_STRUCTURE
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import de.jepfa.yapm.R
import de.jepfa.yapm.model.Password
import de.jepfa.yapm.model.Session
import de.jepfa.yapm.service.secret.SecretService
import de.jepfa.yapm.ui.BaseFragment
import de.jepfa.yapm.ui.createvault.CreateVaultActivity
import de.jepfa.yapm.usecase.LoginUseCase
import de.jepfa.yapm.util.PreferenceUtil
import de.jepfa.yapm.util.AsyncWithProgressBar
import de.jepfa.yapm.util.putEncrypted
import java.util.*


class LoginEnterPinFragment : BaseFragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login_enter_pin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, null)

        getBaseActivity().supportActionBar?.setDisplayHomeAsUpEnabled(false)

        val pinTextView: EditText = view.findViewById(R.id.edittext_enter_pin)
        val nextButton = view.findViewById<Button>(R.id.button_login_next)

        pinTextView.setImeOptions(EditorInfo.IME_ACTION_DONE)
        pinTextView.setOnEditorActionListener{ textView, id, keyEvent ->
            nextButton.performClick()
            true
        }

        nextButton.setOnClickListener {

            val keyForTemp = SecretService.getAndroidSecretKey(SecretService.ALIAS_KEY_TRANSPORT)

            val userPin = Password.fromEditable(pinTextView.text)
            if (userPin.isEmpty()) {
                pinTextView.setError(getString(R.string.pin_required))
                pinTextView.requestFocus()

                return@setOnClickListener
            }

            val loginActivity = getBaseActivity() as LoginActivity

            val encStoredMasterPasswd = PreferenceUtil.getEncrypted(PreferenceUtil.DATA_ENCRYPTED_MASTER_PASSWORD, getBaseActivity())

            if (!Session.isLoggedOut()) {
                val keyForTemp = SecretService.getAndroidSecretKey(SecretService.ALIAS_KEY_TRANSPORT)
                val encMasterPasswd = Session.getEncMasterPasswd()
                if (encMasterPasswd == null) {
                    Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val masterPasswd = SecretService.decryptPassword(keyForTemp, encMasterPasswd)

                login(pinTextView, userPin, masterPasswd, loginActivity)
            }
            else if (encStoredMasterPasswd != null) {

                val keyForMP = SecretService.getAndroidSecretKey(SecretService.ALIAS_KEY_MP)
                val storedMasterPasswd = SecretService.decryptPassword(keyForMP, encStoredMasterPasswd)

                login(pinTextView, userPin, storedMasterPasswd, loginActivity)
            } else {
                val encUserPin = SecretService.encryptPassword(keyForTemp, userPin)
                val args = Bundle()
                args.putEncrypted(CreateVaultActivity.ARG_ENC_PIN, encUserPin)

                findNavController().navigate(R.id.action_Login_PinFragment_to_MasterPasswordFragment, args)
            }

        }
    }

    private fun login(
        pinTextView: TextView,
        userPin: Password,
        masterPasswd: Password,
        loginActivity: LoginActivity
    ) {

        loginActivity.hideKeyboard(pinTextView)

        loginActivity.getProgressBar()?.let {

            AsyncWithProgressBar(
                loginActivity,
                {
                    LoginUseCase.execute(
                        userPin,
                        masterPasswd,
                        getBaseActivity()
                    )
                },
                { success ->
                    if (!success) {
                        loginActivity.handleFailedLoginAttempt()
                        pinTextView.setError("${getString(R.string.password_wrong)} ${loginActivity.getLoginAttemptMessage()}")
                        pinTextView.requestFocus()
                        false
                    } else {
                        userPin.clear()
                        masterPasswd.clear()
                        pinTextView.setText("")
                        val args = Bundle()
                        val assistStructure = getBaseActivity().intent.getParcelableExtra<AssistStructure>(EXTRA_ASSIST_STRUCTURE)
                        args.putParcelable(EXTRA_ASSIST_STRUCTURE, assistStructure)
                        findNavController().navigate(R.id.action_Login_to_CredentialList, args)
                        loginActivity.loginSuccessful()
                        true
                    }
                }
            )
        }
    }
}