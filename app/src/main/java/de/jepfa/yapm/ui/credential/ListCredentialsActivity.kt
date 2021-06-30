package de.jepfa.yapm.ui.credential

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuItemCompat
import androidx.core.view.setPadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.pchmn.materialchips.ChipView
import de.jepfa.yapm.R
import de.jepfa.yapm.model.Session
import de.jepfa.yapm.model.encrypted.EncCredential
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.autofill.CurrentCredentialHolder
import de.jepfa.yapm.service.autofill.ResponseFiller
import de.jepfa.yapm.service.label.LabelFilter
import de.jepfa.yapm.service.label.LabelService
import de.jepfa.yapm.service.secret.MasterPasswordService.getMasterPasswordFromSession
import de.jepfa.yapm.service.secret.MasterPasswordService.storeMasterPassword
import de.jepfa.yapm.service.secret.SecretService
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.ui.changelogin.ChangeMasterPasswordActivity
import de.jepfa.yapm.ui.changelogin.ChangePinActivity
import de.jepfa.yapm.ui.editcredential.EditCredentialActivity
import de.jepfa.yapm.ui.exportvault.ExportVaultActivity
import de.jepfa.yapm.ui.label.ListLabelsActivity
import de.jepfa.yapm.ui.settings.SettingsActivity
import de.jepfa.yapm.usecase.*
import de.jepfa.yapm.util.*
import de.jepfa.yapm.util.DebugInfo.getVersionName
import de.jepfa.yapm.service.PreferenceService.DATA_ENCRYPTED_MASTER_PASSWORD
import de.jepfa.yapm.service.PreferenceService.PREF_SORT_BY_RECENT

class ListCredentialsActivity : SecureActivity(), NavigationView.OnNavigationItemSelectedListener  {

    var assistStructure: AssistStructure? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle

    val newOrUpdateCredentialActivityRequestCode = 1

