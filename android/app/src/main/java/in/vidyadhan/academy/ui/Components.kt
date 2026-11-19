package `in`.vidyadhan.academy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.students.asDate
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.rounded.Send

// ───────────────────────── backgrounds ─────────────────────────

/** Deep academy teal with a warm marigold glow top-right and a green glow bottom-left: behind every page header. */
fun Modifier.heroBackground(): Modifier = drawBehind {
    val a = Math.toRadians(155.0)
    val dx = Math.sin(a).toFloat(); val dy = -Math.cos(a).toFloat()
    val half = (kotlin.math.abs(size.width * dx) + kotlin.math.abs(size.height * dy)) / 2
    val c = Offset(size.width / 2, size.height / 2)
    drawRect(Brush.linearGradient(0f to Color(0xFF093541), 0.6f to Brand.Blue700, 1f to Color(0xFF145E70),
        start = Offset(c.x - dx * half, c.y - dy * half), end = Offset(c.x + dx * half, c.y + dy * half)))
    drawRect(Brush.radialGradient(listOf(Color(0x3DE07A2B), Color.Transparent), Offset(size.width * 0.92f, size.height * 0.08f), size.width * 0.6f))
    drawRect(Brush.radialGradient(listOf(Color(0x2E16A34A), Color.Transparent), Offset(size.width * 0.08f, size.height * 0.85f), size.width * 0.6f))
}

/** Sub-screens (forms, detail pages) hide the bottom navigation while they are open. */
val LocalNavHider = staticCompositionLocalOf<MutableIntState?> { null }

@Composable
fun HideBottomNav() {
    val hider = LocalNavHider.current
    DisposableEffect(hider) { hider?.let { it.intValue++ }; onDispose { hider?.let { it.intValue-- } } }
}

/** Full-screen variant for the lock screen: decoration stays clear of the keypad. */
fun Modifier.lockBackground(): Modifier = drawBehind {
    drawRect(Brush.linearGradient(listOf(Brand.Blue950, Brand.Blue800, Brand.Blue600), Offset.Zero, Offset(size.width, size.height)))
    drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.55f, center = Offset(size.width * 1.0f, 0f))
    drawCircle(Brand.Blue400.copy(alpha = 0.10f), radius = size.width * 0.22f, center = Offset(0f, size.height * 0.36f))
}

val AccentBrush = Brush.linearGradient(listOf(Brand.Saffron500, Brand.Saffron700))
val PrimaryBrush = Brush.linearGradient(listOf(Brand.Blue500, Brand.Blue800))

/** A text size that grows with the phone's text setting only up to [cap] times (for small fixed-width labels). */
@Composable
fun cappedSp(base: Float, cap: Float = 1.15f): androidx.compose.ui.unit.TextUnit {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    return (base * minOf(fs, cap) / fs).sp
}

/** Gentle press-down effect for anything tappable. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Tappable card: a thin border that turns teal while pressed (no dark ripple), and a small press-down. */
@Composable
fun Modifier.pressCard(shape: Shape, onClick: () -> Unit, normal: Color = Brand.Line, active: Color = Brand.Blue700): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "press")
    val edge by animateColorAsState(if (pressed) active else normal, tween(120), label = "edge")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }.border(if (pressed) 1.5.dp else 1.dp, edge, shape)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** White card with a soft blue-tinted shadow. */
fun Modifier.softCard(shape: Shape = RoundedCornerShape(22.dp), elevation: Dp = 10.dp): Modifier =
    this.shadow(elevation, shape, ambientColor = Brand.Blue900.copy(alpha = 0.10f), spotColor = Brand.Blue900.copy(alpha = 0.12f))
        .background(Color.White, shape)
        .border(1.dp, Brand.Line, shape)

// ───────────────────────── page scaffolding ─────────────────────────

/**
 * Standard page: gradient header (behind the status bar) and a rounded light sheet that overlaps it.
 */
