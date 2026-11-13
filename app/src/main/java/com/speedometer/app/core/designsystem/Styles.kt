package com.speedometer.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Chakra Petch style, as the design sets numerals and titles. */
fun displayStyle(sizeSp: Int, weight: FontWeight, color: Color = TextPrimary) =
    TextStyle(fontFamily = DisplayFamily, fontWeight = weight, fontSize = sizeSp.sp, color = color)

/** DM Sans style for body text. */
fun bodyStyle(sizeSp: Int, weight: FontWeight = FontWeight.Normal, color: Color = TextMuted) =
    TextStyle(fontFamily = BodyFamily, fontWeight = weight, fontSize = sizeSp.sp, color = color)

/** Colour of a whole-trip score: green from 75, amber from 60, red below (History, Insights). */
fun tripScoreColor(score: Int): Color = when {
    score >= 75 -> Ok
    score >= 60 -> Amber
    else -> Danger
}

/** Colour of a Smooth score part bar (Results): green from 85, amber from 60, red below. */
fun partScoreColor(score: Int): Color = when {
    score >= 85 -> Ok
    score >= 60 -> Amber
    else -> Danger
}

/** Scrolling page on the app background, content width capped at 560dp and centred. */
@Composable
fun ScrollPage(
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
    /** Widest the content gets; defaults to the layout's single-column width. */
    maxWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Above the gesture or navigation bar. On the tab screens the bar already took that room, so nothing is added there.
    Column(modifier.fillMaxSize().background(Bg).navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = maxWidth ?: rememberLayout().contentMax).fillMaxWidth(), content = content)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = maxWidth ?: rememberLayout().contentMax).fillMaxWidth()) { bottom() }
        }
    }
}

/** Segmented control: track Surface1, 48dp tall. The amber highlight slides from the old segment to the new one. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(Radii.segmented)).padding(4.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val x by androidx.compose.animation.core.animateDpAsState(
            segment * selected.coerceAtLeast(0),
            androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "segment",
        )
        Box(Modifier.offset(x = x).width(segment).height(48.dp).clip(RoundedCornerShape(20.dp)).background(Amber))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val textColor by androidx.compose.animation.animateColorAsState(
                    if (on) Bg else TextSoft, androidx.compose.animation.core.tween(220), label = "segmentText",
                )
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = bodyStyle(15, if (on) FontWeight.Bold else FontWeight.Medium, textColor), maxLines = 1)
                }
            }
        }
    }
}

/** 52 x 32 switch with a 24 thumb inside a 48dp touch area, as in the design. */
@Composable
fun DesignSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 56.dp, height = 48.dp).clickable(role = Role.Switch) { onChange(!checked) },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 32.dp)
                .background(if (checked) Amber else Line2, RoundedCornerShape(16.dp)).padding(horizontal = 4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(24.dp).background(if (checked) Bg else TextSoft, CircleShape)) }
    }
}

/** Single-line text field on a Surface2 pill, with a hint while empty. */
@Composable
fun DesignTextField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    type: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Surface2, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, style = bodyStyle(16))
        androidx.compose.foundation.text.BasicTextField(
            value, onChange, singleLine = true, textStyle = bodyStyle(16, color = TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Amber),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Swipe left for the next tab, right for the previous one (a drag of 48 dp or more). Taps and vertical drags are left alone. */
fun Modifier.tabSwipe(onNext: () -> Unit, onPrevious: () -> Unit): Modifier = composed {
    val next = androidx.compose.runtime.rememberUpdatedState(onNext)
    val previous = androidx.compose.runtime.rememberUpdatedState(onPrevious)
    androidx.compose.ui.Modifier.pointerInput(Unit) {
        var dragX = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragX = 0f },
            onHorizontalDrag = { _, amount -> dragX += amount },
            onDragEnd = {
                val threshold = 48.dp.toPx()
                if (dragX < -threshold) next.value() else if (dragX > threshold) previous.value()
            },
        )
    }
}
