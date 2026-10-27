package app.lteguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B1220)
private val CardBg = Color(0xFF111B2E)
private val Cyan = Color(0xFF22D3EE)
private val Green = Color(0xFF34D399)
private val Amber = Color(0xFFFBBF24)
private val Muted = Color(0xFF94A3B8)
private val TextC = Color(0xFFE5E7EB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    onPrimary = Color(0xFF00252B),
                    background = Bg,
                    surface = CardBg,
                    onSurface = TextC,
                    onBackground = TextC,
                    secondaryContainer = Color(0xFF0E4A57),
                    onSecondaryContainer = TextC
                )
            ) {
                Screen()
            }
        }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun StepRow(title: String, detail: String, ok: Boolean, action: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (ok) "OK" else "TODO", color = if (ok) Green else Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextC, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        if (!ok && action != null) {
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (Prefs.enabled(ctx) && !Live.state.value.running) {
            try {
                ctx.startForegroundService(Intent(ctx, GuardService::class.java))
            } catch (_: Exception) {
            }
        }
        while (true) {
            delay(1500)
            tick++
        }
    }

    val live by Live.state.collectAsState()
    val log by AppLog.lines.collectAsState()
    var enabled by remember { mutableStateOf(Prefs.enabled(ctx)) }
    var useLock by remember { mutableStateOf(Prefs.useBandLock(ctx)) }
    var useCycle by remember { mutableStateOf(Prefs.useDataCycle(ctx)) }
    var bands by remember { mutableStateOf(Prefs.bands(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val phonePerm = remember(tick) {
        ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    val notifPerm = remember(tick) {
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val shizuku = remember(tick) { ShizukuBridge.state(ctx) }
    val battery = remember(tick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun run(label: String, block: suspend () -> Boolean) {
        if (busy) return
        busy = true
        note = "$label ..."
        scope.launch {
            val ok = try {
                block()
            } catch (e: Exception) {
                AppLog.log("$label error: $e")
                false
            }
            note = label + if (ok) ": done" else ": failed (see log)"
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("LTE Guard", color = TextC, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Keeps your mobile data on plain 4G, no 4.5G.", color = Muted, fontSize = 13.sp)

        CardBox {
            val title = when {
                !enabled -> "Guard is off"
                live.plain == true -> "Plain 4G"
                live.plain == false -> "Not plain 4G"
                else -> "Waiting for mobile data"
            }
            val color = when {
                !enabled -> Muted
                live.plain == true -> Green
                live.plain == false -> Amber
                else -> Muted
            }
            Text(title, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Network now: " + live.net, color = TextC)
            if (live.radio.isNotEmpty()) Text(live.radio, color = Muted, fontSize = 12.sp)
            Text(
                "Cellular internet: " + when (live.internet) {
                    true -> "OK"
                    false -> "none"
                    null -> "unknown"
                } + if (live.lastAction.isNotEmpty()) "   Last fix: " + live.lastAction else "",
                color = Muted, fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = enabled, onCheckedChange = { on ->
                    enabled = on
                    Prefs.setEnabled(ctx, on)
                    if (on) {
                        try {
                            ctx.startForegroundService(Intent(ctx, GuardService::class.java))
                        } catch (e: Exception) {
                            AppLog.log("Cannot start service: $e")
                        }
                    } else {
                        ctx.stopService(Intent(ctx, GuardService::class.java))
                    }
                })
                Text("Guard active in the background", color = TextC)
            }
        }

        CardBox {
            Text("Setup", color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            StepRow(
                "Phone state + notifications",
                "Lets the app see the 4G / 4.5G state instantly.",
                phonePerm && notifPerm, "Grant"
            ) {
                permLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS))
            }
            StepRow(
                "Shizuku",
                when (shizuku) {
                    ShizukuState.READY -> "Running and allowed"
                    ShizukuState.NO_PERMISSION -> "Running - allow LTE Guard"
                    ShizukuState.NOT_RUNNING -> "Installed but not running - start it"
                    ShizukuState.NOT_INSTALLED -> "Not installed"
                },
                shizuku == ShizukuState.READY,
                if (shizuku == ShizukuState.NO_PERMISSION) "Allow" else "Open Shizuku"
            ) {
                if (shizuku == ShizukuState.NO_PERMISSION) {
                    ShizukuBridge.requestPermission()
                } else {
                    val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (i != null) ctx.startActivity(i) else note = "Install Shizuku first"
                }
            }
            StepRow(
                "Battery: unrestricted",
                "Stops HyperOS from killing the guard.",
                battery, "Allow"
            ) {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                )
            }
            Text(
                "HyperOS extras (cannot be set by apps): Settings > Apps > LTE Guard > turn Autostart on and set " +
                    "Battery saver to No restrictions; lock the app card in Recents.",
                color = Muted, fontSize = 12.sp
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                )
            }) { Text("Open app settings") }
        }

        CardBox {
            Text("Fix strategy", color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = useLock, onCheckedChange = {
                    useLock = it
                    Prefs.setUseBandLock(ctx, it)
                })
                Column {
                    Text("Band lock (the real fix)", color = TextC)
                    Text("Locks LTE to the bands below; 4.5G only appeared on B40.", color = Muted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in listOf(1, 3, 7, 8, 20)) {
                    FilterChip(
                        selected = b in bands,
                        onClick = {
                            bands = if (b in bands) bands - b else bands + b
                            Prefs.setBands(ctx, bands)
                        },
                        label = { Text("B$b") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = useCycle, onCheckedChange = {
                    useCycle = it
                    Prefs.setUseDataCycle(ctx, it)
                })
                Column {
                    Text("Data cycle fallback", color = TextC)
                    Text("Turns mobile data off/on (max 1 per 3 min, 10 per hour).", color = Muted, fontSize = 12.sp)
                }
            }
        }

        CardBox {
            Text("Manual actions", color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Keep the screen on and unlocked for band actions.", color = Muted, fontSize = 12.sp)
            Button(enabled = !busy && shizuku == ShizukuState.READY, onClick = {
                run("Band lock") {
                    val ok = Actions.applyBandLock(Prefs.bands(ctx))
                    if (ok) Prefs.setLockVerifiedAt(ctx, System.currentTimeMillis())
                    ok
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Apply band lock now") }
            OutlinedButton(enabled = !busy && shizuku == ShizukuState.READY, onClick = {
                run("Restore bands") {
                    val ok = Actions.restoreBands()
                    if (ok) Prefs.setLockVerifiedAt(ctx, 0L)
                    ok
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Restore automatic bands") }
            OutlinedButton(enabled = !busy && shizuku == ShizukuState.READY, onClick = {
                run("Data cycle") { Actions.cycleData() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Cycle mobile data now") }
            OutlinedButton(enabled = !busy && shizuku == ShizukuState.READY, onClick = {
                run("Radio check") {
                    val r = Actions.readRadio()
                    if (r != null) {
                        Live.update { it.copy(radio = r.label()) }
                        AppLog.log("Radio: " + r.label())
                    }
                    r != null
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Check radio / band now") }
            if (note.isNotEmpty()) Text(note, color = Cyan, fontSize = 13.sp)
        }

        CardBox {
            Text("Log", color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (log.isEmpty()) "(empty)" else log.takeLast(40).joinToString("\n"),
                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { note = AppLog.export(ctx) }) { Text("Export to Downloads") }
                OutlinedButton(onClick = { AppLog.clear() }) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
