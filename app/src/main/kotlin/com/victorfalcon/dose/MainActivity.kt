package com.victorfalcon.dose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.victorfalcon.dose.ui.theme.DoseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoseTheme {
                // TODO(nav): replace with the Navigation3 NavDisplay (Today / History)
                // + bottom bar, Settings in the top bar, FAB to add a medication.
                Scaffold { inner ->
                    Text("Dose", modifier = Modifier.padding(inner))
                }
            }
        }
    }
}
