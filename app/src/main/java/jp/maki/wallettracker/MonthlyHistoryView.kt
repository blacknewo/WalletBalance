package jp.maki.wallettracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

data class MonthSummary(
    val yearMonthKey: String, // "yyyy-MM"
    val displayName: String,  // "2026年10月"
    val incomeTotal: Int,
    val expenseTotal: Int,
    val balance: Int,
    val transactions: List<Transaction>
)

fun groupTransactionsByMonth(transactions: List<Transaction>): List<MonthSummary> {
    val cal = Calendar.getInstance()
    val map = linkedMapOf<String, MutableList<Transaction>>()

    for (tx in transactions) {
        cal.timeInMillis = tx.createdAt
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val key = String.format("%04d-%02d", year, month)
        map.getOrPut(key) { mutableListOf() }.add(tx)
    }

    return map.map { (key, txList) ->
        val parts = key.split("-")
        val year = parts[0].toIntOrNull() ?: 2026
        val month = parts[1].toIntOrNull() ?: 1
        val displayName = "${year}年${month}月"

        var incomeTotal = 0
        var expenseTotal = 0
        for (tx in txList) {
            if (tx.type == "income") {
                incomeTotal += tx.amount
            } else {
                expenseTotal += tx.amount
            }
        }

        MonthSummary(
            yearMonthKey = key,
            displayName = displayName,
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            balance = incomeTotal - expenseTotal,
            transactions = txList
        )
    }
}

@Composable
fun MonthlyHistoryView(
    allTransactions: List<Transaction>,
    onTransactionLongClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthSummaries = remember(allTransactions) {
        groupTransactionsByMonth(allTransactions)
    }

    // 初回表示時、最新の月（最初の月）をデフォルトで展開しておく
    var expandedMonths by remember(monthSummaries) {
        mutableStateOf(
            if (monthSummaries.isNotEmpty()) setOf(monthSummaries.first().yearMonthKey)
            else emptySet()
        )
    }

    if (monthSummaries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "履歴データがありません",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(monthSummaries, key = { it.yearMonthKey }) { summary ->
                val isExpanded = expandedMonths.contains(summary.yearMonthKey)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 月ヘッダー行（タップでアコーディオン開閉）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedMonths = if (isExpanded) {
                                        expandedMonths - summary.yearMonthKey
                                    } else {
                                        expandedMonths + summary.yearMonthKey
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = summary.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(${summary.transactions.size}件)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "収入: +${formatCurrency(summary.incomeTotal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF008433),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "支出: -${formatCurrency(summary.expenseTotal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD43C35),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "月間収支",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = (if (summary.balance >= 0) "+" else "") + formatCurrency(summary.balance),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (summary.balance >= 0) Color(0xFF008433) else Color(0xFFD43C35)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = if (isExpanded) "折りたたむ" else "展開する",
                                    tint = Color.Gray
                                )
                            }
                        }

                        // アコーディオン内部: 当該月の全履歴リスト
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF9FAFB))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Divider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(bottom = 4.dp))

                                summary.transactions.forEach { tx ->
                                    SimpleTransactionItem(
                                        tx = tx,
                                        onLongClick = { onTransactionLongClick(tx) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
