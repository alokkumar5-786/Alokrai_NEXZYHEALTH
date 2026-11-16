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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.model.TestParameter
import com.example.model.TestStatus
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun DetailedResultsScreen(
  parameters: List<TestParameter>,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: All, 1: Needs Discussion, 2: Normal

  val filteredList = when (selectedFilterTab) {
    1 -> parameters.filter { it.status == TestStatus.ATTENTION || it.status == TestStatus.REVIEW_NEEDED }
    2 -> parameters.filter { it.status == TestStatus.NORMAL }
    else -> parameters
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Detailed Test Results",
      subtitle = "All lab metrics with everyday takeaways",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp)
    ) {
      Spacer(modifier = Modifier.height(6.dp))

      // Filter Tabs
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = selectedFilterTab == 0,
          onClick = { selectedFilterTab = 0 },
          label = { Text("All (${parameters.size})") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandMintBackground,
            selectedLabelColor = BrandTealPrimary
          ),
          modifier = Modifier.testTag("filter_all_tests")
        )

        FilterChip(
          selected = selectedFilterTab == 1,
          onClick = { selectedFilterTab = 1 },
          label = { Text("Needs Discussion (2)") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFFFEF3C7),
            selectedLabelColor = Color(0xFFB45309)
          ),
          modifier = Modifier.testTag("filter_needs_discussion")
        )

        FilterChip(
          selected = selectedFilterTab == 2,
          onClick = { selectedFilterTab = 2 },
          label = { Text("Normal (4)") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandMintBackground,
            selectedLabelColor = BrandTealPrimary
          ),
          modifier = Modifier.testTag("filter_normal_tests")
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(filteredList) { param ->
          ExpandableParameterCard(parameter = param)
        }
        item {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }
}

@Composable
private fun ExpandableParameterCard(parameter: TestParameter) {
  var isExpanded by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
      .clickable { isExpanded = !isExpanded }
      .testTag("param_card_${parameter.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = parameter.name,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = BrandNavyText
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "${parameter.value} ${parameter.unit} (Ref: ${parameter.referenceRange})",
            fontSize = 12.sp,
            color = BrandNavyMuted
          )
        }
        StatusBadge(status = parameter.status)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
          imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (isExpanded) "Collapse" else "Expand",
          tint = BrandNavyMuted
        )
      }

      // Preview Metaphor (Always visible)
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = parameter.simpleMetaphor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = BrandTealPrimary
      )

      AnimatedVisibility(visible = isExpanded) {
        Column {
          Spacer(modifier = Modifier.height(12.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.White, RoundedCornerShape(10.dp))
              .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
              .padding(12.dp)
          ) {
            Column {
              Text(
                text = "Simple Explanation:",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = BrandNavyText
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = parameter.simpleExplanation,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = BrandNavyText
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp))
              .padding(12.dp)
          ) {
            Row(verticalAlignment = Alignment.Top) {
              Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = Color(0xFFB45309),
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Doctor Question: ${parameter.doctorQuestion}",
                fontSize = 12.sp,
                color = Color(0xFF78350F),
                lineHeight = 17.sp
              )
            }
          }
        }
      }
    }
  }
}
