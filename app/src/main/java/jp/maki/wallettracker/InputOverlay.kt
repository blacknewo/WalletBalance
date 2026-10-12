package jp.maki.wallettracker

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
        var dragOffsetY by remember(visible) { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
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
