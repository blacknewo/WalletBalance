package jp.maki.wallettracker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToInt

// --- ViewModel ---
class WalletViewModel(private val dao: TransactionDao) : ViewModel() {
    val balance = dao.getBalanceFlow()
    val recentTransactions = dao.getRecentFlow()
    val allTransactions = dao.getAllFlow()

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.update(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.delete(transaction)
        }
    }

    suspend fun insert(transaction: Transaction) = dao.insert(transaction)

    suspend fun deleteAll() = dao.deleteAll()
}

class WalletViewModelFactory(private val dao: TransactionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalletViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- UI Components ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 縦画面固定（横画面回転を完全に抑止）
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // OSバージョンによってIME制御戦略を切り替える
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11以上: 背景固定＋フォームのみ自律移動（プレミアム構成）
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        } else {
            // Android 10以下: OSによる強制リサイズ（安定性重視の構成）
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            // ステータスバーの白浮き防止のため、直接色を塗る
            window.statusBarColor = android.graphics.Color.parseColor("#005292")
        }

        val dao = AppDatabase.getDatabase(this).transactionDao()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF005292),
                    secondary = Color(0xFF008433),
                    error = Color(0xFFD43C35)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WalletApp(dao)
                }
            }
        }
    }
}

// DAO の全件取得（バックアップ用: suspend 関数として一括取得）
suspend fun TransactionDao.getAllOnce(): List<Transaction> {
    // Flow ではなく一回限りの取得が必要なため、flow.first() で1回だけ収集する
    return withContext(Dispatchers.IO) {
        getAllFlow().first()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp(dao: TransactionDao) {
    val viewModel: WalletViewModel = viewModel(factory = WalletViewModelFactory(dao))
    val balance by viewModel.balance.collectAsState(initial = 0)
    val transactions by viewModel.recentTransactions.collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: メイン, 1: 月別履歴
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var scrollTargetId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    LaunchedEffect(transactions, scrollTargetId) {
        val targetId = scrollTargetId
        if (targetId != null) {
            val index = transactions.indexOfFirst { it.id == targetId }
            if (index >= 0 && !listState.isScrollInProgress) {
                listState.animateScrollToItem(index)
            }
            scrollTargetId = null
        }
    }

    var showSheet by remember { mutableStateOf(false) }
    var transactionType by remember { mutableStateOf("income") }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showReconciliationDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Transaction?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf<Uri?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }

    // 起動時に最新バージョンを裏側でチェック
    LaunchedEffect(Unit) {
        val update = AppUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
        if (update != null) {
            availableUpdate = update
        }
    }

    // リストア: ファイルピッカーのランチャー
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // ユーザーが選択したファイルの確認ダイアログを表示するためにURIを保持
            showRestoreConfirmDialog = uri
        }
    }

    val isModern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = if (isModern) WindowInsets.systemBars else WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text("まきちゃんのお小遣い帳", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // バックアップ
                            DropdownMenuItem(
                                text = { Text("バックアップ") },
                                onClick = {
                                    showMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        val allTransactions = dao.getAllOnce()
                                        val json = transactionsToJson(allTransactions)
                                        val success = performBackup(context, json)
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(
                                                    context,
                                                    "バックアップ完了: ダウンロードフォルダに MakiWallet.json を保存しました",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "バックアップに失敗しました",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )
                            // リストア
                            DropdownMenuItem(
                                text = { Text("リストア") },
                                onClick = {
                                    showMenu = false
                                    // application/json と */* の両方でファイルを選択可能にする
                                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("残高の照合") },
                                onClick = {
                                    showMenu = false
                                    showReconciliationDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("残高のリセット", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showResetDialog = true
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("アップデートを確認") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        Toast.makeText(context, "最新バージョンを確認中...", Toast.LENGTH_SHORT).show()
                                        val update = AppUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
                                        if (update != null) {
                                            availableUpdate = update
                                        } else {
                                            Toast.makeText(context, "現在ご利用のバージョンは最新です (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("アプリについて", color = Color.Gray) },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("入力・残高", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("月別集計", fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedTab == 0) {
                    // --- メイン画面 ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "現在残高",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray
                        )
                        Text(
                            text = formatCurrency(balance ?: 0),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ActionButton(
                                text = "支出",
                                icon = Icons.Default.Remove,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f).height(80.dp)
                            ) {
                                transactionType = "expense"
                                showSheet = true
                            }
                            ActionButton(
                                text = "入金",
                                icon = Icons.Default.Add,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f).height(80.dp)
                            ) {
                                transactionType = "income"
                                showSheet = true
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "最近の履歴 (最新20件)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.Gray
                            )
                            TextButton(
                                onClick = { selectedTab = 1 },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("すべての履歴を見る →", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            state = listState
                        ) {
                            items(items = transactions, key = { it.id }) { tx ->
                                SimpleTransactionItem(tx, onLongClick = {
                                    editingTransaction = tx
                                    transactionType = tx.type
                                    showSheet = true
                                })
                            }
                        }
                    }
                } else {
                    // --- 月別履歴・集計画面 ---
                    MonthlyHistoryView(
                        allTransactions = allTransactions,
                        onTransactionLongClick = { tx ->
                            editingTransaction = tx
                            transactionType = tx.type
                            showSheet = true
                        }
                    )
                }
            }
        }

        InputOverlay(
            visible = showSheet,
            type = transactionType,
            transaction = editingTransaction,
            onDismiss = { 
                showSheet = false
                editingTransaction = null
            },
            onSave = { amount, memo ->
                scope.launch {
                    val currentEditing = editingTransaction
                    if (currentEditing != null) {
                        val updated = currentEditing.copy(amount = amount, memo = memo)
                        viewModel.updateTransaction(updated)
                        scrollTargetId = updated.id
                    } else {
                        val newId = viewModel.insert(Transaction(type = transactionType, amount = amount, memo = memo))
                        scrollTargetId = newId
                    }
                    showSheet = false
                    editingTransaction = null
                }
            },
            onDelete = { tx ->
                showDeleteConfirmDialog = tx
            }
        )

        // 削除確認ダイアログ
        if (showDeleteConfirmDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                title = { Text("履歴の削除") },
                text = { Text("この履歴を削除しますか？この操作は取り消せません。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val tx = showDeleteConfirmDialog
                            if (tx != null) {
                                viewModel.deleteTransaction(tx)
                                showSheet = false
                                editingTransaction = null
                            }
                            showDeleteConfirmDialog = null
                        }
                    ) {
                        Text("削除する", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        if (showReconciliationDialog) {
            ReconciliationDialog(
                currentBalance = balance ?: 0,
                onDismiss = { showReconciliationDialog = false }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("残高のリセット") },
                text = { Text("すべての入出金履歴を削除し、残高を0に戻しますか？この操作は取り消せません。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.deleteAll()
                                showResetDialog = false
                            }
                        }
                    ) {
                        Text("リセットする", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        // リストア確認ダイアログ（ファイル選択後に表示）
        val restoreUri = showRestoreConfirmDialog
        if (restoreUri != null) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = null },
                title = { Text("リストアの確認") },
                text = {
                    Text(
                        "バックアップから復元します。\n" +
                        "現在の全履歴を削除してバックアップデータに置き換えます。\n\n" +
                        "この操作は取り消せません。続けますか？"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreConfirmDialog = null
                            scope.launch(Dispatchers.IO) {
                                val jsonText = readJsonFromUri(context, restoreUri)
                                if (jsonText == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "ファイルの読み込みに失敗しました",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@launch
                                }
                                val restored = jsonToTransactions(jsonText)
                                if (restored == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "ファイルの形式が正しくありません",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@launch
                                }
                                // 既存データをクリアしてからバックアップデータを挿入
                                dao.deleteAll()
                                for (tx in restored) {
                                    dao.insert(tx)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "リストア完了: ${restored.size}件のデータを復元しました",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text("復元する", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = null }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        // アプリについてダイアログ
        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }

        // アップデート通知ダイアログ
        val updateInfo = availableUpdate
        if (updateInfo != null) {
            UpdateAvailableDialog(
                releaseInfo = updateInfo,
                onDismiss = { availableUpdate = null },
                onUpdateClick = {
                    val downloadUrl = updateInfo.apkDownloadUrl
                    if (downloadUrl != null) {
                        Toast.makeText(context, "ダウンロードを開始しました...", Toast.LENGTH_SHORT).show()
                        AppUpdater.startDownloadAndInstall(context, downloadUrl, updateInfo.versionName)
                    } else {
                        // APK が直接見つからない場合は GitHub Releases ページを開く
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/blacknewo/WalletBalance/releases/latest"))
                        context.startActivity(intent)
                    }
                    availableUpdate = null
                }
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleTransactionItem(tx: Transaction, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* 通常タップは何もしない */ },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (tx.type == "income") "+" else "-",
                    color = if (tx.type == "income") Color(0xFF008433) else Color(0xFFD43C35),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = formatCurrency(tx.amount),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = tx.memo.ifEmpty { if (tx.type == "income") "入金" else "支出" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
            Text(
                text = formatDate(tx.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}
