package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReportHistoryItem
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder

@Composable
fun ReportHistoryScreen(
  reports: List<ReportHistoryItem>,
  onReportClick: (ReportHistoryItem) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedPersonFilter by remember { mutableStateOf("All") }

  val filterCategories = listOf("All", "Self", "Father", "Mother")

  val filteredReports = reports.filter { report ->
    val matchesSearch = report.title.contains(searchQuery, ignoreCase = true) ||
      report.labName.contains(searchQuery, ignoreCase = true) ||
      report.patientName.contains(searchQuery, ignoreCase = true)

    val matchesPerson = when (selectedPersonFilter) {
      "Self" -> report.patientName.contains("Self", ignoreCase = true)
      "Father" -> report.patientName.contains("Father", ignoreCase = true)
      "Mother" -> report.patientName.contains("Mother", ignoreCase = true)
      else -> true
    }

    matchesSearch && matchesPerson
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Report History",
      subtitle = "All processed lab tests & health summaries",
      showBackButton = false
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp)
    ) {
      Spacer(modifier = Modifier.height(6.dp))

      // Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search by test name, lab, or family member") },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = "Search", tint = BrandNavyMuted)
        },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_reports_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = BrandTealPrimary,
          unfocusedBorderColor = SurfaceBorder
        )
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Filter Chips
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(filterCategories) { cat ->
          val isSelected = cat == selectedPersonFilter
          FilterChip(
            selected = isSelected,
            onClick = { selectedPersonFilter = cat },
            label = { Text(cat) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = BrandMintBackground,
              selectedLabelColor = BrandTealPrimary
            ),
            modifier = Modifier.testTag("filter_chip_$cat")
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (filteredReports.isEmpty()) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp)
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Description,
              contentDescription = null,
              tint = Color(0xFFCBD5E1),
              modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No matching reports found",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = BrandNavyText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Try adjusting your search keywords or filter",
              fontSize = 13.sp,
              color = BrandNavyMuted,
              textAlign = TextAlign.Center
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredReports) { report ->
            RecentReportItemCard(
              report = report,
              onClick = { onReportClick(report) }
            )
          }
          item {
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }
}
