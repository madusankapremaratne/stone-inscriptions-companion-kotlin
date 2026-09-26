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

    // Observed by Home so location is fetched once the user grants it, not only at startup.
    private var locationPermissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locationPermissionGranted = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionGranted = hasLocationPermission()

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
                    onToggleSunlightMode = { isSunlightMode = !isSunlightMode },
                    locationPermissionGranted = locationPermissionGranted
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Android 12+ ignores a FINE request unless COARSE is requested with it.
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
