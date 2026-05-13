package jp.maki.wallettracker

import android.content.ContentValues
import android.content.Context
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

// ==============================================================
// バックアップ / リストア ヘルパー関数
// ==============================================================

/**
 * Transaction リストを JSON 文字列に変換する。
 * 形式: [{"id":1, "type":"income", "amount":1000, "memo":"...", "createdAt":...}, ...]
 */
fun transactionsToJson(transactions: List<Transaction>): String {
    val array = JSONArray()
    for (tx in transactions) {
        val obj = JSONObject()
        obj.put("id", tx.id)
        obj.put("type", tx.type)
        obj.put("amount", tx.amount)
        obj.put("memo", tx.memo)
        obj.put("createdAt", tx.createdAt)
        array.put(obj)
    }
    return array.toString(2) // インデント付きで読みやすく
}

/**
 * JSON 文字列を Transaction リストに変換する。
 * パース失敗時は null を返す（呼び出し元でエラーハンドリング）。
 */
fun jsonToTransactions(json: String): List<Transaction>? {
    return try {
        val array = JSONArray(json)
        val list = mutableListOf<Transaction>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Transaction(
                    // id は Room が自動採番するため 0 を渡し、insert 時に新規 ID が振られる
                    id = 0,
                    type = obj.getString("type"),
                    amount = obj.getInt("amount"),
                    memo = obj.optString("memo", ""),
                    createdAt = obj.getLong("createdAt")
                )
            )
        }
        list
    } catch (e: Exception) {
        null
    }
}

/**
 * ダウンロードフォルダに MakiWallet.json を書き出す（バックアップ）。
 * Android 10 以上: MediaStore 経由。
 * Android 9 以下: 直接ファイルアクセス。
 * @return 成功時 true、失敗時 false
 */
fun performBackup(context: Context, jsonContent: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) 以上: MediaStore を使用
            val resolver = context.contentResolver
            val fileName = "MakiWallet.json"

            // 既存の同名ファイルがあれば先に削除（上書き相当）
            val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            resolver.query(queryUri, arrayOf(MediaStore.Downloads._ID), selection, arrayOf(fileName), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    resolver.delete(Uri.withAppendedPath(queryUri, id.toString()), null, null)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(jsonContent.toByteArray(Charsets.UTF_8))
            }
        } else {
            // Android 9 (API 28) 以下: 直接ファイル操作
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, "MakiWallet.json")
            file.writeText(jsonContent, Charsets.UTF_8)
        }
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * 選択された URI から JSON を読み込む（リストア）。
 * @return JSON 文字列、失敗時は null
 */
fun readJsonFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}

// --- ViewModel ---
class WalletViewModel(private val dao: TransactionDao) : ViewModel() {
    val balance = dao.getBalanceFlow()
    val recentTransactions = dao.getRecentFlow()

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
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
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

                Text(
                    text = "最近の履歴",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.Start),
                    color = Color.Gray
                )
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
    }
}

@Composable
fun ReconciliationDialog(currentBalance: Int, onDismiss: () -> Unit) {
    var actualBalanceText by remember { mutableStateOf("") }
    val actualBalance = actualBalanceText.toIntOrNull() ?: 0
    val diff = actualBalance - currentBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("残高の照合", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("記録上の残高: ${formatCurrency(currentBalance)}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = actualBalanceText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) actualBalanceText = it },
                    label = { Text("実際の財布の金額") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (actualBalanceText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "差額: ${if (diff >= 0) "+" else ""}${formatCurrency(diff)}",
                        color = if (diff == 0) Color(0xFF008433) else Color(0xFFD43C35),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
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

@Composable
fun InputOverlay(
    visible: Boolean,
    type: String,
    transaction: Transaction? = null,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    if (visible) {
        val focusManager = LocalFocusManager.current
        // 表示されるたびにオフセットをリセット
        var dragOffsetY by remember(visible) { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding() // OSバージョンとsoftInputModeに合わせて自動で最適な回避を行う
        ) {
            // 背景レイヤー
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha((1f - (dragOffsetY / 600f)).coerceIn(0f, 1f))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        focusManager.clearFocus(force = true)
                        onDismiss() 
                    }
            )

            // フォームパネル
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .then(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            Modifier.border(1.dp, Color.LightGray, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        } else {
                            Modifier
                        }
                    )
                    .offset { 
                        // キーボード回避は imePadding に任せ、ここではドラッグ分のみ計算
                        IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) 
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragOffsetY += delta
                        },
                        onDragStopped = {
                            if (dragOffsetY > 300) {
                                focusManager.clearFocus(force = true)
                                onDismiss()
                            } else {
                                dragOffsetY = 0f
                            }
                        }
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 8.dp else 0.dp,
                shadowElevation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 8.dp else 0.dp
            ) {
                Column {
                    // ドラッグハンドル
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(Color.LightGray, RoundedCornerShape(2.dp))
                    )
                    
                    InputForm(
                        type = type,
                        transaction = transaction,
                        onSave = { amount, memo ->
                            focusManager.clearFocus(force = true)
                            onSave(amount, memo)
                        },
                        onDelete = { tx ->
                            focusManager.clearFocus(force = true)
                            onDelete(tx)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun InputForm(
    type: String,
    transaction: Transaction? = null,
    onSave: (Int, String) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val memoFocusRequester = remember { FocusRequester() }

    // 修正モードなら既存の値を、新規なら空をセット
    var amountText by rememberSaveable(transaction) { 
        mutableStateOf(transaction?.amount?.toString() ?: "") 
    }
    var memoText by rememberSaveable(transaction) { 
        mutableStateOf(transaction?.memo ?: "") 
    }
    
    val amount = amountText.toIntOrNull() ?: 0
    val isValid = amount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        val title = when {
            transaction != null && type == "income" -> "入金を修正"
            transaction != null -> "支出を修正"
            type == "income" -> "入金の記録"
            else -> "支出の記録"
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (type == "income") Color(0xFF008433) else Color(0xFFD43C35)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
            label = { Text("金額 (¥)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { memoFocusRequester.requestFocus() }
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = memoText,
            onValueChange = { memoText = it },
            label = { Text("メモ (任意)") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(memoFocusRequester),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (isValid) onSave(amount, memoText) },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (transaction != null) "修正する" else "保存する",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (transaction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = { onDelete(transaction) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("この履歴を削除する", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun formatCurrency(amount: Int): String {
    return NumberFormat.getCurrencyInstance(Locale.JAPAN).format(amount)
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
    return sdf.format(date)
}