@Composable
fun HeroPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        Column(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).heroBackground().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Blue100.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Column { header() }
        }
        Column(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = Color.White) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape).clickable(onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Translucent chip shown on the gradient header. */
@Composable
fun GlassStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.11f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Blue200, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = valueColor, maxLines = 1)
    }
}

// ───────────────────────── identity ─────────────────────────

private val avatarPalettes = listOf(
    Brand.Blue400 to Brand.Blue800,
    Brand.Saffron400 to Brand.Saffron700,
    Brand.Teal500 to Brand.Teal700,
    Brand.Violet500 to Brand.Violet700,
    Color(0xFFFB7185) to Color(0xFFBE123C),
    Brand.Green500 to Brand.Green700,
    Color(0xFFFBBF24) to Brand.Amber700,
)

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

@Composable
fun Avatar(name: String, size: Dp = 46.dp, ring: Boolean = false, photo: String = "") {
    val (a, b) = avatarPalettes[name.hashCode().absoluteValue % avatarPalettes.size]
    val img = rememberPhoto(photo, (size.value * 3).toInt())
    Box(
        Modifier.size(size).let { if (ring) it.border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape) else it }
            .padding(if (ring) 3.dp else 0.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))),
        Alignment.Center,
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(initials(name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
fun LogoBadge(size: Dp = 44.dp) {
    Box(Modifier.size(size).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White).padding(size * 0.08f), Alignment.Center) {
        Image(painterResource(R.drawable.logo), null, Modifier.fillMaxSize().clip(CircleShape))
    }
}

// ───────────────────────── chips ─────────────────────────

enum class Tone(val bg: Color, val fg: Color) {
    Good(Brand.Green50, Brand.Green700),
    Bad(Brand.Red50, Brand.Red700),
    Warn(Brand.Amber50, Brand.Amber700),
    Info(Brand.Blue50, Brand.Blue700),
    Neutral(Brand.Slate50, Brand.Slate600),
    Accent(Brand.Saffron50, Brand.Saffron700),
}

@Composable
fun StatusChip(text: String, tone: Tone, dot: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(tone.bg).border(1.dp, tone.fg.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) { Box(Modifier.size(6.dp).clip(CircleShape).background(tone.fg)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = tone.fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun SmallTag(text: String, color: Color = Brand.Blue700) {
    Text(
        text, Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.09f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1,
    )
}

/** Pill-shaped segmented switch. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(50)).padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            val bg by animateColorAsState(if (sel) Brand.Blue700 else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (sel) Color.White else Brand.Slate500, label = "segfg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(key) }.padding(vertical = 10.dp),
                Alignment.Center,
            ) { Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun ChoicePills(options: List<String>, selected: String, onSelect: (String) -> Unit, captions: Map<String, String> = emptyMap()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) Brand.Blue700 else Color.White, label = "pill")
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(bg)
                    .border(1.dp, if (sel) Brand.Blue700 else Brand.Slate200, RoundedCornerShape(16.dp))
                    .clickable { onSelect(o) }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(o, color = if (sel) Color.White else Brand.Slate800, style = MaterialTheme.typography.labelLarge)
                captions[o]?.let { Text(it, color = if (sel) Brand.Blue100 else Brand.Slate400, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ───────────────────────── cards & sections ─────────────────────────

@Composable
fun ListCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 6.dp).clip(RoundedCornerShape(22.dp))
            .let { if (onClick != null) it.pressable(onClick = onClick) else it }.padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
        if (action != null) Text(action, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            color = Brand.Blue600, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 42.dp, filled: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(if (filled) Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color)) else Brush.linearGradient(listOf(color.copy(alpha = 0.10f), color.copy(alpha = 0.16f)))),
        Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) Color.White else color, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
fun MetricCard(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier, sub: String? = null, subColor: Color = color, onClick: (() -> Unit)? = null) {
    Column(
        modifier.softCard(RoundedCornerShape(24.dp), 8.dp).clip(RoundedCornerShape(24.dp))
            .let { if (onClick != null) it.pressable(onClick = onClick) else it }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color, 40.dp, filled = true)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Brand.Slate300)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
        if (sub != null) { Spacer(Modifier.height(6.dp)); SmallTag(sub, subColor) }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, Brand.Blue600, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
            Text(value, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(124.dp).clip(CircleShape).background(Brand.Blue50), Alignment.Center) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(Brand.Blue100), Alignment.Center) {
                Box(Modifier.size(62.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(PrimaryBrush), Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); PrimaryButton(action, onAction, icon = Icons.Rounded.Add) }
    }
}

/** Circular progress with rounded ends. */
@Composable
fun ProgressRing(fraction: Float, size: Dp, stroke: Dp, color: Color, track: Color, content: @Composable BoxScope.() -> Unit = {}) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(Modifier.size(size), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = Stroke(stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = st)
            if (f > 0f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = st)
        }
        content()
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Brand.Green500, track: Color = Brand.Slate200, height: Dp = 6.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

// ───────────────────────── buttons ─────────────────────────

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Boolean = false, icon: ImageVector? = null, custom: Brush? = null) {
    val brush = if (!enabled) Brush.linearGradient(listOf(Brand.Slate300, Brand.Slate300)) else custom ?: if (accent) AccentBrush else PrimaryBrush
    Row(
        modifier.height(54.dp).shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(18.dp), spotColor = (if (accent) Brand.Saffron700 else Brand.Blue800).copy(alpha = 0.35f))
            .clip(RoundedCornerShape(18.dp)).background(brush).pressable(enabled, onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Brand.Blue700, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = if (enabled) 0.09f else 0.04f))
            .pressable(enabled, onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon with a caption underneath (Call, WhatsApp, quick actions…). */
@Composable
fun RoundAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit, glass: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
                .background(if (glass) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.10f))) else Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color)))
                .let { if (glass) it.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)) else it },
            Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (glass) Color.White else Brand.Slate600, maxLines = 1)
    }
}

