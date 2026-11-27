package com.anishtayin.pocketagent

import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PocketColors = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F6FB),
    onBackground = Color(0xFF0A1628),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A1628),
    surfaceVariant = Color(0xFFE7EEF7),
    onSurfaceVariant = Color(0xFF54677F),
    outlineVariant = Color(0xFFD5E0EE)
)

/** One accent family only: cyan -> azure. */
private val AccentBrush = Brush.linearGradient(
    listOf(Color(0xFF22D3EE), Color(0xFF0EA5C6), Color(0xFF2563EB))
)
private val UserBrush = Brush.horizontalGradient(
    listOf(Color(0xFF0891B2), Color(0xFF2563EB))
)

@Composable
fun AgentApp(viewModel: AgentViewModel) {
    MaterialTheme(colorScheme = PocketColors) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var showSettings by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
                AmbientBackground()
                if (showSettings) {
                    SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
                } else {
                    ChatScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}

/** Slow-drifting ambient light orbs — soft depth on the light background. */
@Composable
private fun AmbientBackground() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000), RepeatMode.Reverse),
        label = "drift"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6FB))
    ) {
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = (-70 + drift * 46).dp, y = (-110).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x3822D3EE), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (120 - drift * 52).dp, y = (130).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x2A60A5FA), Color.Transparent)
                    )
                )
        )
    }
}

/** Avatar with a slowly rotating sweep-gradient ring. */
@Composable
private fun AgentAvatar(size: Int, iconSize: Int) {
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Restart),
        label = "angle"
    )
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle }
        ) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Color(0xFF0EA5C6), Color.Transparent)
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Box(
            modifier = Modifier
                .size((size - 7).dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(AccentBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(iconSize.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ChatScreen(viewModel: AgentViewModel, onOpenSettings: () -> Unit) {
    val messages = viewModel.messages
    val busy by viewModel.isBusy
    val processing by viewModel.isProcessing
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy, processing) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { HeaderBar(viewModel, onOpenSettings) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty() && !processing) {
                EmptyState(
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(messages) { msg ->
                        Box(
                            modifier = Modifier.animateItem(
                                placementSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                        ) { MessageRow(msg) }
                    }
                    if (busy) {
                        item { TypingBubble(viewModel.statusText.value) }
                    }
                }
            }
            if (processing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = viewModel.processingText.value.ifBlank { "در حال پردازش…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            InputBar(viewModel = viewModel)
        }
    }
}

@Composable
private fun HeaderBar(viewModel: AgentViewModel, onOpenSettings: () -> Unit) {
    val busy by viewModel.isBusy
    val status by viewModel.statusText
    val name by viewModel.agentName
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )
    Surface(color = Color(0xE6FFFFFF), tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentAvatar(size = 46, iconSize = 24)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (busy) dotAlpha else 1f)
                            .clip(CircleShape)
                            .background(if (busy) Color(0xFFF59E0B) else Color(0xFF10B981))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (busy) status.ifBlank { "در حال کار…" } else "آنلاین — ایجنت شخصی تو",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
            }
            IconButton(onClick = { viewModel.clearChat() }, enabled = !busy) {
                Icon(Icons.Filled.Delete, contentDescription = "پاک کردن گفتگو")
            }
        }
    }
}

private data class Suggestion(val text: String, val icon: ImageVector)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(viewModel: AgentViewModel, modifier: Modifier = Modifier) {
    val name by viewModel.agentName
    val suggestions = listOf(
        Suggestion("شارژ گوشیم چنده؟", Icons.Filled.BatteryFull),
        Suggestion("یه عکس از غروب کرمانشاه بساز", Icons.Filled.Image),
        Suggestion("یه کد پایتون بنویس که عدد اول رو پیدا کنه", Icons.Filled.Code),
        Suggestion("اخبار امروز چیه؟", Icons.Filled.Newspaper),
        Suggestion("ساعت و تاریخ امروز رو بگو", Icons.Filled.Schedule)
    )
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AgentAvatar(size = 104, iconSize = 56)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "سلام! من $name‌ام",
            style = MaterialTheme.typography.headlineMedium.merge(
                TextStyle(brush = AccentBrush)
            ),
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "ایجنت شخصی تو روی این گوشی‌ام. حافظه دارم، سرچ می‌کنم، عکس می‌سازم، فایل و عکس رو پردازش می‌کنم و کد پایتون می‌نویسم، اجراش می‌کنم و دیباگ‌شده تحویلت می‌دم:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            suggestions.forEach { s ->
                Row(
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .clickable { viewModel.send(s.text) }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        s.icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(text = s.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(status: String) {
    val transition = rememberInfiniteTransition(label = "typing")
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .shadow(3.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                }
                for (i in 0..2) {
                    val a by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(550, delayMillis = i * 160),
                            RepeatMode.Reverse
                        ),
                        label = "dot$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(a)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    if (i < 2) Spacer(Modifier.width(5.dp))
                }
            }
        }
    }
}

private fun timeLabel(ms: Long): String =
    if (ms > 0) SimpleDateFormat("HH:mm", Locale.US).format(Date(ms)) else ""

