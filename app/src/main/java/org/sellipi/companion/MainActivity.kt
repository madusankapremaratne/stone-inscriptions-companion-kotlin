package org.sellipi.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.theme.SellipiTheme
import org.sellipi.companion.ui.navigation.SellipiNavHost

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val fineLocGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions for Camera and GPS
        checkAndRequestPermissions()

        setContent {
            var currentLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }
            var isSunlightMode by remember { mutableStateOf(false) }
            val navController = rememberNavController()

            SellipiTheme(isSunlightMode = isSunlightMode) {
                SellipiNavHost(
                    navController = navController,
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { currentLanguage = it },
                    isSunlightMode = isSunlightMode,
                    onToggleSunlightMode = { isSunlightMode = !isSunlightMode }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