/** Sticky action bar at the bottom of forms. */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Brand.Blue900.copy(alpha = 0.2f))
            .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).navigationBarsPadding().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

// ───────────────────────── form fields ─────────────────────────

@Composable
fun FormCard(title: String, icon: ImageVector, color: Color = Brand.Blue600, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color, 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
        }
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White, unfocusedContainerColor = Brand.Slate50, disabledContainerColor = Brand.Slate50,
    focusedBorderColor = Brand.Blue500, unfocusedBorderColor = Brand.Line,
    focusedLeadingIconColor = Brand.Blue600, unfocusedLeadingIconColor = Brand.Slate400,
    focusedLabelColor = Brand.Blue600, unfocusedLabelColor = Brand.Slate400,
)

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1, prefix: String? = null,
    supporting: String? = null, isError: Boolean = false, readOnly: Boolean = false, trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines, readOnly = readOnly,
        leadingIcon = icon?.let { { Icon(it, null) } }, trailingIcon = trailing,
        prefix = prefix?.let { { Text(it, color = Brand.Slate500) } }, isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
    )
}

@Composable
fun DigitsField(label: String, value: String, maxDigits: Int, onChange: (String) -> Unit, icon: ImageVector? = null, prefix: String? = null,
                phone: Boolean = false, supporting: String? = null, isError: Boolean = false, modifier: Modifier = Modifier) =
    Field(value, { if (it.length <= maxDigits && it.all(Char::isDigit)) onChange(it) }, label, icon, modifier,
        if (phone) KeyboardType.Phone else KeyboardType.Number, prefix = prefix, supporting = supporting, isError = isError)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Field(epochDay.asDate(), {}, label, Icons.Rounded.CalendarMonth, readOnly = true,
            trailing = { Icon(Icons.Rounded.ExpandMore, null, tint = Brand.Slate400) })
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable { open = true })
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }; open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Brand.Slate800)
        Row(Modifier.clip(RoundedCornerShape(50)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).clickable { if (value > range.first) onChange(value - 1) }, Alignment.Center) { Icon(Icons.Rounded.Remove, "Less", tint = Brand.Blue700) }
            Text("$value$suffix", Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.titleSmall, color = Brand.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(Modifier.size(38.dp).clip(CircleShape).clickable { if (value < range.last) onChange(value + 1) }, Alignment.Center) { Icon(Icons.Rounded.Add, "More", tint = Brand.Blue700) }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Brand.Slate800)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Slate400)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Blue600, uncheckedBorderColor = Brand.Slate300, uncheckedThumbColor = Brand.Slate400, uncheckedTrackColor = Brand.Slate50))
    }
}

