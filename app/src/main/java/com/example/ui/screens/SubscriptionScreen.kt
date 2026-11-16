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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubscriptionPlan
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.StatusAttentionAmber
import com.example.ui.theme.StatusAttentionHoney
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun SubscriptionScreen(
  freeCreditsRemaining: Int = 3,
  plans: List<SubscriptionPlan>,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedPlanId by remember { mutableStateOf("plus") }
  var mockMessage by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Subscription & Credits",
      subtitle = "Report allowances & caregiver tiers",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        // Free Credit Status Card
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = StatusAttentionHoney.copy(alpha = 0.5f)),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StatusAttentionAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(46.dp)
                .background(Color.White, CircleShape)
            ) {
              Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                tint = StatusAttentionAmber,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$freeCreditsRemaining Free Reports Remaining",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = BrandNavyText
              )
              Text(
                text = "Includes full 12-yr-old translations & questions",
                fontSize = 12.sp,
                color = BrandNavyMuted
              )
            }
          }
        }
      }

      item {
        Text(
          text = "Choose A Plan",
          style = MaterialTheme.typography.titleMedium,
          color = BrandNavyText,
          fontWeight = FontWeight.Bold
        )
      }

      items(plans) { plan ->
        val isSelected = plan.id == selectedPlanId
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandMintSurface else SurfaceCard
          ),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) BrandTealPrimary else SurfaceBorder,
              shape = RoundedCornerShape(16.dp)
            )
            .clickable { selectedPlanId = plan.id }
            .testTag("plan_card_${plan.id}")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = plan.name,
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = BrandNavyText
                )
                Text(
                  text = plan.description,
                  fontSize = 12.sp,
                  color = BrandNavyMuted
                )
              }
              if (plan.isPopular) {
                Box(
                  modifier = Modifier
                    .background(BrandTealPrimary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text(
                    text = "Most Popular",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = plan.priceDisplay,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                color = BrandNavyText
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = plan.billingCycle,
                fontSize = 13.sp,
                color = BrandNavyMuted,
                modifier = Modifier.padding(bottom = 4.dp)
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              plan.features.forEach { feat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = BrandTealPrimary,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = feat,
                    fontSize = 13.sp,
                    color = BrandNavyText
                  )
                }
              }
            }
          }
        }
      }

      item {
        if (mockMessage != null) {
          Text(
            text = mockMessage!!,
            color = BrandTealPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            mockMessage = "Plan selection noted. Payments will be enabled in future releases (Day 1 Mock Interface)."
          },
          colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("btn_select_subscription_plan")
        ) {
          Text(
            text = "Select & Continue",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
          onClick = {
            mockMessage = "Top-up pack noted. Payments will be connected in future releases."
          },
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("btn_buy_report_credits")
        ) {
          Text(
            text = "Buy 3 Report Credits for ₹49",
            color = BrandNavyText,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
          )
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}
