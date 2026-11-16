package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.SupportAgent
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
import com.example.model.FaqItem
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun HelpSupportScreen(
  faqs: List<FaqItem>,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var feedbackToast by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Help & Support",
      subtitle = "Caregiver assistance & report guidance",
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
        // Quick Contact Channels Card
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Need Assistance with a Report?",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = BrandNavyText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Our caregiver support team is available Mon-Sat (9 AM - 7 PM IST).",
              fontSize = 12.sp,
              color = BrandNavyMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { feedbackToast = "Opening WhatsApp support chat..." },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("btn_whatsapp_support")
              ) {
                Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }

              OutlinedButton(
                onClick = { feedbackToast = "Opening email composer..." },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("btn_email_support")
              ) {
                Icon(Icons.Default.Email, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Email", color = BrandNavyText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
              }
            }
          }
        }
      }

      if (feedbackToast != null) {
        item {
          Text(
            text = feedbackToast!!,
            color = BrandTealPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
          )
        }
      }

      item {
        Text(
          text = "Frequently Asked Questions",
          style = MaterialTheme.typography.titleMedium,
          color = BrandNavyText,
          fontWeight = FontWeight.Bold
        )
      }

      items(faqs) { faq ->
        FaqAccordionCard(faq = faq)
      }

      item {
        // Disclaimer Note
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFB), RoundedCornerShape(12.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Important Medical Notice",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = BrandNavyText
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "NexzyHealth provides educational explanations and does not provide formal medical diagnoses or prescriptions. Always consult your certified healthcare provider for medical decisions.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = BrandNavyMuted
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun FaqAccordionCard(faq: FaqItem) {
  var isExpanded by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
      .clickable { isExpanded = !isExpanded }
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = faq.question,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = BrandNavyText,
          modifier = Modifier.weight(1f)
        )
        Icon(
          imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = null,
          tint = BrandNavyMuted
        )
      }

      AnimatedVisibility(visible = isExpanded) {
        Column {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = faq.answer,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Color(0xFF334155)
          )
        }
      }
    }
  }
}
