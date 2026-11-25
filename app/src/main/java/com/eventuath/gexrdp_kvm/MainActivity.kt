package com.eventuath.gexrdp_kvm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eventuath.gexrdp_kvm.ui.GexRoot
import com.eventuath.gexrdp_kvm.ui.theme.GexRDPKVMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GexRDPKVMTheme {
                GexRoot()
            }
        }
    }
}
