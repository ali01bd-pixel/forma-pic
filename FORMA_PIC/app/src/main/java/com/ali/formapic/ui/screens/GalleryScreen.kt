package com.ali.formapic.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ali.formapic.data.media.ExifMetadataDetails
import com.ali.formapic.data.media.FormaMediaItem
import com.ali.formapic.ui.components.ProBadgeChip
import com.ali.formapic.ui.theme.*
import com.ali.formapic.ui.viewmodel.FormaPicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: FormaPicViewModel,
    items: List<FormaMediaItem>,
    onEditInStudio: (FormaMediaItem) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<FormaMediaItem?>(items.firstOrNull()) }
    var exifDetails by remember { mutableStateOf<ExifMetadataDetails?>(null) }
    var showExifSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selectedItem) {
        selectedItem?.let { item ->
            if (!item.isVideo) {
                exifDetails = viewModel.galleryRepo.readExifDetails(item.uri)
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Camera", tint = PureWhite)
                    }
                },
                title = {
                    Text("FORMA GALLERY (${items.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ChampagneGold)
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshGallery(searchQuery) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = PureWhite)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.refreshGallery(it)
                },
                placeholder = { Text("Search filename, RAW, EDIT, or video...", fontSize = 13.sp, color = MutedSilver) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ChampagneGold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ChampagneGold,
                    unfocusedBorderColor = CharcoalElevated,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite
                )
            )

            // Selected Media Hero Viewer & Action Toolbar
            selectedItem?.let { active ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalSurface)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = active.uri,
                            contentDescription = active.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (active.isRawDng) {
                                ProBadgeChip("RAW DNG", true) {}
                            }
                            if (active.isEditedCopy) {
                                ProBadgeChip("EDITED COPY", true) {}
                            }
                            if (active.isVideo) {
                                ProBadgeChip("VIDEO MP4", true) {}
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${active.displayName} • ${active.formattedResolution()} • ${active.formattedFileSize()}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TitaniumSilver
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Buttons: AI Studio Edit, EXIF Info, Duplicate, Share, Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!active.isVideo) {
                            Button(
                                onClick = { onEditInStudio(active) },
                                colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("EDIT IN AI STUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(onClick = { showExifSheet = !showExifSheet }) {
                            Icon(Icons.Default.Info, contentDescription = "EXIF Metadata", tint = ChampagneGold)
                        }

                        IconButton(onClick = { viewModel.duplicateMedia(active) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = PureWhite)
                        }

                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = active.mimeType
                                putExtra(Intent.EXTRA_STREAM, active.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share FORMA PIC Media"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = PureWhite)
                        }

                        IconButton(onClick = {
                            viewModel.deleteMedia(active)
                            selectedItem = items.firstOrNull { it.id != active.id }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRecord)
                        }
                    }

                    if (showExifSheet && exifDetails != null) {
                        val ex = exifDetails!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(CharcoalElevated, RoundedCornerShape(10.dp))
                                .border(0.8.dp, ChampagneGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text("EXIF OPTICAL & SENSOR METADATA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ChampagneGold)
                            Text("Camera: ${ex.cameraModel} | Lens: ${ex.focalLength} (${ex.aperture})", fontSize = 11.sp, color = PureWhite)
                            Text("Exposure: ${ex.iso} • ${ex.exposureTime} | Color: ${ex.colorSpace}", fontSize = 11.sp, color = TitaniumSilver)
                            Text("Captured: ${ex.dateTaken}", fontSize = 11.sp, color = TitaniumSilver)
                        }
                    }
                }
            }

            // Thumbnail Grid
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos or videos captured yet.", color = TitaniumSilver)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items, key = { "${it.isVideo}_${it.id}" }) { item ->
                        val isSelected = selectedItem?.id == item.id
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.5.dp,
                                    color = if (isSelected) ChampagneGold else TitaniumSilver.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedItem = item }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (item.isEditedCopy) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(ChampagneGold, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("EDIT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ObsidianBlack)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
