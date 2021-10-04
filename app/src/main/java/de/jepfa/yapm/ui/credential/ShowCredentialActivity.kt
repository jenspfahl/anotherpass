package de.jepfa.yapm.ui.credential

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.setPadding
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.pchmn.materialchips.ChipView
import de.jepfa.yapm.R
import de.jepfa.yapm.model.Session
import de.jepfa.yapm.model.encrypted.EncCredential
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.PreferenceService.PREF_ENABLE_COPY_PASSWORD
import de.jepfa.yapm.service.PreferenceService.PREF_ENABLE_OVERLAY_FEATURE
import de.jepfa.yapm.service.PreferenceService.PREF_MASK_PASSWORD
import de.jepfa.yapm.service.PreferenceService.PREF_PASSWD_WORDS_ON_NL
import de.jepfa.yapm.service.autofill.CurrentCredentialHolder
import de.jepfa.yapm.service.label.LabelService
import de.jepfa.yapm.service.overlay.DetachHelper
import de.jepfa.yapm.service.secret.SecretService.decryptCommonString
import de.jepfa.yapm.service.secret.SecretService.decryptPassword
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.ui.editcredential.EditCredentialActivity
import de.jepfa.yapm.ui.label.LabelDialogOpener
import de.jepfa.yapm.usecase.ExportCredentialUseCase
import de.jepfa.yapm.usecase.LockVaultUseCase
import de.jepfa.yapm.util.ClipboardUtil
import de.jepfa.yapm.util.DebugInfo
import de.jepfa.yapm.util.DeobfuscationDialog
import de.jepfa.yapm.util.PasswordColorizer.spannableObfusableAndMaskableString


class ShowCredentialActivity : SecureActivity() {

    val updateCredentialActivityRequestCode = 1

    private var passwordPresentation = Password.PresentationMode.DEFAULT
    private var maskPassword = false
    private var multiLine = false
    private var obfuscationKey: Key? = null

