package de.jepfa.yapm.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import de.jepfa.yapm.R
import de.jepfa.yapm.model.Session
import de.jepfa.yapm.ui.BaseActivity
import de.jepfa.yapm.ui.createvault.CreateVaultActivity
import de.jepfa.yapm.ui.importvault.ImportVaultActivity
import de.jepfa.yapm.util.Constants
import de.jepfa.yapm.util.PreferenceUtil
import de.jepfa.yapm.util.PreferenceUtil.PREF_ENCRYPTED_MASTER_KEY
import de.jepfa.yapm.util.PreferenceUtil.PREF_MAX_LOGIN_ATTEMPTS


class LoginActivity : BaseActivity() {

    val DEFAULT_MAX_LOGIN_ATTEMPTS = 3
    var loginAttempts = 0

    val createVaultActivityRequestCode = 1
    val importVaultActivityRequestCode = 2

    private lateinit var viewProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)

        if (PreferenceUtil.isPresent(PREF_ENCRYPTED_MASTER_KEY, this)) {
            setContentView(R.layout.activity_login)
            viewProgressBar = findViewById(R.id.progressBar);

        }
        else {
            setContentView(R.layout.activity_create_or_import_vault)
            val buttonCreateVault: Button = findViewById(R.id.button_create_vault)
            buttonCreateVault.setOnClickListener {
                val intent = Intent(this@LoginActivity, CreateVaultActivity::class.java)
                startActivityForResult(intent, importVaultActivityRequestCode)
            }
            val buttonImportVault: Button = findViewById(R.id.button_import_vault)
            buttonImportVault.setOnClickListener {
                val intent = Intent(this@LoginActivity, ImportVaultActivity::class.java)
                startActivityForResult(intent, createVaultActivityRequestCode)
            }
        }
        setSupportActionBar(findViewById(R.id.toolbar))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_login, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_login_help) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Constants.HOMEPAGE)
            startActivity(browserIntent)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == createVaultActivityRequestCode && resultCode == Activity.RESULT_OK) {
            recreate()
        }
        if (requestCode == importVaultActivityRequestCode && resultCode == Activity.RESULT_OK) {
            recreate()
        }
    }

    fun handleFailedLoginAttempt() {
        loginAttempts++
        if (loginAttempts >= getMaxLoginAttempts()) {
            Toast.makeText(baseContext, R.string.too_may_wrong_logins, Toast.LENGTH_LONG).show()
            PreferenceUtil.delete(PreferenceUtil.PREF_ENCRYPTED_MASTER_PASSWORD, baseContext)
            PreferenceUtil.delete(PreferenceUtil.PREF_MASTER_PASSWORD_TOKEN_KEY, baseContext)
            Session.logout()
            finishAffinity()
            finishAndRemoveTask()
        }
    }

    fun getLoginAttemptMessage(): String {
        return "(attempt $loginAttempts of ${getMaxLoginAttempts()})"
    }

    fun loginSuccessful() {
        loginAttempts = 0
        finishAffinity()
    }

    fun getProgressBar(): ProgressBar {
        return viewProgressBar
    }

    private fun getMaxLoginAttempts(): Int {
        return PreferenceUtil.getInt(PREF_MAX_LOGIN_ATTEMPTS, DEFAULT_MAX_LOGIN_ATTEMPTS, this)
    }
}