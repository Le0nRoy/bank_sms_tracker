package com.example.banksmstracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.banksmstracker.R
import com.example.banksmstracker.service.SmsProcessingService

class OnboardingActivity : BaseActivity() {

    companion object {
        const val PREFS_ONBOARDING = "onboarding"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        private const val STEP_WELCOME = 0
        private const val STEP_SMS_PERMISSION = 1
        private const val STEP_NOTIFICATIONS = 2
        private const val STEP_BACKGROUND_SERVICE = 3
        private const val TOTAL_STEPS = 4
    }

    private var currentStep = STEP_WELCOME

    private lateinit var stepContainer: FrameLayout
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button

    // Permission launchers
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        refreshSmsPermissionStep(allGranted)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshNotificationStep(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        stepContainer = findViewById(R.id.onboardingStepContainer)
        dotsContainer = findViewById(R.id.onboardingDots)
        btnNext = findViewById(R.id.btnOnboardingNext)
        btnSkip = findViewById(R.id.btnOnboardingSkip)

        buildDots()
        showStep(STEP_WELCOME)

        btnNext.setOnClickListener { advanceStep() }
        btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun buildDots() {
        dotsContainer.removeAllViews()
        val dotSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4
        val dotMargin = dotSize / 2
        for (i in 0 until TOTAL_STEPS) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                leftMargin = dotMargin
                rightMargin = dotMargin
            }
            dot.layoutParams = params
            val dotRes = if (i == currentStep) {
                R.drawable.onboarding_dot_active
            } else {
                R.drawable.onboarding_dot_inactive
            }
            dot.setBackgroundResource(dotRes)
            dotsContainer.addView(dot)
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        buildDots()

        // Update Next button label on last step
        if (step == TOTAL_STEPS - 1) {
            btnNext.text = getString(R.string.onboarding_get_started)
        } else {
            btnNext.text = getString(R.string.onboarding_next)
        }

        // Skip button: hide on last step
        btnSkip.visibility = if (step == TOTAL_STEPS - 1) View.GONE else View.VISIBLE

        stepContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        when (step) {
            STEP_WELCOME -> inflater.inflate(R.layout.onboarding_step_welcome, stepContainer, true)
            STEP_SMS_PERMISSION -> {
                val view = inflater.inflate(R.layout.onboarding_step_sms_permission, stepContainer, true)
                setupSmsStep(view)
            }
            STEP_NOTIFICATIONS -> {
                val view = inflater.inflate(R.layout.onboarding_step_notifications, stepContainer, true)
                setupNotificationsStep(view)
            }
            STEP_BACKGROUND_SERVICE -> {
                val view = inflater.inflate(R.layout.onboarding_step_background_service, stepContainer, true)
                setupServiceStep(view)
            }
        }
    }

    private fun setupSmsStep(view: View) {
        val tvStatus = view.findViewById<TextView>(R.id.tvSmsPermissionStatus)
        val btnGrant = view.findViewById<Button>(R.id.btnGrantSmsPermission)

        if (hasSmsPermissions()) {
            tvStatus.text = getString(R.string.onboarding_permission_granted)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnGrant.isEnabled = false
        } else {
            tvStatus.text = ""
            btnGrant.isEnabled = true
        }

        btnGrant.setOnClickListener {
            smsPermissionLauncher.launch(
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            )
        }
    }

    private fun refreshSmsPermissionStep(granted: Boolean) {
        val tvStatus = stepContainer.findViewById<TextView>(R.id.tvSmsPermissionStatus) ?: return
        val btnGrant = stepContainer.findViewById<Button>(R.id.btnGrantSmsPermission) ?: return
        if (granted) {
            tvStatus.text = getString(R.string.onboarding_permission_granted)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnGrant.isEnabled = false
        } else {
            tvStatus.text = getString(R.string.onboarding_permission_denied)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun setupNotificationsStep(view: View) {
        val tvStatus = view.findViewById<TextView>(R.id.tvNotificationPermissionStatus)
        val btnGrant = view.findViewById<Button>(R.id.btnGrantNotificationPermission)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            tvStatus.text = getString(R.string.onboarding_notification_unavailable)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnGrant.isEnabled = false
            return
        }

        if (hasNotificationPermission()) {
            tvStatus.text = getString(R.string.onboarding_permission_granted)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnGrant.isEnabled = false
        } else {
            tvStatus.text = ""
            btnGrant.isEnabled = true
        }

        btnGrant.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshNotificationStep(granted: Boolean) {
        val tvStatus = stepContainer.findViewById<TextView>(R.id.tvNotificationPermissionStatus) ?: return
        val btnGrant = stepContainer.findViewById<Button>(R.id.btnGrantNotificationPermission) ?: return
        if (granted) {
            tvStatus.text = getString(R.string.onboarding_permission_granted)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnGrant.isEnabled = false
        } else {
            tvStatus.text = getString(R.string.onboarding_permission_denied)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun setupServiceStep(view: View) {
        val tvStatus = view.findViewById<TextView>(R.id.tvServiceStatus)
        val btnStart = view.findViewById<Button>(R.id.btnStartService)

        btnStart.setOnClickListener {
            SmsProcessingService.start(this)
            tvStatus.text = getString(R.string.onboarding_service_started)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnStart.isEnabled = false
        }
    }

    private fun advanceStep() {
        if (currentStep < TOTAL_STEPS - 1) {
            showStep(currentStep + 1)
        } else {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        finish()
    }

    private fun hasSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
