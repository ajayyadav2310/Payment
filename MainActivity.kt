package com.paymentannouncer.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val Navy = Color(0xFF101827)
private val Panel = Color(0xFF1B293B)
private val Mint = Color(0xFF52E0B5)
private val Muted = Color(0xFF9AA9BA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaymentApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentApp() {
    val context = LocalContext.current
    val dao = remember { PaymentDatabase.get(context).dao() }
    val payments by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val received by dao.received().collectAsStateWithLifecycle(initialValue = 0.0)
    val sent by dao.sent().collectAsStateWithLifecycle(initialValue = 0.0)
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var page by remember { mutableStateOf("Home") }
    var voice by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var incoming by remember { mutableStateOf(prefs.getBoolean("incoming", true)) }
    var outgoing by remember { mutableStateOf(prefs.getBoolean("outgoing", true)) }
    var language by remember { mutableStateOf(prefs.getString("voice_language", "hi") ?: "hi") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = buildString {
                append("Date,Direction,Amount,App,Details\n")
                payments.forEach { p -> append("\"${Date(p.createdAt)}\",${p.direction},${p.amount},\"${p.appName.replace("\"","") }\",\"${p.detail.replace("\"","")}\"\n") }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val array = JSONArray()
            payments.forEach { p -> array.put(JSONObject().put("amount",p.amount).put("direction",p.direction).put("appName",p.appName).put("detail",p.detail).put("createdAt",p.createdAt).put("fingerprint",p.fingerprint)) }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(array.toString(2)) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "[]"
                val arr = JSONArray(content)
                val list = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val amount = o.optDouble("amount", 0.0)
                    val dir = o.optString("direction")
                    if (amount <= 0 || dir !in listOf("RECEIVED","SENT")) null else Payment(
                        amount=amount, direction=dir, appName=o.optString("appName","Imported"),
                        detail=o.optString("detail","Restored backup"), createdAt=o.optLong("createdAt",System.currentTimeMillis()),
                        fingerprint=o.optString("fingerprint","restore-${UUID.randomUUID()}")
                    )
                }
                dao.insertAll(list)
            } catch (_: Exception) { }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Navy, surface = Panel, onSurface = Color.White)) {
        Scaffold(
            containerColor = Navy,
            topBar = {
                TopAppBar(
                    title = { Column { Text("PAYMENT ANNOUNCER", fontSize = 13.sp, color = Mint, fontWeight = FontWeight.Bold); Text(page, fontWeight = FontWeight.Bold) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    listOf("Home","History","Insights","Voice","Settings").forEach { item ->
                        val icon = when(item) { "Home"->Icons.Default.Home; "History"->Icons.Default.ReceiptLong; "Insights"->Icons.Default.BarChart; "Voice"->Icons.Default.RecordVoiceOver; else->Icons.Default.Settings }
                        NavigationBarItem(selected=page==item,onClick={page=item},icon={Icon(icon,null)},label={Text(item)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Mint,selectedTextColor=Mint,indicatorColor=Navy,unselectedTextColor=Muted,unselectedIconColor=Muted))
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
                when(page) {
                    "Home" -> {
                        Spacer(Modifier.height(10.dp))
                        CardBlock {
                            Text("TOTAL RECEIVED", color=Muted, fontSize=12.sp)
                            Text("₹${"%.2f".format(received)}", fontSize=32.sp, fontWeight=FontWeight.Bold, color=Mint)
                            Text("Total sent  ₹${"%.2f".format(sent)}", color=Muted)
                            Text("${payments.size} notifications saved", color=Muted, fontSize=12.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            SmallStat("Received","₹${"%.0f".format(received)}",Mint,Modifier.weight(1f))
                            SmallStat("Sent","₹${"%.0f".format(sent)}",Color(0xFFFFB86B),Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Recent activity",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        if(payments.isEmpty()) EmptyState("No payments detected yet","Enable notification access and receive a UPI payment notification.")
                        else payments.take(5).forEach { PaymentRow(it) }
                    }
                    "History" -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search app or details")},singleLine=true)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=8.dp)) {
                            listOf("ALL","RECEIVED","SENT").forEach { f -> FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)}) }
                        }
                        val filtered=payments.filter { (filter=="ALL" || it.direction==filter) && (query.isBlank() || it.appName.contains(query,true) || it.detail.contains(query,true)) }
                        if(filtered.isEmpty()) EmptyState("Nothing here","Matching payment notifications will appear here.")
                        else LazyColumn { items(filtered,key={it.id}) { PaymentRow(it) } }
                    }
                    "Insights" -> {
                        Spacer(Modifier.height(12.dp))
                        CardBlock {
                            Text("ALL-TIME OVERVIEW",color=Muted,fontSize=12.sp)
                            Text("₹${"%.2f".format(received+sent)}",fontSize=30.sp,fontWeight=FontWeight.Bold)
                            Text("Total payment volume",color=Muted)
                            Spacer(Modifier.height(12.dp))
                            Text("Received  ₹${"%.2f".format(received)}",color=Mint)
                            LinearProgressIndicator(progress={if(received+sent==0.0) 0f else (received/(received+sent)).toFloat()},modifier=Modifier.fillMaxWidth(),color=Mint)
                            Spacer(Modifier.height(8.dp))
                            Text("Sent  ₹${"%.2f".format(sent)}",color=Color(0xFFFFB86B))
                            LinearProgressIndicator(progress={if(received+sent==0.0) 0f else (sent/(received+sent)).toFloat()},modifier=Modifier.fillMaxWidth(),color=Color(0xFFFFB86B))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("By app",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        payments.groupBy { it.appName }.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }.forEach { e ->
                            CardBlock { Text(e.key,fontWeight=FontWeight.Bold); Text("₹${"%.2f".format(e.value.sumOf { it.amount })} • ${e.value.size} notifications",color=Muted) }
                        }
                    }
                    "Voice" -> {
                        Spacer(Modifier.height(12.dp))
                        CardBlock {
                            SettingSwitch("Voice announcements",voice) { voice=it; prefs.edit().putBoolean("voice_enabled",it).apply() }
                            Divider(color=Muted.copy(alpha=.2f))
                            SettingSwitch("Incoming payments",incoming) { incoming=it; prefs.edit().putBoolean("incoming",it).apply() }
                            SettingSwitch("Outgoing payments",outgoing) { outgoing=it; prefs.edit().putBoolean("outgoing",it).apply() }
                        }
                        Spacer(Modifier.height(12.dp))
                        CardBlock {
                            Text("Announcement language",fontWeight=FontWeight.Bold)
                            listOf("hi" to "Hindi","en" to "English","hinglish" to "Hinglish").forEach { (key,label) ->
                                Row(Modifier.fillMaxWidth().clickable { language=key; prefs.edit().putString("voice_language",key).apply() }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    RadioButton(selected=language==key,onClick={language=key;prefs.edit().putString("voice_language",key).apply()})
                                    Text(label)
                                }
                            }
                            Button(onClick={
                                speakTest(context, language)
                            },modifier=Modifier.fillMaxWidth()) { Text("Test voice") }
                            Text("Hinglish uses the Hindi voice engine where available.",color=Muted,fontSize=12.sp)
                        }
                    }
                    "Settings" -> {
                        Spacer(Modifier.height(12.dp))
                        CardBlock {
                            Text("Notification access",fontWeight=FontWeight.Bold)
                            Text("Required to read payment alerts from UPI apps. No bank login or bank connection is used.",color=Muted,fontSize=13.sp)
                            Button(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},modifier=Modifier.fillMaxWidth()) { Text("Open notification access settings") }
                        }
                        Spacer(Modifier.height(12.dp))
                        CardBlock {
                            Text("Data & backup",fontWeight=FontWeight.Bold)
                            Button(onClick={exportLauncher.launch("payment-history.csv")},modifier=Modifier.fillMaxWidth()) { Text("Export CSV") }
                            OutlinedButton(onClick={backupLauncher.launch("payment-announcer-backup.json")},modifier=Modifier.fillMaxWidth()) { Text("Create JSON backup") }
                            OutlinedButton(onClick={restoreLauncher.launch(arrayOf("application/json"))},modifier=Modifier.fillMaxWidth()) { Text("Restore JSON backup") }
                            OutlinedButton(onClick={scope.launch { dao.clear() }},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color(0xFFFF8585))) { Text("Delete all local history") }
                        }
                        Spacer(Modifier.height(12.dp))
                        CardBlock {
                            Text("About",fontWeight=FontWeight.Bold)
                            Text("Payment Announcer 1.0.0",color=Muted)
                            Text("Records are extracted from notifications only. They are not verified bank statements. Detection depends on the wording exposed by each app.",color=Muted,fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),content=content)
    }
}
@Composable
private fun SmallStat(label:String,value:String,color:Color,modifier:Modifier=Modifier) {
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) { Text(label,color=Muted,fontSize=12.sp); Text(value,color=color,fontWeight=FontWeight.Bold,fontSize=22.sp) }
    }
}
@Composable
private fun PaymentRow(p:Payment) {
    Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(if(p.direction=="RECEIVED") Icons.Default.CallReceived else Icons.Default.CallMade,null,tint=if(p.direction=="RECEIVED") Mint else Color(0xFFFFB86B))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.appName,fontWeight=FontWeight.SemiBold)
                Text(SimpleDateFormat("dd MMM, hh:mm a",Locale.getDefault()).format(Date(p.createdAt)),color=Muted,fontSize=11.sp)
            }
            Text("${if(p.direction=="RECEIVED") "+" else "-"}₹${"%.2f".format(p.amount)}",color=if(p.direction=="RECEIVED") Mint else Color(0xFFFFB86B),fontWeight=FontWeight.Bold)
        }
    }
}
@Composable
private fun EmptyState(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Default.NotificationsNone,null,tint=Mint,modifier=Modifier.size(42.dp))
        Text(title,fontWeight=FontWeight.Bold)
        Text(subtitle,color=Muted,fontSize=12.sp)
    }
}
@Composable
private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f))
        Switch(checked,onChange)
    }
}

private fun speakTest(context: android.content.Context, language: String) {
    var engine: TextToSpeech? = null
    engine = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val locale = if (language == "en") Locale.US else Locale("hi", "IN")
            engine?.language = locale
            engine?.speak("Payment Announcer voice test", TextToSpeech.QUEUE_FLUSH, null, "voice-test")
        }
    }
}
