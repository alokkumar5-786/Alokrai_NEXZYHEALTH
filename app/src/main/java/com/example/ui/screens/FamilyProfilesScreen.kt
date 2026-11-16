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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.ui.components.NexzyTopAppBar
import com.example.ui.theme.BrandMintBackground
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandNavyMuted
import com.example.ui.theme.BrandNavyText
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard

@Composable
fun FamilyProfilesScreen(
  familyMembers: List<FamilyMember>,
  onAddMember: (FamilyMember) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showAddDialog by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }
  var newRelation by remember { mutableStateOf("Father") }
  var newAge by remember { mutableStateOf("") }
  var newNotes by remember { mutableStateOf("") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    NexzyTopAppBar(
      title = "Family Profiles",
      subtitle = "Adult caregiver & dependent health accounts",
      showBackButton = true,
      onBackClick = onNavigateBack
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Text(
        text = "Caregiver Family Circle",
        style = MaterialTheme.typography.titleMedium,
        color = BrandNavyText,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Easily organize lab reports separately for each family member under one account.",
        style = MaterialTheme.typography.bodyMedium,
        color = BrandNavyMuted
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(familyMembers) { member ->
          FamilyMemberCard(member = member)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Button(
        onClick = { showAddDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_add_family_member")
      ) {
        Icon(Icons.Default.PersonAdd, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Add Family Member",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  if (showAddDialog) {
    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = {
        Text("Add Family Member", fontWeight = FontWeight.Bold, color = BrandNavyText)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
          )

          OutlinedTextField(
            value = newRelation,
            onValueChange = { newRelation = it },
            label = { Text("Relationship (e.g. Father, Mother, Spouse)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
          )

          OutlinedTextField(
            value = newAge,
            onValueChange = { newAge = it },
            label = { Text("Age (Years)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
          )

          OutlinedTextField(
            value = newNotes,
            onValueChange = { newNotes = it },
            label = { Text("Health Notes (e.g. Diabetic, Thyroid)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newName.isNotBlank()) {
              onAddMember(
                FamilyMember(
                  id = "fam-${System.currentTimeMillis()}",
                  name = newName,
                  relation = newRelation,
                  age = newAge.toIntOrNull() ?: 50,
                  gender = "Other",
                  conditionNotes = if (newNotes.isBlank()) "Routine monitoring" else newNotes,
                  reportCount = 0
                )
              )
            }
            showAddDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary)
        ) {
          Text("Save Profile")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showAddDialog = false }) {
          Text("Cancel", color = BrandNavyMuted)
        }
      }
    )
  }
}

@Composable
private fun FamilyMemberCard(member: FamilyMember) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
      .testTag("family_card_${member.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(46.dp)
          .background(BrandMintBackground, CircleShape)
      ) {
        Icon(
          imageVector = if (member.age >= 60) Icons.Default.Elderly else Icons.Default.Person,
          contentDescription = null,
          tint = BrandTealPrimary,
          modifier = Modifier.size(26.dp)
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = member.name,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = BrandNavyText
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .background(BrandMintBackground, RoundedCornerShape(6.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = member.relation,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = BrandTealPrimary
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = "${member.age} yrs • ${member.gender} • ${member.reportCount} reports",
          fontSize = 12.sp,
          color = BrandNavyMuted
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Notes: ${member.conditionNotes}",
          fontSize = 12.sp,
          color = Color(0xFF475569)
        )
      }
    }
  }
}