/** Row used in sheets to pick one option. */
@Composable
fun OptionRow(icon: ImageVector, color: Color, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Brand.Line, RoundedCornerShape(18.dp)).pressable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color, 44.dp, filled = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Brand.Slate300)
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Brand.Ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
    }
}

/** Confirmation dialog in the app's style. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        icon = { IconTile(if (danger) Icons.Rounded.DeleteOutline else Icons.Rounded.Info, if (danger) Brand.Red600 else Brand.Blue600, 52.dp) },
        title = { Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = if (danger) Brand.Red600 else Brand.Blue700, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Brand.Slate500) } },
    )
}

// ───────────────────────── extras ─────────────────────────

/** Lets the light sheet slide up under the header while still reaching the bottom of the screen. */
fun Modifier.overlapUp(by: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = by.roundToPx()
    val c = if (constraints.hasBoundedHeight) constraints.copy(minHeight = constraints.minHeight + extra, maxHeight = constraints.maxHeight + extra) else constraints
    val p = measurable.measure(c)
    layout(p.width, (p.height - extra).coerceAtLeast(0)) { p.place(0, -extra) }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Brand.Ink),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(14.dp))
                .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Brand.Slate400, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (value.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable { onChange("") }, Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Clear", tint = Brand.Slate400, modifier = Modifier.size(18.dp))
                }
            }
        })
}

@Composable
fun Fab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(56.dp).shadow(14.dp, RoundedCornerShape(20.dp), spotColor = Brand.Saffron700.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(20.dp)).background(AccentBrush).pressable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, color: Color = Brand.Blue700, icon: ImageVector? = null) {
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(color).pressable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SmallIconButton(icon: ImageVector, desc: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.10f)).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = color, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun LegendLine(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
    }
}

@Composable
fun GlassChip(text: String, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.13f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Brand.Blue100, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

/** Speech-bubble preview of a message before it is sent. */
@Composable
fun MessageBubble(text: String, marathi: Boolean) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
            .background(Color(0xFFE7FBD9)).border(1.dp, Color(0xFFCFEFBA), RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
            .padding(16.dp),
    ) {
        Text(text, style = if (marathi) MaterialTheme.typography.bodyLarge.copy(fontFamily = Mukta, fontSize = 17.sp, lineHeight = 25.sp) else MaterialTheme.typography.bodyMedium, color = Brand.Ink)
    }
}

/** Header used on detail pages: big avatar, name, chips and round actions on the gradient. */
@Composable
fun ProfileHero(
    name: String, onBack: () -> Unit, chips: @Composable RowScope.() -> Unit, actions: @Composable RowScope.() -> Unit,
    topActions: @Composable RowScope.() -> Unit = {}, badge: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 46.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = topActions)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (badge != null) badge() else Avatar(name, 88.dp, ring = true)
            Spacer(Modifier.height(10.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = chips)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, content = actions)
        }
    }
}

