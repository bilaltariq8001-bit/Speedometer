package com.speedometer.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MessageKind { Success, Info, Error }

data class AppMessage(val text: String, val kind: MessageKind)

/**
 * Short messages in the app's own look, in place of the system toast: a dark rounded pill with a coloured icon that
 * slides up from the bottom and fades away. Call [show] from anywhere; [AppMessageHost] draws it once, over everything.
 */
object AppMessages {
    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    fun show(text: String, kind: MessageKind = MessageKind.Success) {
        _events.tryEmit(AppMessage(text, kind))
    }
}

private const val SHOW_MS = 2400L

@Composable
fun AppMessageHost(modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<AppMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        AppMessages.events.collect { message ->
            current = message
            visible = true
            delay(SHOW_MS)
            visible = false
            delay(250) // let it fade before the next one
        }
    }
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 2 },
        ) {
            current?.let { m ->
                val tint = when (m.kind) { MessageKind.Success -> Ok; MessageKind.Info -> Ice; MessageKind.Error -> Danger }
                Row(
                    Modifier.widthIn(max = 460.dp)
                        .background(Color(0xFF1A2028), RoundedCornerShape(22.dp))
                        .border(1.dp, Color(0xFF2A3644), RoundedCornerShape(22.dp))
                        .padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(30.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        StrokeIcon(
                            when (m.kind) {
                                MessageKind.Success -> "M5 12.5l4.5 4.5L19 7.5"
                                MessageKind.Info -> "M12 8v.5M12 11v6"
                                MessageKind.Error -> "M12 7v6M12 16.5v.5"
                            },
                            tint, size = 18.dp, box = 24f,
                        )
                    }
                    Text(m.text, style = bodyStyle(15, FontWeight.Bold, TextPrimary))
                }
            }
        }
    }
}
