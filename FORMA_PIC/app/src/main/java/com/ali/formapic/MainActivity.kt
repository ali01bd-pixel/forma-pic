package com.ali.formapic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ali.formapic.ui.screens.AboutUsScreen
import com.ali.formapic.ui.screens.CameraScreen
import com.ali.formapic.ui.screens.GalleryScreen
import com.ali.formapic.ui.screens.SettingsScreen
import com.ali.formapic.ui.screens.StudioEditorScreen
import com.ali.formapic.ui.theme.*
import com.ali.formapic.ui.viewmodel.FormaPicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FormaPicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FormaPicTheme {
                FormaPicRootApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FormaPicRootApp(viewModel: FormaPicViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val cameraState by viewModel.cameraState.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val galleryItems by viewModel.galleryItems.collectAsState()
    val editorState by viewModel.editorState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            viewModel.refreshGallery()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            val permissionsToRequest = buildList {
                add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                }
            }.toTypedArray()
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    if (!hasCameraPermission) {
        PermissionExplanationScreen(
            onGrantClicked = {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        )
    } else {
        NavHost(navController = navController, startDestination = "camera") {
            composable("camera") {
                CameraScreen(
                    viewModel = viewModel,
                    cameraState = cameraState,
                    settings = settings,
                    onOpenGallery = {
                        viewModel.refreshGallery()
                        navController.navigate("gallery")
                    },
                    onOpenSettings = {
                        navController.navigate("settings")
                    }
                )
            }
            composable("gallery") {
                GalleryScreen(
                    viewModel = viewModel,
                    items = galleryItems,
                    onEditInStudio = { mediaItem ->
                        viewModel.openPhotoInStudio(mediaItem)
                        navController.navigate("studio")
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("studio") {
                StudioEditorScreen(
                    viewModel = viewModel,
                    editorState = editorState,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    settings = settings,
                    capabilities = cameraState.capabilities,
                    onOpenAboutUs = { navController.navigate("about") },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("about") {
                AboutUsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun PermissionExplanationScreen(onGrantClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CharcoalSurface, RoundedCornerShape(20.dp))
                .border(1.5.dp, ChampagneGold, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FORMA PIC",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = ChampagneGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Created by ALI",
                fontSize = 12.sp,
                color = SoftGold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera Access Required",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FORMA PIC needs camera permission to display the live DSLR viewfinder and capture photos and videos. All image processing runs 100% locally on your device.",
                fontSize = 13.sp,
                color = TitaniumSilver,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneGold,
                    contentColor = ObsidianBlack
                )
            ) {
                Text("GRANT CAMERA ACCESS", fontWeight = FontWeight.Bold)
            }
        }
    }
}
