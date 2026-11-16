package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun UserProfileScreen(
  userName: String = "Priya Sharma",
  userEmail: String = "priya.caregiver@example.com",
  selectedLanguage: String = "English",
  reportsAnalysed: Int = 14,
  freeCredits: Int = 3,
  userPhone: String = "",
  isPhoneVerified: Boolean = false,
  onNavigateToFamilyProfiles: () -> Unit,
  onNavigateToSubscription: () -> Unit,
  onNavigateToPrivacy: () -> Unit,
  onNavigateToHelp: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToLanguage: () -> Unit,
  onNavigateToPhoneVerification: () -> Unit = {},
  onLogoutClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "My Profile",
      subtitle = "Caregiver account & preferences",
      showBackButton = false
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      // User Card
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(18.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(56.dp)
              .background(BrandTealPrimary, CircleShape)
          ) {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = "Avatar",
              tint = Color.White,
              modifier = Modifier.size(32.dp)
            )
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = userName,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = BrandNavyText
              )
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = BrandTealPrimary,
                modifier = Modifier.size(16.dp)
              )
            }
            Text(
              text = userEmail,
              fontSize = 13.sp,
              color = BrandNavyMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
              modifier = Modifier
                .background(BrandMintBackground, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = "Primary Family Caregiver",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandTealPrimary
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Phone Verification Status Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (isPhoneVerified) BrandMintSurface else Color(0xFFFFFBEB)
        ),
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (isPhoneVerified) SurfaceBorder else Color(0xFFFDE68A)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { if (!isPhoneVerified) onNavigateToPhoneVerification() }
          .testTag("user_profile_phone_card")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(40.dp)
              .background(
                if (isPhoneVerified) BrandMintBackground else Color(0xFFFEF3C7),
                CircleShape
              )
          ) {
            Icon(
              imageVector = if (isPhoneVerified) Icons.Default.CheckCircle else Icons.Default.PhoneAndroid,
              contentDescription = null,
              tint = if (isPhoneVerified) BrandTealPrimary else Color(0xFFD97706),
              modifier = Modifier.size(22.dp)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (isPhoneVerified) "Verified Mobile Number" else "Mobile Number Not Linked",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = BrandNavyText
            )
            Text(
              text = if (isPhoneVerified && userPhone.isNotBlank()) userPhone else "Add mobile for 2FA security and SMS alerts",
              fontSize = 12.sp,
              color = BrandNavyMuted
            )
          }

          if (!isPhoneVerified) {
            Box(
              modifier = Modifier
                .background(BrandTealPrimary, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = "Link Now",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Stats Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        ProfileStatBox("14", "Reports Analysed", modifier = Modifier.weight(1f))
        ProfileStatBox("3", "Family Members", modifier = Modifier.weight(1f))
        ProfileStatBox("$freeCredits", "Free Credits", modifier = Modifier.weight(1f))
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Account & Caregiver Settings",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(10.dp))

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MenuNavCard(
          icon = Icons.Default.PhoneAndroid,
          title = "Mobile Number Verification",
          subtitle = if (isPhoneVerified) "$userPhone • Verified" else "Link and verify mobile number",
          onClick = onNavigateToPhoneVerification,
          testTag = "menu_phone_verification"
        )
        MenuNavCard(
          icon = Icons.Default.Group,
          title = "Family Profiles",
          subtitle = "Manage parent & dependent records",
          onClick = onNavigateToFamilyProfiles,
          testTag = "menu_family_profiles"
        )

        MenuNavCard(
          icon = Icons.Default.CreditCard,
          title = "Subscription & Credits",
          subtitle = "$freeCredits free reports remaining • View plans",
          onClick = onNavigateToSubscription,
          testTag = "menu_subscription"
        )

        MenuNavCard(
          icon = Icons.Default.Language,
          title = "Explanation Language",
          subtitle = "Current: $selectedLanguage",
          onClick = onNavigateToLanguage,
          testTag = "menu_language"
        )

        MenuNavCard(
          icon = Icons.Default.Security,
          title = "Privacy & Data Deletion",
          subtitle = "Zero data selling • Delete records",
          onClick = onNavigateToPrivacy,
          testTag = "menu_privacy"
        )

        MenuNavCard(
          icon = Icons.Default.HelpOutline,
          title = "Help & Support",
          subtitle = "FAQs & WhatsApp assistance",
          onClick = onNavigateToHelp,
          testTag = "menu_help"
        )

        MenuNavCard(
          icon = Icons.Default.Settings,
          title = "App Settings",
          subtitle = "Font sizing, voice speed, alerts",
          onClick = onNavigateToSettings,
          testTag = "menu_settings"
        )

        MenuNavCard(
          icon = Icons.Default.Logout,
          title = "Sign Out",
          subtitle = "Log out from this device",
          onClick = onLogoutClick,
          isDestructive = true,
          testTag = "menu_logout"
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ProfileStatBox(
  value: String,
  label: String,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = modifier.border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        color = BrandNavyText
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        fontSize = 10.sp,
        color = BrandNavyMuted,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun MenuNavCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
  isDestructive: Boolean = false
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(38.dp)
          .background(
            if (isDestructive) Color(0xFFFEE2E2) else BrandMintBackground,
            CircleShape
          )
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isDestructive) Color(0xFFDC2626) else BrandTealPrimary,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = if (isDestructive) Color(0xFFDC2626) else BrandNavyText
        )
        Text(
          text = subtitle,
          fontSize = 12.sp,
          color = BrandNavyMuted
        )
      }

      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = "Navigate",
        tint = BrandNavyMuted
      )
    }
  }
}
