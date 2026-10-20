package com.rakib.bushra.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.core.Phase

/** ডার্ক নিয়ন-গ্লাস রঙের প্যালেট (রেফারেন্স ডিজাইনের মতো টিল/নীল) */
object Clay {
    val bg = Color(0xFF031017)
    val bgMid = Color(0xFF062A31)
    val surface = Color(0xFF0B2A33)
    val inset = Color(0xFF061C24)
    val shadow = Color(0xFF010608)
    val glow = Color(0xFF19E3D0)
    val text = Color(0xFFE8FFFC)
    val muted = Color(0xFF86B7B8)
    val onAccent = Color(0xFF03181D)
    val accent = Color(0xFF14D9C6)
    val accent2 = Color(0xFF2D8CFF)
    val ok = Color(0xFF5CF2B0)
    val warn = Color(0xFFFFB86B)
}

fun Color.lighten(f: Float): Color = lerp(this, Color.White, f)

fun Color.darken(f: Float): Color = lerp(this, Color.Black, f)

/** কাচের মতো ঝলমলে উপাদান: গাঢ় গ্রেডিয়েন্ট, টিল আভা, পাতলা জ্বলজ্বলে ধার */
fun Modifier.clay(
    radius: Dp = 28.dp,
    color: Color = Clay.surface,
    depth: Dp = 10.dp
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(depth, shape, clip = false, ambientColor = Clay.glow, spotColor = Clay.glow)
        .background(
            Brush.linearGradient(listOf(color.lighten(0.10f), color, color.darken(0.25f))),
            shape
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(Clay.glow.copy(alpha = 0.60f), Clay.glow.copy(alpha = 0.08f))),
            shape
        )
}

@Composable
fun BushraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Clay.accent,
            background = Clay.bg,
            surface = Clay.surface,
            onSurface = Clay.text
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Clay.text) {
            content()
        }
    }
}

@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clay(26.dp, Clay.surface, 10.dp)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Clay.accent,
    textColor: Color = Clay.onAccent,
    enabled: Boolean = true
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "btn")
    Box(
        modifier = modifier
            .scale(scale)
            .clay(22.dp, color, if (pressed) 4.dp else 10.dp)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ClaySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 26.dp else 2.dp, label = "sw")
    Box(
        Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) Clay.accent.copy(alpha = 0.35f) else Clay.inset)
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(28.dp)
                .clay(14.dp, if (checked) Clay.accent else Clay.muted, 4.dp)
        )
    }
}

@Composable
fun ClayField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false
) {
    Column(modifier) {
        Text(
            label,
            color = Clay.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Clay.inset)
                .border(1.dp, Clay.glow.copy(alpha = 0.25f), shape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = Clay.text, fontSize = 16.sp),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(Clay.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** অবস্থা অনুযায়ী রং ও গতি বদলানো নিয়ন গোলক: রিং, ঝলমলে গোলক এবং দুই পাশে সাউন্ড-বার */
@Composable
fun Orb(phase: Phase, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val speedMs = when (phase) {
        Phase.IDLE -> 2600
        Phase.LISTENING -> 1100
        Phase.THINKING -> 700
        Phase.SPEAKING -> 900
    }
    val pulse by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(speedMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val target = when (phase) {
        Phase.IDLE -> Color(0xFF2D8CFF)
        Phase.LISTENING -> Color(0xFF19E3D0)
        Phase.THINKING -> Color(0xFF8E7CFF)
        Phase.SPEAKING -> Color(0xFF5CF2B0)
    }
    val color by animateColorAsState(target, tween(450), label = "orbColor")
    val amp = when (phase) {
        Phase.IDLE -> 0.02f
        Phase.LISTENING -> 0.05f + (level.coerceIn(0f, 10f) / 10f) * 0.08f
        Phase.THINKING -> 0.08f
        Phase.SPEAKING -> 0.12f
    }
    val barLevel = when (phase) {
        Phase.IDLE -> 0.25f
        Phase.LISTENING -> 0.35f + (level.coerceIn(0f, 10f) / 10f) * 0.65f
        Phase.THINKING -> 0.5f
        Phase.SPEAKING -> 0.8f
    }
    Canvas(modifier) {
        val c = center
        val base = size.minDimension / 3.1f
        val r = base * (1f + amp * pulse)

        // বাইরের জ্বলজ্বলে আভা
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r * 2.1f),
            r * 2.1f, c
        )
        // দুটো রিং: একটা ঘুরন্ত ড্যাশ
        drawCircle(color.copy(alpha = 0.55f), r * 1.38f, c, style = Stroke(width = 2.dp.toPx()))
        drawArc(
            color = color,
            startAngle = spin,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(c.x - r * 1.55f, c.y - r * 1.55f),
            size = androidx.compose.ui.geometry.Size(r * 3.1f, r * 3.1f),
            style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawCircle(color.copy(alpha = 0.25f), r * 1.55f, c, style = Stroke(width = 1.dp.toPx()))

        // গোলক
        drawCircle(
            Brush.radialGradient(
                listOf(color.lighten(0.65f), color, color.darken(0.55f)),
                center = c - Offset(r * 0.32f, r * 0.38f),
                radius = r * 1.5f
            ),
            r, c
        )
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.14f, c - Offset(r * 0.38f, r * 0.42f))

        // দুই পাশে সাউন্ড-বার
        val heights = floatArrayOf(0.5f, 0.9f, 0.65f)
        for (side in intArrayOf(-1, 1)) {
            for ((i, h) in heights.withIndex()) {
                val wob = 0.65f + 0.35f * (if ((i + (if (side > 0) 1 else 0)) % 2 == 0) pulse else 1f - pulse)
                val bh = r * 0.95f * h * barLevel * wob + 6.dp.toPx()
                val x = c.x + side * (base * 1.65f + i * 8.dp.toPx())
                drawRoundRect(
                    color = color.copy(alpha = 0.9f),
                    topLeft = Offset(x - 2.dp.toPx(), c.y - bh / 2f),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), bh),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}
