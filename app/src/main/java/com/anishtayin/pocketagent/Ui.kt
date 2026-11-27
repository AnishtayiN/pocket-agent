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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Muse-style design language: warm paper background, bubble-free assistant
 * text, one cyan->azure brand accent, hairline borders, soft shadows. */
private val PocketColors = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFF1EFE9),
    onSurfaceVariant = Color(0xFF73706A),
    outlineVariant = Color(0xFFE6E3DB)
)

private val AccentBrush = Brush.linearGradient(
    listOf(Color(0xFF22D3EE), Color(0xFF0EA5C6), Color(0xFF2563EB))
)
private val Hairline = Color(0xFFE6E3DB)
private val SoftFill = Color(0xFFF4F4F0)
private val UserBubble = Color(0xFFEAF3F8)

@Composable
fun AgentApp(viewModel: AgentViewModel) {
    MaterialTheme(colorScheme = PocketColors) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var showSettings by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PocketColors.background)
            ) {
                if (showSettings) {
                    SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
                } else {
                    ChatScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
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
                .shadow(8.dp, CircleShape)
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

    val streaming by viewModel.streamingText
    val itemCount = messages.size + if (busy) 1 else 0
    LaunchedEffect(messages.size, busy, processing) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LaunchedEffect(streaming.length / 90) {
        if (busy && itemCount > 0) {
            listState.scrollToItem(itemCount - 1)
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
                val lastAssistantIndex = messages.indexOfLast { it.role == "assistant" }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(messages) { index, msg ->
                        Box(
                            modifier = Modifier.animateItem(
                                placementSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                        ) {
                            MessageRow(
                                msg = msg,
                                agentName = viewModel.agentName.value,
                                isLastAssistant = index == lastAssistantIndex,
                                busy = busy,
                                onRegenerate = { viewModel.regenerate() }
                            )
                        }
                    }
                    if (busy) {
                        item(key = "live") {
                            if (streaming.isNotBlank()) {
                                StreamingBubble(
                                    text = streaming,
                                    reasoning = viewModel.reasoningText.value,
                                    agentName = viewModel.agentName.value
                                )
                            } else {
                                ThinkingBubble(
                                    status = viewModel.statusText.value,
                                    reasoning = viewModel.reasoningText.value
                                )
                            }
                        }
                    }
                }
            }
            if (processing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
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
    Surface(color = Color(0xF2FFFFFF), tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentAvatar(size = 42, iconSize = 22)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
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
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "تنظیمات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.clearChat() }, enabled = !busy) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "پاک کردن گفتگو",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Hairline)
            )
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
        Suggestion("اخبار امروز رو کامل و با منبع بگو", Icons.Filled.Newspaper),
        Suggestion("یه تحقیق کامل درباره سیاه‌چاله‌ها بکن", Icons.Filled.Public)
    )
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AgentAvatar(size = 96, iconSize = 52)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "سلام! من $name‌ام",
            style = MaterialTheme.typography.headlineLarge.merge(
                TextStyle(brush = AccentBrush)
            ),
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "ایجنت شخصی تو روی این گوشی‌ام. قبل از هر جواب واقعاً فکر می‌کنم، برای هر سؤال در وب تحقیق می‌کنم، جوابم را بازبینی می‌کنم و کامل و با منبع تحویل می‌دم. حافظه دارم، عکس می‌سازم، فایل و عکس را پردازش می‌کنم و کد پایتون می‌نویسم و دیباگ‌شده تحویلت می‌دم:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(26.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            suggestions.forEach { s ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .border(1.dp, Hairline, RoundedCornerShape(50))
                        .clickable { viewModel.send(s.text) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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

/** Muse-style thinking state: a shimmering status line (no heavy card),
 *  elapsed seconds, and a soft live preview of the model's reasoning. */
@Composable
private fun ThinkingBubble(status: String, reasoning: String) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val shimmer by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer"
    )
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier
                    .size(19.dp)
                    .alpha(shimmer),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = status.ifBlank { "در حال فکر کردن…" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(0.55f + 0.45f * shimmer)
            )
            if (seconds > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${seconds}ث",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (reasoning.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftFill)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = reasoning.takeLast(600),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Small name header above assistant text — the Muse look. */
@Composable
private fun AssistantHeader(agentName: String, time: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AgentAvatar(size = 26, iconSize = 15)
        Spacer(Modifier.width(8.dp))
        Text(
            text = agentName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        val t = timeLabel(time)
        if (t.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = t,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The answer appearing token-by-token, bubble-free, with a blinking cursor. */
@Composable
private fun StreamingBubble(text: String, reasoning: String, agentName: String) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        AssistantHeader(agentName = agentName, time = 0L)
        Spacer(Modifier.height(7.dp))
        if (reasoning.isNotBlank()) {
            ReasoningSection(reasoning = reasoning, live = true)
            Spacer(Modifier.height(9.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Box(modifier = Modifier.weight(1f)) {
                MessageContent(text)
            }
            Text(
                text = "▌",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(cursorAlpha)
            )
        }
    }
}

/** Collapsible "فرایند فکر" card on a finished answer. */
@Composable
private fun ReasoningSection(reasoning: String, live: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SoftFill)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (live) "در حال فکر کردن…" else "فرایند فکر",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (live) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = reasoning.takeLast(300),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Web sources of an answer: tappable domain chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesSection(sources: String) {
    val context = LocalContext.current
    val urls = sources.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (urls.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "منابع",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            urls.take(8).forEach { url ->
                val host = try {
                    android.net.Uri.parse(url).host ?: url
                } catch (e: Exception) {
                    url
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFEAF3F8))
                        .border(1.dp, Color(0xFFBFE3EE), RoundedCornerShape(50))
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "لینک باز نشد", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = host.removePrefix("www."),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF155E6B)
                    )
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

/** Renders assistant text as real markdown: headings, bold/italic, inline
 *  code, clickable links, bullet & numbered lists, quotes — plus proper
 *  code blocks (``` fences): dark card, language label, copy button. */
@Composable
private fun MessageContent(text: String) {
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1) {
                CodeBlock(part)
            } else if (part.isNotBlank()) {
                MarkdownBlocks(part.trim())
            }
            if (idx < parts.size - 1) Spacer(Modifier.height(6.dp))
        }
    }
}

private fun isBullet(line: String) =
    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")

private fun isNumbered(line: String) = line.matches(Regex("^\\d{1,3}[.)]\\s.*"))

@Composable
private fun HeadingText(text: String, level: Int) {
    val style = when (level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = parseInline(text),
        style = style,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MarkdownBlocks(text: String) {
    val lines = text.lines()
    var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.isBlank() -> i++
                line.startsWith("### ") -> {
                    HeadingText(line.drop(4), 3); i++
                }
                line.startsWith("## ") -> {
                    HeadingText(line.drop(3), 2); i++
                }
                line.startsWith("# ") -> {
                    HeadingText(line.drop(2), 1); i++
                }
                line == "---" || line == "***" || line == "___" -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Hairline)
                    )
                    i++
                }
                isBullet(line) || isNumbered(line) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size &&
                        (isBullet(lines[i].trim()) || isNumbered(lines[i].trim()))
                    ) {
                        items.add(lines[i].trim())
                        i++
                    }
                    items.forEach { item ->
                        if (isBullet(item)) {
                            Row {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = parseInline(item.drop(2).trim()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            val marker = item.takeWhile { it.isDigit() || it == '.' || it == ')' }
                            Row {
                                Text(
                                    text = marker,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = parseInline(item.drop(marker.length).trim()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                line.startsWith("> ") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SoftFill)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = parseInline(line.drop(2)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    i++
                }
                else -> {
                    val para = StringBuilder(line)
                    i++
                    while (i < lines.size) {
                        val next = lines[i].trim()
                        if (next.isBlank() || next.startsWith("#") || isBullet(next) ||
                            isNumbered(next) || next.startsWith("> ") ||
                            next == "---" || next == "***"
                        ) break
                        para.append(' ').append(next)
                        i++
                    }
                    Text(
                        text = parseInline(para.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Inline markdown: **bold**, *italic*, `code`, [label](url) clickable links. */
private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(
        SpanStyle(
            color = Color(0xFF0369A1),
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Bold
        )
    )
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFFEFEEE9)
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append('`')
                    i++
                }
            }
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                val closeParen = if (closeBracket > i && closeBracket + 1 < text.length &&
                    text[closeBracket + 1] == '('
                ) {
                    text.indexOf(')', closeBracket + 2)
                } else {
                    -1
                }
                if (closeBracket > i && closeParen > closeBracket) {
                    val label = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    val start = length
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF0369A1),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                    addLink(LinkAnnotation.Url(url, linkStyles), start, length)
                    i = closeParen + 1
                } else {
                    append('[')
                    i++
                }
            }
            text[i] == '*' || text[i] == '_' -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(marker)
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
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

private fun toolIcon(name: String?): ImageVector = when (name) {
    "web_search" -> Icons.Filled.Search
    "web_fetch" -> Icons.Filled.Language
    "web_research" -> Icons.Filled.Public
    "verify" -> Icons.Filled.Psychology
    "generate_image" -> Icons.Filled.Image
    "write_file" -> Icons.Filled.Code
    "read_file", "process_file" -> Icons.Filled.Description
    "run_python", "run_python_file" -> Icons.Filled.PlayArrow
    "get_battery_info" -> Icons.Filled.BatteryFull
    "get_current_time" -> Icons.Filled.Schedule
    "save_memory", "read_memory", "delete_memory" -> Icons.Filled.Memory
    else -> Icons.Filled.Build
}

/** Slim activity line for one tool call: spinner while running, green check
 *  when done, with the query / file name / URL as detail. */
@Composable
private fun ToolCard(msg: ChatMsg) {
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SoftFill)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            toolIcon(msg.toolName),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = msg.toolLabel ?: "ابزار",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            val detail = msg.toolDetail
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (msg.toolDone) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF10B981)
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MessageRow(
    msg: ChatMsg,
    agentName: String,
    isLastAssistant: Boolean = false,
    busy: Boolean = false,
    onRegenerate: () -> Unit = {}
) {
    when (msg.role) {
        "tool-ui" -> ToolCard(msg)
        "user" -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(UserBubble)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        val path = msg.imagePath
                        if (path != null) {
                            AttachedImage(path)
                            if (msg.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                        }
                        if (msg.content.isNotBlank()) {
                            Text(
                                text = msg.content,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
        else -> {
            // Assistant: bubble-free plain text with a small name header,
            // exactly the Muse layout.
            Column(modifier = Modifier.fillMaxWidth()) {
                AssistantHeader(agentName = agentName, time = msg.time)
                Spacer(Modifier.height(7.dp))
                val reasoning = msg.reasoning
                if (!reasoning.isNullOrBlank()) {
                    ReasoningSection(reasoning)
                    if (msg.content.isNotBlank()) Spacer(Modifier.height(9.dp))
                }
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
                val sources = msg.sources
                if (!sources.isNullOrBlank()) {
                    Spacer(Modifier.height(11.dp))
                    SourcesSection(sources)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val clipboard = LocalClipboardManager.current
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(msg.content))
                            Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "کپی پاسخ",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isLastAssistant && !busy) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "تولید دوباره پاسخ",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Floating composer, Muse style: one rounded bar, attach inside,
 *  circular send that turns into a red stop while the agent works. */
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(28.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = !busy && !processing,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "پیوست",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("به ایجنتت یه پیام بده…") },
                maxLines = 5,
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            busy -> Brush.linearGradient(
                                listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                            )
                            input.isNotBlank() -> AccentBrush
                            else -> Brush.linearGradient(
                                listOf(Color(0xFFD8D5CC), Color(0xFFD8D5CC))
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    IconButton(onClick = { viewModel.stop() }) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "توقف",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { submit() },
                        enabled = input.isNotBlank() && !processing
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسال",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
            Surface(color = Color(0xF2FFFFFF), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                Column {
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Hairline)
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
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
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFDC2626)
                        )
                    ) { Text("پاک کردن کل حافظه") }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "ایجنت جیبی — نسخه ۶٫۰٫۰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
