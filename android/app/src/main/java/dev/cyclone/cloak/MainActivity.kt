package dev.cyclone.cloak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.cyclone.cloak.ui.CloakApp
import dev.cyclone.cloak.ui.CloakTheme
import dev.cyclone.cloak.ui.CloakViewModel

/** Hosts the app. Everything it does lives in CloakViewModel; the screens are in the ui package. */
class MainActivity : ComponentActivity() {
    private val vm: CloakViewModel by viewModels()
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CloakTheme { CloakApp(vm) } }
    }

    override fun onResume() {
        super.onResume()
        // The owner may have just approved Cloak in Cyclone, or switched profiles: look again.
        if (resumedOnce) vm.refresh()
        resumedOnce = true
    }
}
