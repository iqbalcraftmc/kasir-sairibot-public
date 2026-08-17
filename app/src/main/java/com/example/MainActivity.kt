package com.iqbalcraftmc.sairibokasir.publicversion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("user_public_config", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("user_api_key", "") ?: ""
        set(value) = prefs.edit().putString("user_api_key", value.trim()).apply()

    var githubUser: String
        get() = prefs.getString("user_github_user", "") ?: ""
        set(value) = prefs.edit().putString("user_github_user", value.trim()).apply()

    var githubRepo: String
        get() = prefs.getString("user_github_repo", "bayar.github.io") ?: "bayar.github.io"
        set(value) = prefs.edit().putString("user_github_repo", value.trim()).apply()

    val paymentBaseUrl: String
        get() = if (githubUser.isNotEmpty()) {
            "https://$githubUser.github.io/$githubRepo/"
        } else {
            ""
        }

    var isNotificationMuted: Boolean
        get() = prefs.getBoolean("is_notification_muted", false)
        set(value) = prefs.edit().putBoolean("is_notification_muted", value).apply()
}



@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val invoiceId: String,
    val amount: Int,
    val fee: Int,
    val total: Int,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET status = :status WHERE invoiceId = :invoiceId")
    suspend fun updateStatus(invoiceId: String, status: String)

    @Query("SELECT SUM(total) FROM invoices WHERE status = 'paid' OR status = 'PAID'")
    fun getTotalBalance(): Flow<Int?>
}

