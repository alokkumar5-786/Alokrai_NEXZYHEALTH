package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TestStatus
import com.example.ui.theme.NexzyTheme

@Composable
fun StatusBadge(
  status: TestStatus,
  customLabel: String? = null,
  modifier: Modifier = Modifier
) {
  val colors = NexzyTheme.colors
  val (bgColor, textColor, borderColor, icon) = when (status) {
    TestStatus.NORMAL -> Quad(
      colors.statusNormalContainer,
      colors.statusNormalText,
      colors.statusNormalBorder,
      Icons.Default.CheckCircle
    )
    TestStatus.ATTENTION -> Quad(
      colors.statusAttentionContainer,
      colors.statusAttentionText,
      colors.statusAttentionBorder,
      Icons.Default.Warning
    )
    TestStatus.REVIEW_NEEDED -> Quad(
      colors.statusAlertContainer,
      colors.statusAlertText,
      colors.statusAlertBorder,
      Icons.Default.ErrorOutline
    )
    TestStatus.INFORMATIONAL -> Quad(
      colors.statusInfoContainer,
      colors.statusInfoText,
      colors.statusInfoBorder,
      Icons.Default.Info
    )
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(color = bgColor, shape = RoundedCornerShape(20.dp))
      .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
      .padding(horizontal = 10.dp, vertical = 4.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = textColor,
      modifier = Modifier.size(14.dp)
    )
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = customLabel ?: status.label,
      color = textColor,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