    private lateinit var credential: EncCredential //TODO change to ?
    private lateinit var appBarLayout: CollapsingToolbarLayout
    private lateinit var titleLayout: LinearLayout
    private lateinit var passwordTextView: TextView
    private lateinit var userTextView: TextView
    private lateinit var websiteTextView: TextView
    private lateinit var additionalInfoTextView: TextView
    private var optionsMenu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_credential)

        val toolbar: Toolbar = findViewById(R.id.activity_credential_detail_toolbar)
        setSupportActionBar(toolbar)

        titleLayout = findViewById(R.id.collapsing_toolbar_layout_title)
        titleLayout.post {
            // this is important to ensure labels are faded out when collapsing the app bar
            val layoutParams = toolbar.layoutParams as CollapsingToolbarLayout.LayoutParams
            layoutParams.height = titleLayout.height
            toolbar.layoutParams = layoutParams
        }

        val idExtra = intent.getIntExtra(EncCredential.EXTRA_CREDENTIAL_ID, -1)

        maskPassword = PreferenceService.getAsBool(PREF_MASK_PASSWORD, this)
        val formatted = PreferenceService.getAsBool(PreferenceService.PREF_PASSWD_SHOW_FORMATTED, this)
        multiLine = PreferenceService.getAsBool(PREF_PASSWD_WORDS_ON_NL, this)
        passwordPresentation = Password.PresentationMode.createFromFlags(multiLine, formatted)

        appBarLayout = findViewById(R.id.credential_detail_toolbar_layout)

        userTextView  = findViewById(R.id.user)
        websiteTextView  = findViewById(R.id.website)
        additionalInfoTextView  = findViewById(R.id.additional_info)
        passwordTextView = findViewById(R.id.passwd)

        websiteTextView.setOnClickListener {
            val uri: Uri? = try {
            Uri.parse(ensureHttp(websiteTextView.text.toString()))
            } catch (e: Exception) {
                null
            }

            if (uri != null) {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(browserIntent)
            }
        }

        passwordTextView.setOnClickListener {
            if (maskPassword) {
                maskPassword = false
            }
            else {
                passwordPresentation =
                    if (multiLine) passwordPresentation.prev()
                    else passwordPresentation.next()
            }
            updatePasswordView(idExtra)
        }

        updatePasswordView(idExtra)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    private fun ensureHttp(s: String): String {
        if (s.startsWith(prefix = "http", ignoreCase = true)) {
            return s
        }
        else {
            return "http://" + s
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.credential_detail_menu, menu)

        val enableCopyPassword = PreferenceService.getAsBool(PREF_ENABLE_COPY_PASSWORD, this)
        if (!enableCopyPassword) {
            menu.findItem(R.id.menu_copy_credential)?.isVisible = false
        }

        val enableOverlayFeature = PreferenceService.getAsBool(PREF_ENABLE_OVERLAY_FEATURE, this)
        if (!enableOverlayFeature) {
            menu.findItem(R.id.menu_detach_credential)?.isVisible = false
        }

        menu.findItem(R.id.menu_deobfuscate_password)?.isVisible = credential.isObfuscated


        optionsMenu = menu

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            val upIntent = Intent(this, ListCredentialsActivity::class.java)
            navigateUpTo(upIntent)
            return true
        }

        if (Session.isDenied()) {
            LockVaultUseCase.execute(this)
            return false
        }

        if (id == R.id.menu_export_credential) {
            ExportCredentialUseCase.openStartExportDialog(credential, obfuscationKey, this)
            return true
        }

        if (id == R.id.menu_lock_items) {
            LockVaultUseCase.execute(this)
            return true
        }

        if (id == R.id.menu_detach_credential) {
            DetachHelper.detachPassword(this, credential.password, obfuscationKey, passwordPresentation)
            return true
        }

        if (id == R.id.menu_copy_credential) {
            ClipboardUtil.copyEncPasswordWithCheck(credential.password, obfuscationKey, this)
            return true
        }

        if (id == R.id.menu_change_credential) {

            val intent = Intent(this, EditCredentialActivity::class.java)
            intent.putExtra(EncCredential.EXTRA_CREDENTIAL_ID, credential.id)

            startActivityForResult(intent, updateCredentialActivityRequestCode)

            return true
        }

        if (id == R.id.menu_delete_credential) {

            masterSecretKey?.let{ key ->
                val decName = decryptCommonString(key, credential.name)

                AlertDialog.Builder(this)
                        .setTitle(R.string.title_delete_credential)
                        .setMessage(getString(R.string.message_delete_credential, decName))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes) { dialog, whichButton ->
                            credentialViewModel.delete(credential)

                            val upIntent = Intent(this, ListCredentialsActivity::class.java)
                            navigateUpTo(upIntent)
                        }
                        .setNegativeButton(android.R.string.no, null)
                        .show()
            }
            return true
        }

        if (id == R.id.menu_deobfuscate_password) {

            masterSecretKey?.let{ key ->

                if (obfuscationKey != null) {
                    obfuscationKey?.clear()
                    obfuscationKey = null
                    item.isChecked = false

                    CurrentCredentialHolder.clear()

                    val originPassword = decryptPassword(key, credential.password)
                    var spannedString =
                        spannableObfusableAndMaskableString(originPassword, passwordPresentation, maskPassword, showObfuscated(credential), this)
                    passwordTextView.text = spannedString
                    originPassword.clear()

                    Toast.makeText(this, R.string.deobfuscate_restored, Toast.LENGTH_LONG).show()
                }
                else {

                    DeobfuscationDialog.openDeobfuscationDialog(this) { deobfuscationKey ->
                        maskPassword = false
                        item.isChecked = true

                        obfuscationKey = deobfuscationKey
                        obfuscationKey?.let {
                            val passwordForDeobfuscation = decryptPassword(key, credential.password)
                            passwordForDeobfuscation.deobfuscate(it)

                            var spannedString =
                                spannableObfusableAndMaskableString(
                                    passwordForDeobfuscation,
                                    passwordPresentation,
                                    maskPassword,
                                    showObfuscated(credential),
                                    this
                                )
                            passwordForDeobfuscation.clear()

                            passwordTextView.text = spannedString
                            CurrentCredentialHolder.update(credential, it)
                        }

                        Toast.makeText(this, R.string.password_deobfuscated, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == updateCredentialActivityRequestCode && resultCode == Activity.RESULT_OK) {
            data?.let {

                val credential = EncCredential.fromIntent(it)
                if (credential.isPersistent()) {
                    obfuscationKey?.clear()
                    obfuscationKey = null
                    optionsMenu?.findItem(R.id.menu_deobfuscate_password)?.isChecked = false
                    credentialViewModel.update(credential)
                }
            }
        }

    }

    override fun lock() {
        obfuscationKey?.clear()
        obfuscationKey = null
        maskPassword = true
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        obfuscationKey?.clear()
    }

    private fun showObfuscated(credential: EncCredential): Boolean {
        return credential.isObfuscated && obfuscationKey == null
    }


    private fun updatePasswordView(idExtra: Int) {
        credentialViewModel.getById(idExtra).observe(this, {
            credential = it

            masterSecretKey?.let{ key ->
                val name = decryptCommonString(key, credential.name)
                val user = decryptCommonString(key, credential.user)
                val website = decryptCommonString(key, credential.website)
                val additionalInfo = decryptCommonString(key, credential.additionalInfo)
                val password = decryptPassword(key, credential.password)
                obfuscationKey?.let {
                    password.deobfuscate(it)
                }

                appBarLayout.title = name

                titleLayout.removeAllViews()

                val labelsForCredential = LabelService.getLabelsForCredential(key, credential)
                if (labelsForCredential.isNotEmpty()) {
                    labelsForCredential.forEachIndexed { idx, it ->
                        val chipView = ChipView(this)
                        // doesnt work: chipView.setChip(it.labelChip)
                        chipView.label = it.labelChip.label
                        chipView.setChipBackgroundColor(it.labelChip.getColor(this))
                        chipView.setLabelColor(getColor(R.color.white))
                        chipView.setPadding(16)
                        chipView.setOnChipClicked {_ ->
                            LabelDialogOpener.openLabelDialog(this, it)
                        }
                        titleLayout.addView(chipView, idx)
                    }
                }
                else {
                    // add blind label to ensure layouting
                    val blindView = TextView(this)
                    blindView.setPadding(16)
                    titleLayout.addView(blindView)
                }

                if (user.isEmpty()) {
                    val userView: ImageView = findViewById(R.id.user_image)
                    userView.visibility = View.INVISIBLE
                }
                userTextView.text = user

                if (website.isEmpty()) {
                    val websiteView: ImageView = findViewById(R.id.website_image)
                    websiteView.visibility = View.INVISIBLE
                }
                websiteTextView.text = website

                additionalInfoTextView.text = additionalInfo

                var spannedString = spannableObfusableAndMaskableString(password, passwordPresentation, maskPassword, showObfuscated(credential),this)
                passwordTextView.text = spannedString

                optionsMenu?.findItem(R.id.menu_deobfuscate_password)?.isVisible = credential.isObfuscated

                if (DebugInfo.isDebug) {
                    passwordTextView.setOnLongClickListener {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                        val icon: Drawable = applicationInfo.loadIcon(packageManager)
                        val message = credential.toString()
                        builder.setTitle(R.string.debug)
                            .setMessage(message)
                            .setIcon(icon)
                            .show()
                        true
                    }
                }
            }

            CurrentCredentialHolder.update(credential, obfuscationKey)
        })
    }

}