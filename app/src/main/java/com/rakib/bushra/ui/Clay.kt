package com.rakib.bushra.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.core.Phase

/** ক্লেমরফিজম রঙের প্যালেট */
object Clay {
    val bg = Color(0xFFE7E1F7)
    val surface = Color(0xFFEFEAFB)
    val inset = Color(0xFFDDD6F0)
    val shadow = Color(0xFFB4A8D8)
    val text = Color(0xFF3B3355)
    val muted = Color(0xFF7C739A)
    val accent = Color(0xFFFF8FB1)
    val accent2 = Color(0xFF8E7CFF)
    val ok = Color(0xFF5CC8A0)
    val warn = Color(0xFFFFB86B)
}

fun Color.lighten(f: Float): Color = lerp(this, Color.White, f)

fun Color.darken(f: Float): Color = lerp(this, Color.Black, f)

/** নরম, ফোলা মাটির মতো উপাদান: উপরে-বামে হালকা আলো, নিচে-ডানে নরম ছায়া */
fun Modifier.clay(
    radius: Dp = 28.dp,
    color: Color = Clay.surface,
    depth: Dp = 10.dp
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .drawBehind {
            val r = radius.toPx()
            val d = depth.toPx()
            for (i in 1..3) {
                val o = d * i / 3f * 0.7f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(-o, -o),
                    size = size,
                    cornerRadius = CornerRadius(r, r)
                )
            }
        }
        .shadow(depth, shape, clip = false, ambientColor = Clay.shadow, spotColor = Clay.shadow)
        .background(
            Brush.linearGradient(listOf(color.lighten(0.08f), color, color.darken(0.05f))),
            shape
        )
        .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
}

@Composable
fun BushraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
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
    textColor: Color = Color.White,
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
            .background(if (checked) Clay.accent.copy(alpha = 0.4f) else Clay.shadow.copy(alpha = 0.5f))
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(28.dp)
                .clay(14.dp, if (checked) Clay.accent else Clay.surface, 4.dp)
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
                .border(1.dp, Clay.shadow.copy(alpha = 0.5f), shape)
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

/** কথা বলার অবস্থা অনুযায়ী স্পন্দিত মাটির গোলক */
@Composable
fun Orb(phase: Phase, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val amp = when (phase) {
        Phase.IDLE -> 0.02f
        Phase.LISTENING -> 0.05f + (level.coerceIn(0f, 10f) / 10f) * 0.08f
        Phase.THINKING -> 0.10f
        Phase.SPEAKING -> 0.13f
    }
    val color = when (phase) {
        Phase.IDLE -> Clay.muted
        Phase.LISTENING -> Clay.accent
        Phase.THINKING -> Clay.warn
        Phase.SPEAKING -> Clay.accent2
    }
    Canvas(modifier) {
        val c = center
        val base = size.minDimension / 2.6f
        val r = base * (1f + amp * pulse)
        drawCircle(color.copy(alpha = 0.16f), r * 1.5f, c)
        drawCircle(color.copy(alpha = 0.26f), r * 1.22f, c)
        drawCircle(Clay.shadow.copy(alpha = 0.6f), r, c + Offset(r * 0.12f, r * 0.16f))
        drawCircle(
            Brush.radialGradient(
                listOf(color.lighten(0.5f), color, color.darken(0.18f)),
                center = c - Offset(r * 0.35f, r * 0.4f),
                radius = r * 1.6f
            ),
            r,
            c
        )
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.16f, c - Offset(r * 0.4f, r * 0.45f))
    }
}
