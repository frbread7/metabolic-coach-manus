package com.young.metaboliccoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * In-app Health Connect permission rationale and privacy disclosure.
 *
 * Android launches this activity from both the pre-Android 14 rationale action and the
 * Android 14+ Health Connect permission-usage entry declared in the manifest.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(),
            ) {
                Surface {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Health data and privacy",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Metabolic Coach reads only the Health Connect data you grant: " +
                                "blood glucose, steps, floors climbed, heart rate, exercise " +
                                "sessions, and active calories.",
                        )
                        Text(
                            "This data is used to show your current wellness context, create " +
                                "configurable activity reminders, synchronize that context to " +
                                "your watch, and calculate private on-device observations.",
                        )
                        Text(
                            "Health data is stored locally on your phone. It is not sold, used " +
                                "for advertising, or uploaded to a cloud service by this version. " +
                                "Watch synchronization uses the paired Wear OS Data Layer.",
                        )
                        Text(
                            "You can revoke individual Health Connect permissions at any time. " +
                                "Metabolic Coach continues with the remaining data and clearly " +
                                "reports unavailable features.",
                        )
                        Text(
                            "This is a wellness tool, not a medical device. Keep your CGM " +
                                "manufacturer's application and clinical alerts enabled, and do " +
                                "not use Metabolic Coach for treatment decisions.",
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = ::finish,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
