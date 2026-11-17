package `in`.vidyadhan.academy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.students.asDate
import java.time.Instant
import java.time.ZoneOffset

val HeaderBrush = Brush.horizontalGradient(listOf(Brand.Blue900, Brand.Blue700, Brand.Blue500))

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().background(HeaderBrush).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(36.dp).clip(CircleShape).background(Color.White).padding(2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Blue100)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun SubTop(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, color: Color = Brand.Blue700) {
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.12f)), Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

@Composable fun GoodPill(t: String) = Pill(t, Brand.Green100, Brand.Green700)
@Composable fun BadPill(t: String) = Pill(t, Brand.Red100, Brand.Red700)
@Composable fun WarnPill(t: String) = Pill(t, Brand.Saffron100, Brand.Saffron800)
@Composable fun InfoPill(t: String) = Pill(t, Brand.Blue100, Brand.Blue800)

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Brand.Slate800)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Brand.Blue800)

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Brand.Blue50), Alignment.Center) { Icon(icon, null, Modifier.size(40.dp), tint = Brand.Blue500) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(hint, color = Brand.Slate500, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NumberField(label: String, value: String, maxDigits: Int, onChange: (String) -> Unit, modifier: Modifier = Modifier, phone: Boolean = false) {
    OutlinedTextField(value, { if (it.length <= maxDigits && it.all(Char::isDigit)) onChange(it) }, modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Number))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(epochDay.asDate(), {}, Modifier.fillMaxWidth().clickable { open = true }, readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton({ open = true }) { Icon(Icons.Filled.CalendarMonth, "Pick date") } })
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipChoice(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected == o, { onSelect(o) }, { Text(o) }) }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Boolean = false) {
    Button(onClick, modifier, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }
}
