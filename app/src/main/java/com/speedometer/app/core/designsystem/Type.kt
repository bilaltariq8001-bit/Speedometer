package com.speedometer.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.speedometer.app.R

// Bundled fonts (SIL Open Font License): Chakra Petch for numerals and titles, DM Sans for body.
val DisplayFamily: FontFamily = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: FontWeight) = Font(
    R.font.dm_sans, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
val BodyFamily: FontFamily = FontFamily(
    dmSans(FontWeight.Normal), dmSans(FontWeight.Medium), dmSans(FontWeight.Bold),
)

val SpeedometerTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 64.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontSize = 12.sp),
)
