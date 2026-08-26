package com.nexusneuro.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusneuro.app.auth.BiometricGate
import com.nexusneuro.app.auth.UnlockChallenge
import com.nexusneuro.app.ui.DashboardScreen
import com.nexusneuro.app.ui.LoginScreen
import com.nexusneuro.app.ui.PersonnelHubScreen
import com.nexusneuro.app.ui.SessionViewModel
import com.nexusneuro.app.ui.theme.UnityXTheme
import com.nexusneuro.app.ui.theme.NexusPalette

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnityXTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NexusPalette.Black)
                        .safeDrawingPadding(),
                    color = NexusPalette.Black,
                ) {
                    UnityXApp()
                }
            }
        }
    }
}

@Composable
fun UnityXApp(vm: SessionViewModel = viewModel()) {
    val activity = LocalContext.current as FragmentActivity

    fun launchBiometric() {
        when (vm.unlockChallenge) {
            UnlockChallenge.DualBiometric -> {
                BiometricGate.authenticateDual(
                    activity = activity,
                    onSuccess = { vm.onBiometricSuccess() },
                    onError = { msg -> vm.onBiometricFailure(msg) },
                )
            }
            UnlockChallenge.SingleBiometric,
            UnlockChallenge.FullCredentials,
            -> {
                // FullCredentials path asks single bio after password (challenge flipped to Single).
                BiometricGate.authenticateSingle(
                    activity = activity,
                    onSuccess = { vm.onBiometricSuccess() },
                    onError = { msg -> vm.onBiometricFailure(msg) },
                )
            }
        }
    }

    LaunchedEffect(vm.biometricPromptRequestId) {
        if (vm.biometricPromptRequestId > 0 &&
            vm.biometricRequired &&
            vm.pendingUser != null &&
            vm.user == null
        ) {
            launchBiometric()
        }
    }

    if (vm.user == null) {
        LoginScreen(
            error = vm.loginError,
            challenge = vm.unlockChallenge,
            biometricPending = vm.biometricRequired && vm.pendingUser != null,
            savedDisplayName = vm.savedDisplayName,
            unlockCount = vm.unlockCount,
            onLogin = { id, pw -> vm.login(id, pw) },
            onRequestBiometric = { vm.requestBiometricAgain() },
            onSwitchAccount = { vm.switchAccount() },
        )
    } else if (vm.isAdminUser()) {
        DashboardScreen(vm)
    } else {
        PersonnelHubScreen(vm)
    }
}