/** Light sheet that overlaps a hero. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().overlapUp(28.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Brand.Bg), content = content)
}

// ───────────────────────── photos, notes, dates ─────────────────────────

/** Round photo with a small camera badge, used at the top of forms. */
@Composable
fun PhotoAvatarEdit(name: String, photo: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        Box(Modifier.clip(CircleShape).clickable(onClick = onClick)) {
            Avatar(name.ifBlank { "+" }, 96.dp, photo = photo)
            Box(Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(AccentBrush), Alignment.Center) {
                Icon(Icons.Rounded.PhotoCamera, "Photo", tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
    }
}

/** Small card showing a document photo (e.g. Aadhaar), or a button to add one. */
@Composable
fun DocPhoto(label: String, photo: String, onAdd: () -> Unit, onView: () -> Unit) {
    val img = rememberPhoto(photo, 500)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(16.dp))
            .clickable { if (photo.isBlank()) onAdd() else onView() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)).background(Brand.Blue50), Alignment.Center) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Rounded.AddAPhoto, null, tint = Brand.Blue600)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
            Text(if (photo.isBlank()) "Tap to take or choose a photo" else "Tap to view", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        if (photo.isNotBlank()) SmallIconButton(Icons.Rounded.Edit, "Change", Brand.Blue600, onAdd)
    }
}

/** Date that may be left empty (e.g. date of birth). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalDateField(label: String, epochDay: Long?, onChange: (Long?) -> Unit, icon: ImageVector = Icons.Rounded.Cake) {
    var open by remember { mutableStateOf(false) }
    Box {
        Field(epochDay?.asDate() ?: "", {}, label, icon, readOnly = true,
            trailing = { if (epochDay != null) IconButton({ onChange(null) }) { Icon(Icons.Rounded.Close, "Clear", tint = Brand.Slate400) } else Icon(Icons.Rounded.ExpandMore, null, tint = Brand.Slate400) })
        Box(Modifier.matchParentSize().padding(end = 48.dp).clip(RoundedCornerShape(16.dp)).clickable { open = true })
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (epochDay ?: java.time.LocalDate.now().minusYears(20).toEpochDay()) * 86_400_000L,
            initialDisplayMode = DisplayMode.Input)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }; open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

/** Cash / UPI / Bank choice. */
@Composable
fun ModeChoice(mode: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("Cash", Icons.Rounded.Payments, Brand.Green600), Triple("UPI", Icons.Rounded.QrCode2, Brand.Violet700), Triple("Bank", Icons.Rounded.AccountBalance, Brand.Blue600)).forEach { (m, ic, c) ->
            val sel = m == mode
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (sel) c.copy(alpha = 0.10f) else Color.White)
                    .border(if (sel) 2.dp else 1.dp, if (sel) c else Brand.Line, RoundedCornerShape(18.dp)).clickable { onChange(m) }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(ic, null, tint = c)
                Spacer(Modifier.height(4.dp))
                Text(m, style = MaterialTheme.typography.labelLarge, color = if (sel) c else Brand.Slate600)
            }
        }
    }
}

/** Dated notes for a student, member or enquiry. */
@Composable
fun NotesSection(kind: String, ownerId: Long) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { `in`.vidyadhan.academy.data.AppDb.get(ctx).notes() }
    val notes by remember(kind, ownerId) { dao.notes(kind, ownerId) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    SectionHeader("Notes", action = if (notes.isNotEmpty()) "${notes.size}" else null)
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(text, { text = it }, "Add a note, e.g. promised to pay on 5th", modifier = Modifier.weight(1f), singleLine = false)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (text.isBlank()) Brush.linearGradient(listOf(Brand.Slate300, Brand.Slate300)) else PrimaryBrush)
                .clickable(enabled = text.isNotBlank()) {
                    val t = text.trim(); text = ""
                    scope.launch { dao.insert(`in`.vidyadhan.academy.data.Note(kind = kind, ownerId = ownerId, text = t, createdAt = System.currentTimeMillis())) }
                }, Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.Send, "Add note", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        notes.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Amber50).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(n.text, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate800)
                    Spacer(Modifier.height(4.dp))
                    Text(java.time.Instant.ofEpochMilli(n.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")),
                        style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
                }
                IconButton({ scope.launch { dao.delete(n) } }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Delete note", tint = Brand.Slate400, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