@Composable
private fun AttachedImage(path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "تصویر پیوست",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

/** Renders assistant text with proper code blocks (``` fences): dark card,
 *  language label, copy button, monospace, horizontal scroll. */
@Composable
private fun MessageContent(text: String) {
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1) {
                CodeBlock(part)
            } else if (part.isNotBlank()) {
                Text(text = part.trim(), color = MaterialTheme.colorScheme.onSurface)
            }
            if (idx < parts.size - 1) Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CodeBlock(raw: String) {
    val trimmed = raw.trim('\n')
    val lines = trimmed.lines()
    val first = lines.firstOrNull()?.trim().orEmpty()
    val isLang = first.isNotEmpty() && first.length <= 12 &&
        first.none { it.isWhitespace() } && lines.size > 1
    val lang = if (isLang) first else "code"
    val code = if (isLang) lines.drop(1).joinToString("\n") else trimmed
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B132B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lang,
                color = Color(0xFF7DD3FC),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "کپی کد",
                    tint = Color(0xFFB9C6DC),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = code,
                color = Color(0xFFE2E8F0),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** A file the agent delivered: icon, name, size, open & share actions. */
@Composable
private fun FileCard(path: String) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    if (!file.exists()) return
    val ext = file.extension.lowercase()
    val icon = when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Filled.Image
        "py", "html", "htm", "kt", "kts", "js", "ts", "css", "json", "md", "txt",
        "java", "c", "cpp", "h", "cs", "go", "rs", "php", "rb", "swift", "sh",
        "yaml", "yml", "sql", "dart" -> Icons.Filled.Code
        else -> Icons.Filled.Description
    }
    fun fileUri() = FileProvider.getUriForFile(
        context, context.packageName + ".fileprovider", file
    )
    fun mime() = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    fun openFile() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri(), mime())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل نیست", Toast.LENGTH_SHORT).show()
        }
    }
    fun shareFile() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime()
                putExtra(Intent.EXTRA_STREAM, fileUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل"))
        } catch (e: Exception) {
            Toast.makeText(context, "اشتراک‌گذاری ناموفق بود", Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF0F7FA))
            .border(1.dp, Color(0xFFBFE3EE), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "فایل آماده است — ${file.length() / 1024} کیلوبایت",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row {
            TextButton(onClick = { openFile() }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("باز کردن")
            }
            TextButton(onClick = { shareFile() }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("اشتراک‌گذاری")
            }
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMsg) {
    when (msg.role) {
        "tool-ui" -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFE0F5FA))
                        .border(1.dp, Color(0xFF9ADBE8), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = msg.toolLabel ?: "ابزار",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF155E6B)
                    )
                }
            }
        }
        "user" -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .shadow(5.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(UserBrush)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        val path = msg.imagePath
                        if (path != null) {
                            AttachedImage(path)
                            if (msg.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                        }
                        if (msg.content.isNotBlank()) {
                            Text(text = msg.content, color = Color.White)
                        }
                        val t = timeLabel(msg.time)
                        if (t.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = t,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xB3FFFFFF),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 330.dp)
                        .shadow(3.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        if (msg.content.isNotBlank()) {
                            MessageContent(msg.content)
                        }
                        val path = msg.imagePath
                        if (path != null) {
                            if (msg.content.isNotBlank()) Spacer(Modifier.height(9.dp))
                            AttachedImage(path)
                        }
                        val delivered = msg.filePath
                        if (delivered != null) {
                            Spacer(Modifier.height(9.dp))
                            FileCard(delivered)
                        }
                        val t = timeLabel(msg.time)
                        if (t.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = t,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(viewModel: AgentViewModel) {
    val busy by viewModel.isBusy
    val processing by viewModel.isProcessing
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.attachImage(uri) }

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.attachDocument(uri) }

    fun submit() {
        if (input.isNotBlank() && !busy && !processing) {
            viewModel.send(input)
            input = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box {
            IconButton(
                onClick = { menuOpen = true },
                enabled = !busy && !processing,
                modifier = Modifier
                    .size(50.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = "پیوست")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("پیوست عکس") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("پیوست فایل (کد، PDF، متن…)") },
                    leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        docPicker.launch(arrayOf("*/*"))
                    }
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(4.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("به ایجنتت یه پیام بده…") },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
        }
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(7.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (input.isNotBlank() && !busy) AccentBrush
                    else Brush.linearGradient(listOf(Color(0xFFCBD8E8), Color(0xFFCBD8E8)))
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = { submit() }, enabled = input.isNotBlank() && !busy && !processing) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "ارسال",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: AgentViewModel, onBack: () -> Unit) {
    var nameField by remember { mutableStateOf(viewModel.agentName.value) }
    val memories = viewModel.memoryEntries.toList()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = Color(0xE6FFFFFF), tonalElevation = 3.dp, shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                    Text(
                        text = "تنظیمات ایجنت",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(size = 58, iconSize = 30)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "هویت ایجنت",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم ایجنت") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.setAgentName(nameField)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ذخیره اسم") }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "حافظه بلندمدت (${memories.size} مورد)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "چیزهایی که ایجنت درباره تو یاد گرفته و بین گفتگوها یادش می‌ماند:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (memories.isEmpty()) {
                item {
                    Text(
                        text = "هنوز چیزی در حافظه نیست. هرچه بیشتر حرف بزنی، بیشتر یاد می‌گیره.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(memories) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { viewModel.deleteMemoryAt(index) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "حذف از حافظه",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = { viewModel.clearMemory() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                    ) { Text("پاک کردن کل حافظه") }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "ایجنت جیبی — نسخه ۴٫۰٫۰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