    private var listCredentialAdapter: ListCredentialAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_credentials)
        val toolbar: Toolbar = findViewById(R.id.list_credentials_toolbar)
        setSupportActionBar(toolbar)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        listCredentialAdapter = ListCredentialAdapter(this)
        recyclerView.adapter = listCredentialAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        CurrentCredentialHolder.currentCredential = null
        assistStructure = intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)

        refreshCredentials()

        labelViewModel.allLabels.observe(this, { labels ->
            masterSecretKey?.let{ key ->
                LabelService.initLabels(key, labels.toSet())
            }
        })

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this@ListCredentialsActivity, EditCredentialActivity::class.java)
            startActivityForResult(intent, newOrUpdateCredentialActivityRequestCode)
        }

        fab.setOnLongClickListener {
            it.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        view.x = event.getRawX() - (view.getWidth() / 2)
                        view.y= event.getRawY() - (view.getHeight())
                    }
                    MotionEvent.ACTION_UP -> view.setOnTouchListener(null)
                    else -> {
                    }
                }
                true
            }
            true
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_drawer_view)

        navigationView.setNavigationItemSelectedListener(this)
        refreshMenuMasterPasswordItem(navigationView.menu)
        refreshMenuDebugItem(navigationView.menu)

        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onResume() {
        super.onResume()
        listCredentialAdapter?.notifyDataSetChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        toggle.onConfigurationChanged(newConfig)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        if (Session.isDenied()) {
            LockVaultUseCase.execute(this)
            return false
        }

        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem: MenuItem = menu.findItem(R.id.action_search)
        if (searchItem != null) {
            val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
            searchView.setOnCloseListener(object : SearchView.OnCloseListener {
                override fun onClose(): Boolean {
                    return true
                }
            })

            val searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_src_text) as EditText
            searchPlate.hint = getString(R.string.search)
            val searchPlateView: View =
                searchView.findViewById(androidx.appcompat.R.id.search_plate)
            searchPlateView.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    android.R.color.transparent
                )
            )

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchItem.collapseActionView()
                    return false
                }

                override fun onQueryTextChange(s: String?): Boolean {
                    listCredentialAdapter?.filter?.filter(s)

                    return false
                }
            })

            val searchManager =
                    getSystemService(Context.SEARCH_SERVICE) as SearchManager
            searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        }

        refreshMenuLockItem(menu.findItem(R.id.menu_lock_items))
        refreshMenuFiltersItem(menu.findItem(R.id.menu_filter))
        refreshMenuSortedByItem(menu.findItem(R.id.menu_sort_by_recent))

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (toggle.onOptionsItemSelected(item)) {
            return true
        }

        return when (item.itemId) {
            R.id.menu_lock_items -> {
                LockVaultUseCase.execute(this)
                refreshMenuLockItem(item)
                return true
            }
            R.id.menu_logout -> {
                return LogoutUseCase.execute(this)
            }
            R.id.menu_filter -> {
                val inflater: LayoutInflater = layoutInflater
                val labelsView: View = inflater.inflate(R.layout.content_dynamic_labels_list, null)
                val labelsContainer: LinearLayout = labelsView.findViewById(R.id.dynamic_labels)

                val allLabels = LabelService.getAllLabels()
                val checkBoxes = ArrayList<CheckBox>(allLabels.size)

                allLabels.forEachIndexed { idx, it ->
                    val chipView = ChipView(this)
                    // doesnt work: chipView.setChip(it.labelChip)
                    chipView.label = it.labelChip.label
                    chipView.setChipBackgroundColor(it.labelChip.getColor(this))
                    chipView.setLabelColor(getColor(R.color.white))

                    chipView.setPadding(16)
                    val row = LinearLayout(this)
                    val checkBox = CheckBox(this)
                    checkBox.isChecked = LabelFilter.isFilterFor(it)
                    checkBoxes.add(checkBox)

                    row.addView(checkBox)
                    row.addView(chipView)
                    labelsContainer.addView(row)
                }

                val dialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.filter))
                    .setIcon(R.drawable.ic_baseline_filter_list_24)
                    .setView(labelsView)
                    .setNeutralButton(getString(R.string.deselect_all), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()

                dialog.setOnShowListener {
                    val buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    buttonPositive.setOnClickListener {
                        for (i in 0 until checkBoxes.size) {
                            val checked = checkBoxes[i].isChecked
                            val labelId = allLabels[i].encLabel.id
                            if (labelId != null) {
                                val label = LabelService.lookupByLabelId(labelId)
                                if (label != null) {
                                    if (checked) {
                                        LabelFilter.setFilterFor(label)
                                    } else {
                                        LabelFilter.unsetFilterFor(label)
                                    }
                                }
                            }
                        }

                        listCredentialAdapter?.filter?.filter("")
                        refreshMenuFiltersItem(item)
                        dialog.dismiss()
                    }

                    val buttonNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    buttonNegative.setOnClickListener {
                        dialog.dismiss()
                    }

                    val buttonNeutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    buttonNeutral.setOnClickListener {
                        checkBoxes.forEach { it.isChecked = false }
                    }
                }

                dialog.show()

                return true
            }
            R.id.menu_sort_by_recent -> {
                PreferenceService.toggleBoolean(PREF_SORT_BY_RECENT, this)
                refreshMenuSortedByItem(item)
                refreshCredentials()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == newOrUpdateCredentialActivityRequestCode && resultCode == Activity.RESULT_OK) {
            data?.let {

                val credential = EncCredential.fromIntent(it)
                if (credential.isPersistent()) {
                    credentialViewModel.update(credential)
                }
                else {
                    credentialViewModel.insert(credential)
                }
            }
        }

    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        else {
            super.onBackPressed()
        }
    }

    override fun lock() {
        LabelService.clearAll()
        listCredentialAdapter?.notifyDataSetChanged()
    }

    fun shouldPushBackAutoFill() : Boolean {
        return assistStructure != null
    }

    fun pushBackAutofill(credential: EncCredential) {
        val structure = assistStructure
        if (structure != null) {
            CurrentCredentialHolder.currentCredential = credential
            val replyIntent = Intent().apply {
                val fillResponse = ResponseFiller.createFillResponse(
                    structure,
                    null,
                    false,
                    applicationContext
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(EXTRA_AUTHENTICATION_RESULT, fillResponse)
                }
            }
            assistStructure = null
            setResult(Activity.RESULT_OK, replyIntent)
            finish()

        }

    }

    private fun refreshMenuLockItem(lockItem: MenuItem) {
        val secret = SecretChecker.getOrAskForSecret(this)
        if (secret.isDenied()) {
            lockItem.setIcon(R.drawable.ic_lock_outline_white_24dp)
        } else {
            lockItem.setIcon(R.drawable.ic_lock_open_white_24dp)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        drawerLayout.closeDrawer(GravityCompat.START)

        when (item.itemId) {

            R.id.store_masterpasswd -> {
                val masterPasswd = getMasterPasswordFromSession()
                if (masterPasswd != null) {

                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.store_masterpasswd))
                        .setMessage(getString(R.string.store_masterpasswd_confirmation))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes) { dialog, whichButton ->
                            storeMasterPassword(masterPasswd, this)
                            refreshMenuMasterPasswordItem(navigationView.menu)
                            masterPasswd.clear()
                            Toast.makeText(
                                this,
                                getString(R.string.masterpassword_stored),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .setNegativeButton(android.R.string.no, null)
                        .show()

                    return true
                } else {
                    return false
                }
            }
            R.id.delete_stored_masterpasswd -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_stored_masterpasswd))
                    .setMessage(getString(R.string.delete_stored_masterpasswd_confirmation))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes) { dialog, whichButton ->
                        RemoveStoredMasterPasswordUseCase.execute(this)
                        refreshMenuMasterPasswordItem(navigationView.menu)
                        Toast.makeText(
                            this,
                            getString(R.string.masterpassword_removed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()

                return true
            }
            R.id.generate_encrypted_masterpasswd -> {
                return GenerateMasterPasswordTokenUseCase.execute(this)
            }
            R.id.export_encrypted_masterpasswd -> {
                val encMasterPasswd = Session.getEncMasterPasswd()
                if (encMasterPasswd != null) {
                    ExportEncMasterPasswordUseCase.execute(encMasterPasswd, false, this)
                    return true
                } else {
                    return false
                }
            }
            R.id.export_masterkey -> {
                return ExportEncMasterKeyUseCase.execute(this)
            }
            R.id.export_vault -> {
                val intent = Intent(this, ExportVaultActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.change_pin -> {
                val intent = Intent(this, ChangePinActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.change_master_password -> {
                val intent = Intent(this, ChangeMasterPasswordActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.drop_vault -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_drop_vault))
                    .setMessage(getString(R.string.message_drop_vault))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes) { dialog, whichButton ->
                        DropVaultUseCase.execute(this)
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()

                return true
            }

            R.id.menu_help -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Constants.HOMEPAGE)
                startActivity(browserIntent)
                return true
            }

            R.id.menu_labels -> {
                val intent = Intent(this, ListLabelsActivity::class.java)
                startActivity(intent)
                return true
            }

            R.id.menu_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }

            R.id.menu_about -> {
                ShowInfoUseCase.execute(this)
                return true
            }
            R.id.menu_debug -> {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                val icon: Drawable = applicationInfo.loadIcon(packageManager)
                val message = DebugInfo.getDebugInfo(this)
                builder.setTitle(R.string.debug)
                    .setMessage(message)
                    .setIcon(icon)
                    .show()
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun refreshCredentials() {
        credentialViewModel.allCredentials.observe(this, { credentials ->
            credentials?.let { credentials ->
                var sortedCredentials = credentials

                masterSecretKey?.let { key ->
                    credentials.forEach { LabelService.updateLabelsForCredential(key, it) }

                    val sortedByRecent = PreferenceService.getAsBool(PREF_SORT_BY_RECENT, this)
                    if (sortedByRecent) {
                        sortedCredentials = credentials
                            .sortedBy { it.id }
                            .reversed()
                    } else {
                        sortedCredentials = credentials
                            .sortedBy {
                                SecretService.decryptCommonString(key, it.name).toLowerCase()
                            }
                    }
                }

                listCredentialAdapter?.submitOriginList(sortedCredentials)
                listCredentialAdapter?.filter?.filter("")

            }
        })
    }

    private fun refreshMenuMasterPasswordItem(menu: Menu) {
        val storedMasterPasswdPresent = PreferenceService.isPresent(
            DATA_ENCRYPTED_MASTER_PASSWORD,
            this
        )

        val storeMasterPasswdItem: MenuItem = menu.findItem(R.id.store_masterpasswd)
        if (storeMasterPasswdItem != null) {
            storeMasterPasswdItem.isVisible = !storedMasterPasswdPresent
        }
        val deleteMasterPasswdItem: MenuItem = menu.findItem(R.id.delete_stored_masterpasswd)
        if (deleteMasterPasswdItem != null) {
            deleteMasterPasswdItem.isVisible = storedMasterPasswdPresent
        }
    }

    private fun refreshMenuDebugItem(menu: Menu) {
        val debugItem: MenuItem = menu.findItem(R.id.menu_debug)
        if (debugItem != null) {
            debugItem.isVisible = DebugInfo.isDebug
        }
    }

    private fun refreshMenuFiltersItem(item: MenuItem) {
        val hasFilters = LabelFilter.hasFilters()
        item.isChecked = hasFilters
        if (hasFilters) {
            item.setIcon(R.drawable.ic_baseline_filter_list_with_with_dot_24dp)
        }
        else {
            item.setIcon(R.drawable.ic_baseline_filter_list_24_white)
        }
    }

    private fun refreshMenuSortedByItem(item: MenuItem) {
        val sortedByRecent = PreferenceService.getAsBool(PREF_SORT_BY_RECENT, this)
        item.isChecked = sortedByRecent
    }

    fun deleteCredential(credential: EncCredential) {
        credentialViewModel.delete(credential)
    }

}

