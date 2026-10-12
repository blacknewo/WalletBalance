package jp.maki.wallettracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun AboutDialog(onDismiss: () -> Unit) {
    val buildType = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
    val buildTypeColor = if (BuildConfig.DEBUG) Color(0xFFD43C35) else Color(0xFF008433)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("アプリについて", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "まきちゃんのお小遣い帳",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "バージョン",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ビルド",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildType,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = buildTypeColor
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
fun UpdateAvailableDialog(
    releaseInfo: AppReleaseInfo,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "新しいバージョンがあります",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "バージョン: v${releaseInfo.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (releaseInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = "更新内容:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = releaseInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "※ダウンロード後、自動的にインストール画面が開きます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("今すぐアップデート")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("あとで")
            }
        }
    )
}

