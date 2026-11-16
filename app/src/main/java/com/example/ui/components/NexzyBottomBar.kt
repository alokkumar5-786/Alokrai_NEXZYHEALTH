package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navigation.Screen
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary

@Composable
fun NexzyBottomBar(
  currentRoute: String,
  onNavigateToRoute: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  NavigationBar(
    modifier = modifier.navigationBarsPadding(),
    containerColor = Color.White,
    tonalElevation = 8.dp,
    windowInsets = WindowInsets.navigationBars
  ) {
    val isHome = currentRoute == Screen.Home.route
    NavigationBarItem(
      selected = isHome,
      onClick = { onNavigateToRoute(Screen.Home.route) },
      icon = {
        Icon(
          imageVector = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
          contentDescription = "Home",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "Home",
          fontSize = 12.sp,
          fontWeight = if (isHome) FontWeight.Bold else FontWeight.Medium
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = BrandTealPrimary,
        selectedTextColor = BrandTealPrimary,
        indicatorColor = BrandMintBackground,
        unselectedIconColor = BrandNavyMuted,
        unselectedTextColor = BrandNavyMuted
      ),
      modifier = Modifier.testTag("nav_bottom_home")
    )

    val isReports = currentRoute == Screen.ReportHistory.route
    NavigationBarItem(
      selected = isReports,
      onClick = { onNavigateToRoute(Screen.ReportHistory.route) },
      icon = {
        Icon(
          imageVector = if (isReports) Icons.Filled.Description else Icons.Outlined.Description,
          contentDescription = "My Reports",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "My Reports",
          fontSize = 12.sp,
          fontWeight = if (isReports) FontWeight.Bold else FontWeight.Medium
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = BrandTealPrimary,
        selectedTextColor = BrandTealPrimary,
        indicatorColor = BrandMintBackground,
        unselectedIconColor = BrandNavyMuted,
        unselectedTextColor = BrandNavyMuted
      ),
      modifier = Modifier.testTag("nav_bottom_reports")
    )

    val isProfile = currentRoute == Screen.UserProfile.route
    NavigationBarItem(
      selected = isProfile,
      onClick = { onNavigateToRoute(Screen.UserProfile.route) },
      icon = {
        Icon(
          imageVector = if (isProfile) Icons.Filled.Person else Icons.Outlined.Person,
          contentDescription = "Profile",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "Profile",
          fontSize = 12.sp,
          fontWeight = if (isProfile) FontWeight.Bold else FontWeight.Medium
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = BrandTealPrimary,
        selectedTextColor = BrandTealPrimary,
        indicatorColor = BrandMintBackground,
        unselectedIconColor = BrandNavyMuted,
        unselectedTextColor = BrandNavyMuted
      ),
      modifier = Modifier.testTag("nav_bottom_profile")
    )
  }
}
