package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val OrangeDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFF4B400),    // Brand Yellow
    secondary = Color(0xFF1E293B),  // Slate-800 dark surface
    tertiary = Color(0xFF10B981),   // Emerald success
    background = Color(0xFF0F172A), // Slate-900 dark background
    surface = Color(0xFF1E293B),    // Slate-800 dark surface
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    outlineVariant = Color(0xFF334155),
    surfaceVariant = Color(0xFF1E293B)
  )

private val OrangeLightColorScheme =
  lightColorScheme(
    primary = Color(0xFFF4B400),    // Brand Yellow
    secondary = Color(0xFF000000),  // Black
    tertiary = Color(0xFF059669),   // Emerald-600
    background = Color(0xFFFFFFFF), // White background
    surface = Color(0xFFF8FAFC),    // Light surface
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A), // deep slate text
    onSurface = Color(0xFF1E293B),    // medium dark slate text
    outlineVariant = Color(0xFFE2E8F0), // clean light divider border
    surfaceVariant = Color(0xFFF1F5F9)
  )

private val BlueDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF38BDF8),    // Sky blue
    secondary = Color(0xFF94A3B8),  // soft slate
    tertiary = Color(0xFF2DD4BF),   // teal success
    background = Color(0xFF0B0F19), // Deep rich blue-black
    surface = Color(0xFF151D30),    // Rich dark surface
    onPrimary = Color(0xFF0B0F19),
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    outlineVariant = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF151D30)
  )

private val BlueLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0284C7),    // Sleek executive sky-blue
    secondary = Color(0xFF475569),  // Slate-600
    tertiary = Color(0xFF0D9488),   // Teal-600
    background = Color(0xFFF0F4F8), // Soft slate background
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFF8FAFC)
  )

private val GreenDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF10B981),    // Emerald
    secondary = Color(0xFF94A3B8),  // soft slate
    tertiary = Color(0xFF34D399),   // light emerald success
    background = Color(0xFF062016), // Deep forest black-green
    surface = Color(0xFF0B3324),    // forest dark surface
    onPrimary = Color(0xFF062016),
    onSecondary = Color.White,
    onBackground = Color(0xFFF0FDF4),
    onSurface = Color(0xFFECFDF5),
    outlineVariant = Color(0xFF124E38),
    surfaceVariant = Color(0xFF0B3324)
  )

private val GreenLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF059669),    // Emerald green
    secondary = Color(0xFF475569),  // Slate-600
    tertiary = Color(0xFF047857),   // forest green
    background = Color(0xFFF2FBF7), // Clean pastel mint-green tint
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF062F21),
    onSurface = Color(0xFF0B4632),
    outlineVariant = Color(0xFFE6F4EA),
    surfaceVariant = Color(0xFFF2FBF7)
  )

private val SlateDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF94A3B8),    // Cool slate grey accent
    secondary = Color(0xFFCBD5E1),  // soft silver
    tertiary = Color(0xFF38BDF8),   // sky accent
    background = Color(0xFF181B20), // Professional charcoal-slate black
    surface = Color(0xFF22272E),    // Slate dark surface
    onPrimary = Color(0xFF181B20),
    onSecondary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFF2D353F),
    surfaceVariant = Color(0xFF22272E)
  )

private val SlateLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF475569),    // Slate-600 primary
    secondary = Color(0xFF64748B),  // Slate-500 secondary
    tertiary = Color(0xFF0284C7),   // blue accent
    background = Color(0xFFF1F5F9), // Light silver slate grey background
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFF8FAFC)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  screenMode: String = "Mobile",
  colorTheme: String = "Warm Orange",
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when (colorTheme) {
      "Cool Blue" -> {
        if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
      }
      "Forest Green" -> {
        if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
      }
      "Classic Slate" -> {
        if (darkTheme) SlateDarkColorScheme else SlateLightColorScheme
      }
      else -> { // "Warm Orange"
        if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
      }
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
