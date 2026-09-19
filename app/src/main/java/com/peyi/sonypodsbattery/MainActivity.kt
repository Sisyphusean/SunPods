package com.peyi.sonypodsbattery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var left: TextView
    private lateinit var right: TextView
    private lateinit var caseLevel: TextView
    private lateinit var updated: TextView
    private lateinit var refresh: Button
    private lateinit var permissionSettings: Button
    private lateinit var companionSetup: Button
    private lateinit var companionInstructions: TextView
    private lateinit var setupStatus: TextView
    private lateinit var setupContinue: Button
    private lateinit var ambientSlider: SeekBar
    private lateinit var ambientValue: TextView
    private lateinit var voiceFocus: CheckBox
    private var currentMode: ListeningMode? = null
    private var rendering = false
    private var pendingRefresh = false
    private var permissionRequestInFlight = false
    private var companionAssociationInFlight = false
    private var showingCompanionSetup = false
    private var showingWelcome = false
    private var initialSetupStage: InitialSetupStage? = null
    private val modeButtons = mutableMapOf<ListeningMode, Button>()

    private enum class InitialSetupStage {
        NEARBY_DEVICES,
        NOTIFICATIONS,
        PAIRING,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EarbudCompanionManager.startObservingPresence(this)
        if (requiresCompanionSetup() && shouldShowWelcome()) {
            showWelcomeScreen()
        } else if (requiresCompanionSetup()) {
            showInitialSetupFlow()
        } else {
            showDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            render(BatteryStore.load(this))
            updatePermissionUi()
            updateCompanionUi()
        } else if (showingCompanionSetup && !showingWelcome && initialSetupStage != InitialSetupStage.PAIRING) {
            showInitialSetupFlow()
        }
    }

    private fun buildContent(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun text(value: String, size: Float, color: Int = getColor(R.color.white)) = TextView(this).apply {
            this.text = value; textSize = size; setTextColor(color)
        }
        fun batteryCard(label: String, id: Int): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.card_background)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(text(label.uppercase(), 11f, getColor(R.color.muted)))
            addView(text("Not available", 26f).apply { this.id = id; setTypeface(typeface, Typeface.BOLD) })
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        body.addView(text("SONY PODS", 12f, getColor(R.color.accent)).apply { letterSpacing = .16f })
        body.addView(text("WF-1000XM5", 32f).apply { setTypeface(typeface, Typeface.BOLD) })
        status = text("Ready", 15f, getColor(R.color.muted)).also {
            body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(22)) })
        }

        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val leftCard = batteryCard("Left", ID_LEFT)
        left = leftCard.findViewById(ID_LEFT)
        val rightCard = batteryCard("Right", ID_RIGHT)
        right = rightCard.findViewById(ID_RIGHT)
        cards.addView(leftCard, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        cards.addView(rightCard, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        body.addView(cards)
        val caseCard = batteryCard("Case", ID_CASE)
        caseLevel = caseCard.findViewById(ID_CASE)
        body.addView(caseCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, dp(20)) })

        body.addView(text("LISTENING MODE", 12f, getColor(R.color.muted)))
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 3f }
        fun modeButton(mode: ListeningMode, title: String): Button = Button(this).apply {
            text = title; textSize = 11f; isAllCaps = false
            setTextColor(getColor(R.color.white)); setBackgroundColor(getColor(R.color.surface_2))
            setOnClickListener { changeListeningMode(mode) }
            modeButtons[mode] = this
        }
        modes.addView(modeButton(ListeningMode.NOISE_CANCELLING, "Noise cancel"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(3) })
        modes.addView(modeButton(ListeningMode.AMBIENT, "Ambient"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        modes.addView(modeButton(ListeningMode.OFF, "Off"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(3) })
        body.addView(modes, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(12)) })

        val ambientControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.card_background)
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        ambientValue = text("Transparency level 20", 14f).also { ambientControls.addView(it) }
        ambientSlider = SeekBar(this).apply {
            max = 19; progress = 19
            contentDescription = "Transparency level"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    ambientValue.text = "Transparency level ${progress + 1}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    if (currentMode == ListeningMode.AMBIENT) changeListeningMode(ListeningMode.AMBIENT)
                }
            })
        }
        ambientControls.addView(ambientSlider, LinearLayout.LayoutParams(-1, dp(42)))
        voiceFocus = CheckBox(this).apply {
            text = "Focus on voice"; textSize = 14f; setTextColor(getColor(R.color.white))
            setOnCheckedChangeListener { _, _ ->
                if (!rendering && currentMode == ListeningMode.AMBIENT) changeListeningMode(ListeningMode.AMBIENT)
            }
        }
        ambientControls.addView(voiceFocus)
        body.addView(ambientControls, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(20)) })

        refresh = Button(this).apply {
            text = "REFRESH BATTERY"; textSize = 14f; setTextColor(Color.BLACK)
            background = getDrawable(R.drawable.button_background); isAllCaps = false
        }
        body.addView(refresh, LinearLayout.LayoutParams(-1, dp(56)))
        permissionSettings = Button(this).apply {
            text = "OPEN BLUETOOTH PERMISSION SETTINGS"; textSize = 13f
            setTextColor(getColor(R.color.white)); setBackgroundColor(getColor(R.color.surface_2)); isAllCaps = false
        }
        body.addView(permissionSettings, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(10), 0, 0) })
        companionSetup = Button(this).apply {
            text = "SET UP AUTOMATIC BACKGROUND REFRESH"; textSize = 13f
            setTextColor(getColor(R.color.white)); setBackgroundColor(getColor(R.color.surface_2)); isAllCaps = false
        }
        body.addView(companionSetup, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(10), 0, 0) })
        companionInstructions = text("", 13f, getColor(R.color.muted)).apply {
            setLineSpacing(0f, 1.2f)
            visibility = View.GONE
        }
        body.addView(companionInstructions, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, 0)
        })
        updated = text("Never updated", 12f, getColor(R.color.muted)).also {
            it.gravity = Gravity.CENTER
            body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, dp(26)) })
        }
        body.addView(text("Designed to coexist", 18f).apply { setTypeface(typeface, Typeface.BOLD) })
        body.addView(text("The Sony control connection is opened only during a refresh and closed immediately afterward. If Sound Connect is already open, close it and retry.", 14f, getColor(R.color.muted)).apply {
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(16)) })
        body.addView(Button(this).apply {
            id = ID_DIAGNOSTICS; text = "Share diagnostics"; isAllCaps = false
            setTextColor(getColor(R.color.white)); setBackgroundColor(getColor(R.color.surface_2))
        }, LinearLayout.LayoutParams(-1, dp(48)))
        return ScrollView(this).apply { setBackgroundColor(getColor(R.color.black)); addView(body) }
    }

    private fun shouldShowWelcome() =
        !getPreferences(MODE_PRIVATE).getBoolean(WELCOME_SEEN, false)

    private fun showWelcomeScreen() {
        showingCompanionSetup = true
        showingWelcome = true
        initialSetupStage = null
        setContent {
            OnboardingWelcomeScreen(
                onContinue = {
                    getPreferences(MODE_PRIVATE).edit()
                        .putBoolean(WELCOME_SEEN, true)
                        .apply()
                    showingWelcome = false
                    showInitialSetupFlow()
                }
            )
        }
    }

    private fun showCompanionSetupScreen() {
        showingCompanionSetup = true
        initialSetupStage = InitialSetupStage.PAIRING
        setContentView(buildCompanionSetupContent())
    }

    private fun showInitialSetupFlow() {
        showingWelcome = false
        when {
            !hasBluetoothPermission() -> showPermissionSetupScreen(InitialSetupStage.NEARBY_DEVICES)
            !hasNotificationPermission() -> showPermissionSetupScreen(InitialSetupStage.NOTIFICATIONS)
            else -> showCompanionSetupScreen()
        }
    }

    private fun showPermissionSetupScreen(stage: InitialSetupStage) {
        showingCompanionSetup = true
        initialSetupStage = stage
        val isNearby = stage == InitialSetupStage.NEARBY_DEVICES
        val permissionName = if (isNearby) "Nearby devices" else "Notifications"
        val title = if (isNearby) "Allow Nearby devices" else "Allow notifications"
        val explanation = if (isNearby) {
            "Sony Pods needs Nearby devices permission to communicate with your WF-1000XM5 and to let Android find it during setup."
        } else {
            "Sony Pods uses notifications to keep automatic background refresh reliable and to show its status when your earbuds connect."
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun text(value: String, size: Float, color: Int = getColor(R.color.white)) = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(getColor(R.color.black))
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text("SONY PODS", 12f, getColor(R.color.accent)).apply { letterSpacing = .16f })
        body.addView(text(title, 28f).apply { setTypeface(typeface, Typeface.BOLD) },
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, dp(22)) })
        body.addView(text(explanation, 16f, getColor(R.color.muted)).apply { setLineSpacing(0f, 1.25f) },
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(18)) })
        setupStatus = text("", 14f, getColor(R.color.muted)).apply {
            setLineSpacing(0f, 1.2f)
            visibility = View.GONE
        }
        body.addView(setupStatus, LinearLayout.LayoutParams(-1, -2))
        page.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setupContinue = Button(this).apply {
            text = "ALLOW ${permissionName.uppercase()}"
            textSize = 14f
            setTextColor(Color.BLACK)
            background = getDrawable(R.drawable.button_background)
            isAllCaps = false
            setOnClickListener { requestInitialSetupPermission(stage) }
        }
        page.addView(setupContinue, LinearLayout.LayoutParams(-1, dp(56)).apply {
            setMargins(0, dp(16), 0, 0)
        })
        setContentView(page)
    }

    private fun requestInitialSetupPermission(stage: InitialSetupStage) {
        val alreadyGranted = when (stage) {
            InitialSetupStage.NEARBY_DEVICES -> hasBluetoothPermission()
            InitialSetupStage.NOTIFICATIONS -> hasNotificationPermission()
            InitialSetupStage.PAIRING -> false
        }
        if (alreadyGranted) {
            showInitialSetupFlow()
            return
        }
        permissionRequestInFlight = true
        val permission = if (stage == InitialSetupStage.NEARBY_DEVICES) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.POST_NOTIFICATIONS
        }
        val requestCode = if (stage == InitialSetupStage.NEARBY_DEVICES) {
            REQUEST_BLUETOOTH
        } else {
            REQUEST_NOTIFICATIONS
        }
        setupStatus.visibility = View.GONE
        requestPermissions(arrayOf(permission), requestCode)
    }

    private fun showInitialPermissionDenied(permissionName: String) {
        setupStatus.text = "$permissionName permission is required to continue setup. Enable it in App info, then return here."
        setupStatus.visibility = View.VISIBLE
        setupContinue.text = "OPEN APP SETTINGS"
        setupContinue.isEnabled = true
        setupContinue.setOnClickListener { openPermissionSettings() }
    }

    private fun buildCompanionSetupContent(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun text(value: String, size: Float, color: Int = getColor(R.color.white)) = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(getColor(R.color.black))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(text("SONY PODS", 12f, getColor(R.color.accent)).apply { letterSpacing = .16f })
        body.addView(text("Set up your WF-1000XM5", 28f).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, dp(22)) })
        body.addView(text("Before we look for your earbuds", 18f).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(text(
            "Android can only find the earbuds while they are in pairing mode.\n\n" +
                "1. Put both earbuds in the open charging case.\n" +
                "2. Hold the pairing button on the back for about 5 seconds.\n" +
                "3. Wait until the blue indicator starts flashing.\n\n" +
                "When the blue light is flashing, tap the button below.",
            16f,
            getColor(R.color.muted)
        ).apply { setLineSpacing(0f, 1.25f) }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(10), 0, dp(18))
        })
        setupStatus = text("", 14f, getColor(R.color.muted)).apply {
            setLineSpacing(0f, 1.2f)
            visibility = View.GONE
        }
        body.addView(setupStatus, LinearLayout.LayoutParams(-1, -2))

        page.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setupContinue = Button(this).apply {
            text = "DEVICE IN PAIRING MODE"
            textSize = 14f
            setTextColor(Color.BLACK)
            background = getDrawable(R.drawable.button_background)
            isAllCaps = false
            setOnClickListener { setupCompanionAssociation() }
        }
        page.addView(setupContinue, LinearLayout.LayoutParams(-1, dp(56)).apply {
            setMargins(0, dp(16), 0, 0)
        })
        return page
    }

    private fun showDashboard() {
        showingWelcome = false
        showingCompanionSetup = false
        setContentView(buildContent())
        render(BatteryStore.load(this))
        refresh.setOnClickListener { requestRefresh() }
        permissionSettings.setOnClickListener { openPermissionSettings() }
        companionSetup.setOnClickListener { setupCompanionAssociation() }
        findViewById<Button>(ID_DIAGNOSTICS).setOnClickListener { shareDiagnostics() }
        updatePermissionUi()
        updateCompanionUi()
        requestRefresh()
    }

    private fun requestRefresh() {
        pendingRefresh = true
        continuePermissionFlow()
    }

    private fun continuePermissionFlow() {
        if (permissionRequestInFlight) return
        if (!hasBluetoothPermission()) {
            permissionRequestInFlight = true
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH)
            status.text = "Allow Nearby devices. If no prompt appears, use the settings button."
            return
        }
        if (shouldRequestNotifications()) {
            permissionRequestInFlight = true
            getPreferences(MODE_PRIVATE).edit().putBoolean(NOTIFICATION_REQUESTED, true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            status.text = "Allow notifications so background refresh can show its status."
            return
        }
        if (!pendingRefresh) return
        pendingRefresh = false
        refresh.isEnabled = false
        refresh.text = "Reading..."
        status.text = "Connecting briefly to your earbuds"
        Thread {
            val state = RefreshCoordinator.refresh(applicationContext)
            runOnUiThread { render(state); refresh.isEnabled = true; refresh.text = "REFRESH BATTERY" }
        }.start()
    }

    private fun render(state: BatteryState) {
        fun format(level: Int?, charging: Boolean) = level?.let { "$it%${if (charging) "  charging" else ""}" } ?: "Not available"
        left.text = format(state.left, state.leftCharging)
        right.text = format(state.right, state.rightCharging)
        caseLevel.text = format(state.case, state.caseCharging)
        status.text = state.status
        modeButtons.forEach { (mode, button) ->
            val active = state.listeningMode == mode
            button.setBackgroundColor(getColor(if (active) R.color.accent else R.color.surface_2))
            button.setTextColor(if (active) Color.BLACK else getColor(R.color.white))
        }
        rendering = true
        currentMode = state.listeningMode
        val isAmbient = state.listeningMode == ListeningMode.AMBIENT
        ambientSlider.progress = state.ambientLevel - 1
        voiceFocus.isChecked = state.focusOnVoice
        ambientSlider.isEnabled = isAmbient
        voiceFocus.isEnabled = isAmbient
        ambientValue.alpha = if (isAmbient) 1f else .45f
        voiceFocus.alpha = if (isAmbient) 1f else .45f
        rendering = false
        updated.text = if (state.updatedAt == 0L) "Never updated" else
            "Last updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.updatedAt))}"
    }

    private fun changeListeningMode(mode: ListeningMode) {
        if (!hasBluetoothPermission()) {
            permissionRequestInFlight = true
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH)
            return
        }
        modeButtons.values.forEach { it.isEnabled = false }
        status.text = "Switching to ${mode.label.lowercase()}"
        Thread {
            val state = RefreshCoordinator.setListeningMode(applicationContext, mode, ambientSlider.progress + 1, voiceFocus.isChecked)
            runOnUiThread {
                render(state)
                modeButtons.values.forEach { it.isEnabled = true }
            }
        }.start()
    }

    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun shouldRequestNotifications() =
        Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_REQUESTED, false)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionRequestInFlight = false
        if (requestCode == REQUEST_BLUETOOTH) {
            if (showingCompanionSetup) {
                if (hasBluetoothPermission()) {
                    showInitialSetupFlow()
                } else {
                    showInitialPermissionDenied("Nearby devices")
                }
                return
            }
            updatePermissionUi()
            updateCompanionUi()
            if (hasBluetoothPermission()) {
                if (::status.isInitialized) {
                    status.text = "Bluetooth access granted"
                    continuePermissionFlow()
                }
            } else {
                status.text = "Nearby devices is blocked. Open Bluetooth permission settings below."
            }
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            if (showingCompanionSetup) {
                if (hasNotificationPermission()) {
                    showInitialSetupFlow()
                } else {
                    showInitialPermissionDenied("Notifications")
                }
            } else {
                continuePermissionFlow()
            }
        }
    }

    private fun updatePermissionUi() {
        if (!::permissionSettings.isInitialized) return
        permissionSettings.visibility = if (hasBluetoothPermission()) View.GONE else View.VISIBLE
    }

    private fun updateCompanionUi() {
        if (!::companionSetup.isInitialized || !::companionInstructions.isInitialized) return
        val supported = EarbudCompanionManager.isSupported(this)
        val visible = supported && hasBluetoothPermission()
        val associated = EarbudCompanionManager.isAssociated(this)
        companionSetup.visibility = if (visible) View.VISIBLE else View.GONE
        companionInstructions.visibility = if (visible && !associated) View.VISIBLE else View.GONE
        companionInstructions.text = if (companionAssociationInFlight) {
            "The Android device picker is open. Select WF-1000XM5 and tap Connect."
        } else {
            "Before continuing: place both earbuds in the open charging case, then hold the pairing button on the back for about 5 seconds until the blue light flashes blue. Only then continue so Android can find them."
        }
        if (associated) {
            companionSetup.text = "AUTOMATIC BACKGROUND REFRESH ENABLED"
            companionSetup.isEnabled = false
        } else if (companionAssociationInFlight) {
            companionSetup.text = "FOLLOW THE ANDROID SETUP DIALOG"
            companionSetup.isEnabled = false
        } else {
            companionSetup.text = "I'M READY — OPEN DEVICE PICKER"
            companionSetup.isEnabled = true
        }
    }

    private fun requiresCompanionSetup() =
        EarbudCompanionManager.isSupported(this) &&
            !EarbudCompanionManager.isAssociated(this)

    private fun setupCompanionAssociation() {
        if (!hasBluetoothPermission()) {
            if (showingCompanionSetup) {
                showInitialSetupFlow()
            } else {
                status.text = "Allow Nearby devices first."
                requestRefresh()
            }
            return
        }
        if (companionAssociationInFlight) return
        companionAssociationInFlight = true
        if (showingCompanionSetup) {
            setupContinue.isEnabled = false
            setupStatus.text = "Looking for a device in Android…"
            setupStatus.visibility = View.VISIBLE
        } else {
            status.text = "Follow the one-time Android setup to enable automatic refresh."
            updateCompanionUi()
        }
        EarbudCompanionManager.associate(
            this,
            onStatus = { message ->
                runOnUiThread {
                    if (showingCompanionSetup) {
                        setupStatus.text = message
                        setupStatus.visibility = View.VISIBLE
                    } else {
                        status.text = message
                    }
                }
            },
            onFinished = { success ->
                runOnUiThread {
                    companionAssociationInFlight = false
                    if (showingCompanionSetup) {
                        if (!success) {
                            setupStatus.text = "Android could not open the device picker. Put the earbuds in pairing mode and try again."
                            setupStatus.visibility = View.VISIBLE
                        }
                        setupContinue.isEnabled = true
                    } else {
                        if (!success) status.text = "Automatic refresh setup could not be completed."
                        updateCompanionUi()
                    }
                }
            }
        )
    }

    @Deprecated("Deprecated in Android 16; retained for Android 12-15 companion picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EarbudCompanionManager.REQUEST_ASSOCIATION) return
        companionAssociationInFlight = false
        if (EarbudCompanionManager.handleAssociationResult(this, resultCode, data)) {
            if (showingCompanionSetup) {
                setupStatus.text = "Automatic background refresh enabled."
                setupStatus.visibility = View.VISIBLE
                requestNotificationsThenShowDashboard()
            } else {
                status.text = "Automatic background refresh enabled"
                updateCompanionUi()
                requestRefresh()
            }
        } else {
            if (showingCompanionSetup) {
                setupStatus.text = "Setup cancelled. Put the earbuds in pairing mode and try again."
                setupStatus.visibility = View.VISIBLE
                setupContinue.isEnabled = true
            } else {
                status.text = "Automatic background refresh setup was cancelled"
                updateCompanionUi()
            }
        }
    }

    private fun requestNotificationsThenShowDashboard() {
        showDashboard()
    }

    private fun openPermissionSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun shareDiagnostics() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Sony Pods Battery diagnostics")
            putExtra(Intent.EXTRA_TEXT, DiagnosticsStore.text(this@MainActivity))
        }, "Share diagnostics"))
    }

    companion object {
        private const val ID_LEFT = 1001
        private const val ID_RIGHT = 1002
        private const val ID_CASE = 1003
        private const val ID_DIAGNOSTICS = 1004
        private const val REQUEST_BLUETOOTH = 100
        private const val REQUEST_NOTIFICATIONS = 101
        private const val NOTIFICATION_REQUESTED = "notification_permission_requested"
        private const val WELCOME_SEEN = "onboarding_welcome_seen"
    }
}
