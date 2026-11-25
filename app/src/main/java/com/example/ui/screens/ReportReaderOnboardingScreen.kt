package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MistBlue
import com.example.ui.theme.NexzyTheme
import com.example.ui.theme.TextOnDark
import kotlinx.coroutines.launch

private data class OnboardingSlide(
  val tagRes: Int,
  val titleRes: Int,
  val bodyRes: Int,
  val tipRes: Int,
  val imageRes: Int,
  val imageCdRes: Int,
  val icon: ImageVector,
  val highlights: List<String>
)

@Composable
fun ReportReaderOnboardingScreen(
  onNavigateBack: () -> Unit,
  onStartUploadClick: () -> Unit,
  onStartScanClick: () -> Unit,
  onTryDemoClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = NexzyTheme.colors
  val coroutineScope = rememberCoroutineScope()

  val slides = remember {
    listOf(
      OnboardingSlide(
        tagRes = R.string.onboarding_slide1_tag,
        titleRes = R.string.onboarding_slide1_title,
        bodyRes = R.string.onboarding_slide1_body,
        tipRes = R.string.onboarding_slide1_tip,
        imageRes = R.drawable.img_onboarding_upload,
        imageCdRes = R.string.onboarding_slide1_image_cd,
        icon = Icons.Default.FileUpload,
        highlights = listOf("PDF, JPG & PNG", "Up to 15 MB", "Family profiles")
      ),
      OnboardingSlide(
        tagRes = R.string.onboarding_slide2_tag,
        titleRes = R.string.onboarding_slide2_title,
        bodyRes = R.string.onboarding_slide2_body,
        tipRes = R.string.onboarding_slide2_tip,
        imageRes = R.drawable.img_onboarding_extract,
        imageCdRes = R.string.onboarding_slide2_image_cd,
        icon = Icons.AutoMirrored.Filled.FactCheck,
        highlights = listOf("Values & units", "Reference ranges", "Safety flags")
      ),
      OnboardingSlide(
        tagRes = R.string.onboarding_slide3_tag,
        titleRes = R.string.onboarding_slide3_title,
        bodyRes = R.string.onboarding_slide3_body,
        tipRes = R.string.onboarding_slide3_tip,
        imageRes = R.drawable.img_onboarding_explain,
        imageCdRes = R.string.onboarding_slide3_image_cd,
        icon = Icons.Default.AutoAwesome,
        highlights = listOf("Simple words", "4 languages", "Audio voice")
      ),
      OnboardingSlide(
        tagRes = R.string.onboarding_slide4_tag,
        titleRes = R.string.onboarding_slide4_title,
        bodyRes = R.string.onboarding_slide4_body,
        tipRes = R.string.onboarding_slide4_tip,
        imageRes = R.drawable.img_onboarding_privacy,
        imageCdRes = R.string.onboarding_slide4_image_cd,
        icon = Icons.Default.Shield,
        highlights = listOf("Consent first", "Clear session", "Doctor-ready")
      )
    )
  }

  val pagerState = rememberPagerState(pageCount = { slides.size })
  val currentPage = pagerState.currentPage
  val isLastPage = currentPage == slides.lastIndex

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top Navigation & Progress Header
    Row(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      IconButton(
        onClick = onNavigateBack,
        modifier = Modifier
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .testTag("btn_onboarding_back")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.onboarding_back_cd),
          tint = MaterialTheme.colorScheme.primary
        )
      }

      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Text(
          text = stringResource(R.string.onboarding_step_counter, currentPage + 1, slides.size),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("onboarding_step_counter")
        )
      }

      TextButton(
        onClick = onNavigateBack,
        modifier = Modifier
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .testTag("btn_onboarding_skip")
      ) {
        Text(
          text = stringResource(
            if (isLastPage) R.string.onboarding_done else R.string.onboarding_skip
          ),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }

    // Swipable Carousel Content
    HorizontalPager(
      state = pagerState,
      contentPadding = PaddingValues(horizontal = 18.dp),
      pageSpacing = 14.dp,
      modifier = Modifier
        .widthIn(max = 600.dp)
        .weight(1f)
        .fillMaxWidth()
        .testTag("onboarding_horizontal_pager")
    ) { pageIndex ->
      val slide = slides[pageIndex]
      OnboardingSlidePage(
        slide = slide,
        pageIndex = pageIndex
      )
    }

    // Animated Pager Indicators
    Row(
      modifier = Modifier
        .padding(top = 12.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      slides.indices.forEach { index ->
        val isSelected = index == currentPage
        val width by animateDpAsState(
          targetValue = if (isSelected) 28.dp else 8.dp,
          animationSpec = spring(),
          label = "indicator_width_$index"
        )
        val indicatorCd = stringResource(R.string.onboarding_indicator_cd, index + 1)

        Box(
          modifier = Modifier
            .size(width = width, height = 8.dp)
            .clip(CircleShape)
            .background(
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant
            )
            .clickable(
              role = Role.Tab,
              onClick = {
                coroutineScope.launch {
                  pagerState.animateScrollToPage(index)
                }
              }
            )
            .semantics { contentDescription = indicatorCd }
            .testTag("onboarding_indicator_$index")
        )
      }
    }

    // Bottom Navigation / Action Footer
    Column(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .fillMaxWidth()
        .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      AnimatedVisibility(
        visible = !isLastPage,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (currentPage > 0) {
            OutlinedButton(
              onClick = {
                coroutineScope.launch {
                  pagerState.animateScrollToPage((currentPage - 1).coerceAtLeast(0))
                }
              },
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, palette.dividerBorder),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
                .testTag("btn_onboarding_prev")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.onboarding_prev),
                style = MaterialTheme.typography.labelLarge
              )
            }
          }

          Button(
            onClick = {
              coroutineScope.launch {
                pagerState.animateScrollToPage((currentPage + 1).coerceAtMost(slides.lastIndex))
              }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
              .weight(if (currentPage > 0) 1.6f else 1f)
              .defaultMinSize(minHeight = 52.dp)
              .testTag("btn_onboarding_next")
          ) {
            Text(
              text = stringResource(R.string.onboarding_next),
              style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      AnimatedVisibility(
        visible = isLastPage,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onStartUploadClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 52.dp)
              .testTag("btn_onboarding_start_upload")
          ) {
            Icon(
              imageVector = Icons.Default.FileUpload,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.onboarding_cta_upload),
              style = MaterialTheme.typography.labelLarge
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = onStartScanClick,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, palette.dividerBorder),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("btn_onboarding_start_scan")
            ) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.onboarding_cta_scan),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
              )
            }

            OutlinedButton(
              onClick = onTryDemoClick,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("btn_onboarding_try_demo")
            ) {
              Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.onboarding_cta_demo),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OnboardingSlidePage(
  slide: OnboardingSlide,
  pageIndex: Int
) {
  val palette = NexzyTheme.colors

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, palette.dividerBorder),
    modifier = Modifier
      .fillMaxSize()
      .testTag("onboarding_slide_$pageIndex")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Midnight-to-Royal-Blue Hero Step Banner + Illustration Frame
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(palette.heroGradientBrush)
          .padding(12.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(28.dp)
                  .background(palette.heroCardSurface, CircleShape)
                  .border(1.dp, palette.heroCardBorder, CircleShape)
              ) {
                Icon(
                  imageVector = slide.icon,
                  contentDescription = null,
                  tint = TextOnDark,
                  modifier = Modifier.size(15.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(slide.tagRes),
                color = MistBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
              )
            }
          }

          Image(
            painter = painterResource(id = slide.imageRes),
            contentDescription = stringResource(slide.imageCdRes),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 190.dp)
              .aspectRatio(16f / 10f)
              .clip(RoundedCornerShape(14.dp))
              .border(1.dp, palette.heroCardBorder, RoundedCornerShape(14.dp))
          )
        }
      }

      // Slide Headline & Clear Explanation
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(slide.titleRes),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Text(
          text = stringResource(slide.bodyRes),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 21.sp
        )
      }

      // Capability Chips Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        slide.highlights.forEach { label ->
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp)
              )
              .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1
            )
          }
        }
      }

      // Helpful Tip Callout Card
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(palette.statusInfoContainer)
          .border(1.dp, palette.statusInfoBorder, RoundedCornerShape(14.dp))
          .padding(12.dp),
        verticalAlignment = Alignment.Top
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(28.dp)
            .background(Color.White.copy(alpha = 0.7f), CircleShape)
        ) {
          Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = palette.statusInfoAccent,
            modifier = Modifier.size(16.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = stringResource(R.string.onboarding_tip_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.statusInfoText
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = stringResource(slide.tipRes),
            style = MaterialTheme.typography.bodySmall,
            color = palette.statusInfoText,
            lineHeight = 17.sp
          )
        }
      }
    }
  }
}
