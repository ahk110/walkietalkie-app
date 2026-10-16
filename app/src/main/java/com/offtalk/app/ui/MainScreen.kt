package com.offtalk.app.ui

import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offtalk.app.model.PeerDevice
import com.offtalk.app.ui.theme.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    statusText: String,
    discoveredPeers: List<PeerDevice>,
    sosReceivedCoords: String?,
    onChannelChange: (String) -> Unit,
    onSearchClicked: (String) -> Unit,
    onConnectClicked: (PeerDevice) -> Unit,
    onPttPressStateChanged: (Boolean) -> Unit,
    onSosClicked: () -> Unit,
    onDismissSos: () -> Unit
) {
    var channelInput by remember { mutableStateOf("1234") }
    var isPttPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    sosReceivedCoords?.let { coords ->
        AlertDialog(
            onDismissRequest = { onDismissSos() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SosRed) },
            title = { Text("EMERGENCY SOS RECEIVED!") },
            text = { Text("Coordinates: $coords") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    onClick = {
                        val gmmIntentUri = Uri.parse("geo:$coords?q=$coords(SOS Location)")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        context.startActivity(mapIntent)
                    }
                ) { Text("Open in Maps") }
            },
            dismissButton = { TextButton(onClick = { onDismissSos() }) { Text("Silence Alarm") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OffTalk P2P", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when (statusText) {
                    "Connected" -> PttGreen
                    "Connecting..." -> Color(0xFFF59E0B)
                    "Searching..." -> PrimaryBlue
                    else -> TextSecondary
                }
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusColor))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Status: $statusText", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = channelInput,
                onValueChange = { channelInput = it; onChannelChange(it) },
                label = { Text("Channel Code") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSearchClicked(channelInput) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("Search") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Nearby Phones Running OffTalk:", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            items(discoveredPeers) { peer ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(peer.deviceName, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Channel: ${peer.channelCode}", color = TextSecondary, fontSize = 12.sp)
                        }
                        Button(onClick = { onConnectClicked(peer) }, colors = ButtonDefaults.buttonColors(containerColor = PttGreen)) { Text("Connect") }
                    }
                }
            }
        }

        Button(
            onClick = { onSosClicked() },
            colors = ButtonDefaults.buttonColors(containerColor = SosRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("SEND EMERGENCY SOS (GPS)", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(if (isPttPressed) PttPressed else PttGreen)
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { isPttPressed = true; onPttPressStateChanged(true); true }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isPttPressed = false; onPttPressStateChanged(false); true }
                        else -> false
                    }
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Mic, contentDescription = "PTT", tint = Color.White, modifier = Modifier.size(48.dp))
                Text(if (isPttPressed) "TALKING..." else "HOLD TO TALK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