@Database(entities = [InvoiceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kasir_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class Invoice(
    val invoiceId: String,
    val amount: Int,
    val fee: Int,
    val total: Int,
    val qrisImageUrl: String,
    val expiredAt: String
)

object ApiService {
    private val client = OkHttpClient()

    suspend fun createInvoice(amount: String, apiKey: String): Invoice? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val url = "https://api.sairibot.my.id/api/invoice?apikey=$apiKey&amount=$amount"
        val request = Request.Builder().url(url).build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                body?.let {
                    val json = JSONObject(it)
                    if (json.optBoolean("success", false)) {
                        val qrisUrl = json.optString("qris_image", "")
                        return@withContext Invoice(
                            invoiceId = json.optString("invoice_id"),
                            amount = json.optInt("amount"),
                            fee = json.optInt("fee"),
                            total = json.optInt("total"),
                            qrisImageUrl = qrisUrl,
                            expiredAt = json.optString("expired_at")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun checkStatus(invoiceId: String, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "pending"
        val url = "https://api.sairibot.my.id/api/invoice/status?apikey=$apiKey&invoice_id=$invoiceId"
        val request = Request.Builder().url(url).build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                body?.let {
                    val json = JSONObject(it)
                    return@withContext json.optString("status", "pending")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext "pending"
    }

    suspend fun getBalance(apiKey: String): Int = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext 0
        val url = "https://api.sairibot.my.id/api/balance?apikey=$apiKey"
        val request = Request.Builder().url(url).build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                body?.let {
                    val json = JSONObject(it)
                    return@withContext json.optInt("balance", 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext 0
    }

    suspend fun fetchTransactionHistory(apiKey: String): List<InvoiceEntity>? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val endpoints = listOf(
            "https://api.sairibot.my.id/api/invoice/history?apikey=$apiKey",
            "https://api.sairibot.my.id/api/history?apikey=$apiKey",
            "https://api.sairibot.my.id/api/invoices?apikey=$apiKey"
        )
        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val array = when {
                            json.has("data") && json.get("data") is JSONArray -> json.getJSONArray("data")
                            json.has("invoices") && json.get("invoices") is JSONArray -> json.getJSONArray("invoices")
                            json.has("history") && json.get("history") is JSONArray -> json.getJSONArray("history")
                            else -> null
                        }
                        if (array != null) {
                            val list = mutableListOf<InvoiceEntity>()
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                val invId = item.optString("invoice_id").ifEmpty { item.optString("id") }
                                if (invId.isNotEmpty()) {
                                    val amount = item.optInt("amount", 0)
                                    val fee = item.optInt("fee", 0)
                                    val total = item.optInt("total", amount + fee)
                                    val status = item.optString("status", "pending")
                                    list.add(InvoiceEntity(invId, amount, fee, total, status))
                                }
                            }
                            if (list.isNotEmpty()) return@withContext list
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return@withContext null
    }
}

class KasirViewModel(application: android.app.Application) : AndroidViewModel(application) {
    val appSettings = AppSettings(application)
    private val prefs = application.getSharedPreferences("kasir_prefs", Context.MODE_PRIVATE)
    val androidId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"

    private val dao = AppDatabase.getDatabase(application).invoiceDao()
    val history = dao.getAllInvoices().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _apiBalance = MutableStateFlow(0)
    val balance = _apiBalance.asStateFlow()

    private val _isSyncingHistory = MutableStateFlow(false)
    val isSyncingHistory = _isSyncingHistory.asStateFlow()

    private val _isNotificationMuted = MutableStateFlow(appSettings.isNotificationMuted)
    val isNotificationMuted = _isNotificationMuted.asStateFlow()

    fun toggleNotificationMuted() {
        val newState = !_isNotificationMuted.value
        appSettings.isNotificationMuted = newState
        _isNotificationMuted.value = newState
    }

    init {
        fetchBalance()
    }



    fun fetchBalance() {
        viewModelScope.launch {
            _apiBalance.value = ApiService.getBalance(appSettings.apiKey)
        }
    }

    private val _amount = MutableStateFlow("1000")
    val amount = _amount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _invoice = MutableStateFlow<Invoice?>(null)
    val invoice = _invoice.asStateFlow()

    private val _status = MutableStateFlow("pending")
    val status = _status.asStateFlow()

    private var pollingJob: Job? = null

    fun updateAmount(newAmount: String) {
        _amount.value = newAmount
    }

    fun generateQris() {
        viewModelScope.launch {
            _isLoading.value = true
            _invoice.value = null
            _status.value = "pending"
            stopPolling()
            val result = ApiService.createInvoice(_amount.value, appSettings.apiKey)
            _invoice.value = result
            _isLoading.value = false
            if (result != null) {
                dao.insertInvoice(InvoiceEntity(result.invoiceId, result.amount, result.fee, result.total, "pending"))
                startPolling(result.invoiceId)
            }
        }
    }

    fun checkStatusManually() {
        val id = _invoice.value?.invoiceId ?: return
        viewModelScope.launch {
            val currentStatus = ApiService.checkStatus(id, appSettings.apiKey)
            _status.value = currentStatus
            dao.updateStatus(id, currentStatus)
            if (currentStatus == "paid" || currentStatus == "expired") {
                stopPolling()
            }
        }
    }

    private fun startPolling(invoiceId: String) {
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                val currentStatus = ApiService.checkStatus(invoiceId, appSettings.apiKey)
                _status.value = currentStatus
                dao.updateStatus(invoiceId, currentStatus)
                if (currentStatus == "paid" || currentStatus == "expired") {
                    break
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun clearInvoice() {
        _invoice.value = null
        _amount.value = "1000"
        _status.value = "pending"
        stopPolling()
    }

    fun syncTransactionHistory() {
        viewModelScope.launch {
            _isSyncingHistory.value = true
            try {
                val remoteList = ApiService.fetchTransactionHistory(appSettings.apiKey)
                if (!remoteList.isNullOrEmpty()) {
                    for (inv in remoteList) {
                        dao.insertInvoice(inv)
                    }
                }

                val currentLocalList = history.value
                for (inv in currentLocalList) {
                    val realStatus = ApiService.checkStatus(inv.invoiceId, appSettings.apiKey)
                    if (realStatus.isNotEmpty() && !realStatus.equals(inv.status, ignoreCase = true)) {
                        dao.updateStatus(inv.invoiceId, realStatus)
                    }
                }

                _apiBalance.value = ApiService.getBalance(appSettings.apiKey)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncingHistory.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullScreen()
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                KasirQrisScreen()
            }
        }
    }

    private fun enableFullScreen() {
        // Memastikan tampilan aplikasi bisa memenuhi seluruh area layar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)

        // Menyembunyikan status bar (notifikasi) & navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // Set mode sticky: bar hanya muncul sementara saat di-swipe dari tepi layar
        controller.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

enum class BottomTab { HOME, RIWAYAT, SALDO, PROFIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirQrisScreen(viewModel: KasirViewModel = viewModel()) {
    val primaryColor = Color(0xFF0061A4)
    val bgColor = Color(0xFFF3F4F9)
    val textColor = Color(0xFF1B1B1F)

    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    val amount by viewModel.amount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val invoice by viewModel.invoice.collectAsState()
    val status by viewModel.status.collectAsState()
    val history by viewModel.history.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isNotificationMuted by viewModel.isNotificationMuted.collectAsState()
    val context = LocalContext.current

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { _ -> }
        LaunchedEffect(Unit) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_qris_logo),
                                contentDescription = "Logo QRIS",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SairiBot Kasir",
                                color = textColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "PUBLIC EDITION V2.4",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(if (isNotificationMuted) Color(0xFFFEE2E2) else Color(0xFFF8FAFC), CircleShape)
                            .border(1.dp, if (isNotificationMuted) Color(0xFFFECACA) else Color(0xFFF1F5F9), CircleShape)
                            .clickable { 
                                viewModel.toggleNotificationMuted()
                                val msg = if (!isNotificationMuted) "Notifikasi dibisukan" else "Notifikasi diaktifkan"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isNotificationMuted) "🔕" else "🔔", fontSize = 18.sp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BottomNavItem("🏠", "Home", if (currentTab == BottomTab.HOME) primaryColor else Color(0xFF64748B), currentTab == BottomTab.HOME) { currentTab = BottomTab.HOME }
                    BottomNavItem("📊", "Riwayat", if (currentTab == BottomTab.RIWAYAT) primaryColor else Color(0xFF64748B), currentTab == BottomTab.RIWAYAT) { currentTab = BottomTab.RIWAYAT }
                    BottomNavItem("💳", "Saldo", if (currentTab == BottomTab.SALDO) primaryColor else Color(0xFF64748B), currentTab == BottomTab.SALDO) { currentTab = BottomTab.SALDO }
                    BottomNavItem("👤", "Profil", if (currentTab == BottomTab.PROFIL) primaryColor else Color(0xFF64748B), currentTab == BottomTab.PROFIL) { currentTab = BottomTab.PROFIL }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                BottomTab.HOME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Color(0x80E2E8F0)),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "NOMINAL TRANSAKSI (IDR)",
                                    color = primaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                                
                                TextField(
                                    value = amount,
                                    onValueChange = { viewModel.updateAmount(it) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                        .border(2.dp, if (amount.isNotBlank()) primaryColor else Color.Transparent, RoundedCornerShape(16.dp)),
                                    singleLine = true,
                                    leadingIcon = {
                                        Text(
                                            text = "Rp",
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                        )
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = textColor
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = bgColor,
                                        unfocusedContainerColor = bgColor,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = primaryColor
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = { viewModel.generateQris() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryColor,
                                        disabledContainerColor = primaryColor.copy(alpha = 0.5f)
                                    ),
                                    enabled = !isLoading && amount.isNotBlank()
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("⚡", fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Generate QRIS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (invoice != null) {
                            InvoiceSection(
                                invoice = invoice!!,
                                status = status,
                                onCheckStatus = { viewModel.checkStatusManually() },
                                onDone = { viewModel.clearInvoice() },
                                context = context,
                                appSettings = viewModel.appSettings
                            )
                        }
                    }
                }
                BottomTab.RIWAYAT -> {
                    LaunchedEffect(Unit) {
                        viewModel.syncTransactionHistory()
                    }
                    val isSyncing by viewModel.isSyncingHistory.collectAsState()
                    RiwayatScreen(
                        history = history,
                        primaryColor = primaryColor,
                        isSyncing = isSyncing,
                        onSync = { viewModel.syncTransactionHistory() }
                    )
                }
                BottomTab.SALDO -> {
                    LaunchedEffect(Unit) { viewModel.fetchBalance() }
                    SaldoScreen(balance, primaryColor) { viewModel.fetchBalance() }
                }
                BottomTab.PROFIL -> ProfilScreen(primaryColor, viewModel)
            }
        }
    }
}

@Composable
fun RiwayatScreen(
    history: List<InvoiceEntity>,
    primaryColor: Color,
    isSyncing: Boolean = false,
    onSync: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Riwayat Transaksi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                Text("Disinkronkan secara real-time", fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(
                onClick = onSync,
                enabled = !isSyncing,
                modifier = Modifier
                    .size(36.dp)
                    .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = primaryColor)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = primaryColor, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Belum ada transaksi", fontWeight = FontWeight.SemiBold, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(it.invoiceId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it.createdAt)),
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Rp${it.total}", fontWeight = FontWeight.Bold, color = primaryColor, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaldoScreen(balance: Int, primaryColor: Color, onRefresh: () -> Unit) {
    var showWithdrawSheet by remember { mutableStateOf(false) }
    var isWebViewLoading by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val withdrawUrl = "https://api.sairibot.my.id/withdraw"

    if (showWithdrawSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWithdrawSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💸 Penarikan Saldo SairiBot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { showWithdrawSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.Gray)
                    }
                }

                if (isWebViewLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = primaryColor)
                }

                Box(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                    }
                                }
                                loadUrl(withdrawUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Total Saldo Tersedia", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rp$balance", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = primaryColor)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = primaryColor)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { showWithdrawSheet = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
        ) {
            Text("🌐 Penarikan Saldo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfilScreen(primaryColor: Color, viewModel: KasirViewModel) {
    val context = LocalContext.current
    val appSettings = viewModel.appSettings

    var apiKeyInput by remember { mutableStateOf(appSettings.apiKey) }
    var ghUserInput by remember { mutableStateOf(appSettings.githubUser) }
    var ghRepoInput by remember { mutableStateOf(appSettings.githubRepo) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(72.dp).background(primaryColor, CircleShape), contentAlignment = Alignment.Center) {
            Text("SB", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("SairiBot Kasir (Publik)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Atur API Key & Link GitHub Anda di bawah ini", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚙️ Konfigurasi Pengguna Publik", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key SairiBot") },
                    placeholder = { Text("Masukkan API Key Anda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ghUserInput,
                    onValueChange = { ghUserInput = it },
                    label = { Text("Username GitHub (Web Tagihan)") },
                    placeholder = { Text("Contoh: username_anda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ghRepoInput,
                    onValueChange = { ghRepoInput = it },
                    label = { Text("Nama Repository GitHub Pages") },
                    placeholder = { Text("Default: bayar.github.io") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        appSettings.apiKey = apiKeyInput
                        appSettings.githubUser = ghUserInput
                        appSettings.githubRepo = ghRepoInput
                        viewModel.fetchBalance()
                        Toast.makeText(context, "Pengaturan Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("💾 Simpan Konfigurasi", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📖 Panduan Setup Web Tagihan GitHub", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                Spacer(modifier = Modifier.height(6.dp))
                Text("1. Buka atau fork template repository: https://github.com/iqbalcraftmc/bayar.github.io", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                Text("2. Atau buat repository baru dengan nama 'bayar.github.io' dan unggah template web tagihan.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                Text("3. Aktifkan GitHub Pages di menu Settings > Pages.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                Text("4. Masukkan Username GitHub Anda pada formulir konfigurasi di atas.", fontSize = 11.sp, color = Color(0xFF1E3A8A))

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iqbalcraftmc/bayar.github.io"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("⭐ Buka Template", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/new"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🚀 Buat Repo Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
@Composable
fun BottomNavItem(icon: String, label: String, color: Color, isActive: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp).clickable(onClick = onClick).alpha(if (isActive) 1f else 0.4f)
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .background(if (isActive) color.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun InvoiceSection(
    invoice: Invoice,
    status: String,
    onCheckStatus: () -> Unit,
    onDone: () -> Unit,
    context: Context,
    appSettings: AppSettings
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color(0x80E2E8F0)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(192.dp)
                    .background(Color(0xFFF9FAFB), RoundedCornerShape(16.dp))
                    .border(BorderStroke(2.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                QrisImage(imageUrl = invoice.qrisImageUrl, modifier = Modifier.size(144.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            StatusBadge(status = status)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(icon = "🔄", label = "Cek Status", modifier = Modifier.weight(1f), onClick = onCheckStatus)
                    ActionCard(icon = "💬", label = "Kirim WA", modifier = Modifier.weight(1f), onClick = { sendToWhatsApp(context, invoice.total, invoice.invoiceId, invoice.qrisImageUrl, appSettings) })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(icon = "📋", label = "Salin Link", modifier = Modifier.weight(1f), onClick = { copyToClipboard(context, invoice.total, invoice.invoiceId, invoice.qrisImageUrl, appSettings) })
                    ActionCard(icon = "✅", label = "Selesai", modifier = Modifier.weight(1f), onClick = onDone)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shadowElevation = 1.dp,
        modifier = modifier.height(80.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
            Text(text = label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = (-0.5).sp)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    Surface(
        color = Color(0xFFFFF8E1),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color(0xFFFFD54F))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(text = "🟡", fontSize = 10.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = status.uppercase(), color = Color(0xFF795548), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun QrisImage(imageUrl: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = "QRIS Image",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

fun sendToWhatsApp(context: Context, total: Int, invoiceId: String, qrisUrl: String, appSettings: AppSettings) {
    val baseUrl = appSettings.paymentBaseUrl
    if (baseUrl.isBlank()) {
        Toast.makeText(context, "Harap isi Username GitHub di menu Profil dulu!", Toast.LENGTH_LONG).show()
        return
    }

    val encodedTotal = URLEncoder.encode(total.toString(), "UTF-8")
    val encodedInvId = URLEncoder.encode(invoiceId, "UTF-8")
    val encodedQris = URLEncoder.encode(qrisUrl, "UTF-8")
    
    val link = "$baseUrl?inv=$encodedInvId&total=$encodedTotal&qris=$encodedQris"
    val pesan = "Halo! Silakan selesaikan pembayaran QRIS kamu via link resmi berikut:\n\n$link\n\nTerima kasih!"
    
    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode(pesan, "UTF-8")}"))
    try {
        context.startActivity(waIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp tidak terinstall", Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(context: Context, total: Int, invoiceId: String, qrisUrl: String, appSettings: AppSettings) {
    val baseUrl = appSettings.paymentBaseUrl
    if (baseUrl.isBlank()) {
        Toast.makeText(context, "Harap isi Username GitHub di menu Profil dulu!", Toast.LENGTH_LONG).show()
        return
    }

    val encodedTotal = URLEncoder.encode(total.toString(), "UTF-8")
    val encodedInvId = URLEncoder.encode(invoiceId, "UTF-8")
    val encodedQris = URLEncoder.encode(qrisUrl, "UTF-8")
    val link = "$baseUrl?inv=$encodedInvId&total=$encodedTotal&qris=$encodedQris"
    
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("Link Pembayaran", link)
    clipboardManager.setPrimaryClip(clipData)
    Toast.makeText(context, "Link pembayaran disalin!", Toast.LENGTH_SHORT).show()
}
