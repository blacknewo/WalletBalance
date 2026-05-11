package jp.maki.wallettracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

// --- ViewModel ---
class WalletViewModel(private val dao: TransactionDao) : ViewModel() {
    val balance = dao.getBalanceFlow()
    val recentTransactions = dao.getRecentFlow()

    fun addTransaction(type: String, amount: Int, memo: String) {
        val transaction = Transaction(type = type, amount = amount, memo = memo)
        // 本来は viewModelScope.launch が必要だが、簡略化のためここでUIから直接呼ぶか、DAOをsuspendにする
        // ここではUI側でCoroutineScopeを使う形にするか、ViewModel内でlaunchする
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
        val db = AppDatabase.getDatabase(this)
        val dao = db.transactionDao()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF005292), // デジタル庁推奨に近い青
                    secondary = Color(0xFF008433), // 入金用の緑
                    error = Color(0xFFD43C35) // 支出用の赤
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WalletApp(dao)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp(dao: TransactionDao) {
    val viewModel: WalletViewModel = viewModel(factory = WalletViewModelFactory(dao))
    val balance by viewModel.balance.collectAsState(initial = 0)
    val transactions by viewModel.recentTransactions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    var transactionType by remember { mutableStateOf("expense") }

    var showMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showReconciliationDialog by remember { mutableStateOf(false) }

    Scaffold(
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
            // 現在残高 (さらに大きく、視覚的インパクトを最大化)
            Text(
                text = "現在残高",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray
            )
            Text(
                text = formatCurrency(balance ?: 0),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 64.sp, // 48sp -> 64sp
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            // 照合ボタン
            TextButton(
                onClick = { showReconciliationDialog = true },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("実際の財布と照合する", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.weight(1f)) // 下部にボタンを寄せて親指で押しやすくする

            // 入出金ボタン
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

            // 最近の履歴 (ミニマルなリスト)
            Text(
                text = "最近の履歴",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start),
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1.5f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { tx ->
                    SimpleTransactionItem(tx)
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState
            ) {
                InputForm(
                    type = transactionType,
                    onSave = { amount, memo ->
                        scope.launch {
                            viewModel.insert(Transaction(type = transactionType, amount = amount, memo = memo))
                            showSheet = false
                        }
                    }
                )
            }
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
            Column {
                Text("記録上の残高: ${formatCurrency(currentBalance)}")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = actualBalanceText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) actualBalanceText = it },
                    label = { Text("実際の財布の金額") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (actualBalanceText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "差額: ${if (diff >= 0) "+" else ""}${formatCurrency(diff)}",
                        color = if (diff == 0) Color(0xFF008433) else Color(0xFFD43C35),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
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

@Composable
fun SimpleTransactionItem(tx: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                modifier = Modifier.width(100.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tx.memo.ifEmpty { if (tx.type == "income") "入金" else "支出" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
        }
        Text(
            text = formatDate(tx.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun InputForm(type: String, onSave: (Int, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var memoText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (type == "income") "入金の記録" else "支出の記録",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (type == "income") Color(0xFF008433) else Color(0xFFD43C35)
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
            label = { Text("金額 (¥)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = memoText,
            onValueChange = { memoText = it },
            label = { Text("メモ (任意)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val amount = amountText.toIntOrNull() ?: 0
                if (amount > 0) onSave(amount, memoText)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("保存する", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
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
