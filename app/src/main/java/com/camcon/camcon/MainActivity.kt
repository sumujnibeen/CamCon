package com.camcon.camcon

import kotlin.time.Duration.Companion.seconds
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CamConCyan = Color(0xFF00BCD4)
private val CamConGreen = Color(0xFF4CAF50)
private val CamConRed = Color(0xFFF44336)

private val CamConDarkColors = darkColorScheme(
    primary = CamConCyan,
    onPrimary = Color.Black,
    secondary = CamConCyan,
    onSecondary = Color.Black,
    tertiary = CamConCyan,
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    error = CamConRed,
    onError = Color.White
)

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionsIfNeeded()

        setContent {
            MaterialTheme(
                colorScheme = CamConDarkColors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CamConApp(
                        onStartSharing = {
                            startCameraService()
                        },
                        onStopSharing = {
                            stopCameraService()
                        }
                    )
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {

        val permissions = mutableListOf<String>()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startCameraService() {

        val cameraGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        val audioGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!cameraGranted || !audioGranted) {
            requestPermissionsIfNeeded()
            return
        }

        val intent = Intent(
            this,
            CameraService::class.java
        )

        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopCameraService() {

        stopService(
            Intent(
                this,
                CameraService::class.java
            )
        )
    }
}


/* =========================================================
   APP ROUTER
   ========================================================= */

@Composable
fun CamConApp(
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit
) {

    var screen by remember {
        mutableStateOf("home")
    }

    when (screen) {

        "home" -> {

            HomeScreen(
                onShareCamera = {
                    screen = "share"
                },
                onSeeOtherFeed = {
                    screen = "viewer"
                }
            )
        }

        "share" -> {

            ShareScreen(
                onBack = {
                    onStopSharing()
                    screen = "home"
                },
                onStartSharing = onStartSharing,
                onStopSharing = onStopSharing
            )
        }

        "viewer" -> {

            ViewerScreen(
                onBack = {
                    screen = "home"
                }
            )
        }
    }
}


/* =========================================================
   HOME SCREEN
   ========================================================= */

@Composable
fun HomeScreen(
    onShareCamera: () -> Unit,
    onSeeOtherFeed: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "CamCon",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Camera sharing over local network",
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(40.dp)
        )

        Button(
            onClick = onShareCamera,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CamConCyan,
                contentColor = Color.Black
            )
        ) {
            Text("Share My Camera")
        }

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        OutlinedButton(
            onClick = onSeeOtherFeed,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CamConCyan
            )
        ) {
            Text("See Other Feed")
        }
    }
}


/* =========================================================
   SHARE SCREEN
   ========================================================= */

@Composable
fun ShareScreen(
    onBack: () -> Unit,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember {
        mutableStateOf("")
    }

    var sharing by remember {
        mutableStateOf(false)
    }

    var recording by remember {
        mutableStateOf(false)
    }

    var copied by remember {
        mutableStateOf(false)
    }

    var selectedFacing by remember {
        mutableStateOf("back")
    }

    var pendingRequest by remember {
        mutableStateOf<CameraServer.PendingRequest?>(null)
    }

    var viewerApproved by remember {
        mutableStateOf(false)
    }

    var viewerDenied by remember {
        mutableStateOf(false)
    }

    var ipAddress by remember {
        mutableStateOf("")
    }

    /*
     * Start/stop server depending on sharing state.
     */
    LaunchedEffect(sharing) {

        if (sharing) {

            CameraServer.setPassword(password)

            ipAddress =
                CameraServer.getLocalIpAddress()

            onStartSharing()

        } else {

            onStopSharing()

            pendingRequest = null
            viewerApproved = false
            viewerDenied = false
            recording = false
        }
    }


    /*
     * Check viewer request periodically.
     */
    LaunchedEffect(sharing) {

        while (sharing) {

            pendingRequest =
                CameraServer.getPendingRequest()

            val requestId =
                pendingRequest?.requestId

            viewerApproved =
                requestId != null &&
                        CameraServer.isApproved(requestId)

            viewerDenied =
                requestId != null &&
                        CameraServer.isDenied(requestId)

            delay(500)
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Share My Camera",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )


        /*
         * PASSWORD
         */

        if (!sharing) {

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                },
                label = {
                    Text("Set Password")
                },
                singleLine = true,
                visualTransformation =
                    PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }


        /*
         * IP CARD
         */

        if (sharing) {

            IpAddressCard(
                ipAddress = ipAddress,
                copied = copied,
                onCopy = {
                    val clipboard =
                        context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "CamCon Viewer Link",
                            "http://$ipAddress:8080/viewer"
                        )
                    )

                    copied = true
                }
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }


        /*
         * START / STOP SHARING
         */

        Button(
            onClick = {

                if (!sharing) {

                    if (password.isBlank()) {
                        return@Button
                    }

                    sharing = true

                } else {

                    sharing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (sharing) {
                ButtonDefaults.buttonColors(
                    containerColor = CamConRed,
                    contentColor = Color.White
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = CamConCyan,
                    contentColor = Color.Black
                )
            }
        ) {

            Text(
                if (sharing)
                    "Stop Sharing"
                else
                    "Start Sharing"
            )
        }


        /*
         * CAMERA CONTROLS
         */

        if (sharing) {

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "Camera",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Button(
                    onClick = {

                        selectedFacing = "front"
                        CameraService.setFacing(
                            CameraService.Facing.FRONT
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFacing == "front") CamConCyan else Color(0xFF263238),
                        contentColor = if (selectedFacing == "front") Color.Black else Color.White
                    )
                ) {

                    Text(
                        if (selectedFacing == "front")
                            "Front ✓"
                        else
                            "Front"
                    )
                }

                Button(
                    onClick = {

                        selectedFacing = "back"

                        CameraService.setFacing(
                            CameraService.Facing.BACK
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFacing == "back") CamConCyan else Color(0xFF263238),
                        contentColor = if (selectedFacing == "back") Color.Black else Color.White
                    )
                ) {

                    Text(
                        if (selectedFacing == "back")
                            "Back ✓"
                        else
                            "Back"
                    )
                }
            }


            /*
             * RECORDING
             */

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Video Recording",
                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Recorded videos are saved in Movies/CamCon"
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Button(
                        onClick = {

                            if (!recording) {

                                CameraService
                                    .startRecording()

                                recording = true

                            } else {

                                CameraService
                                    .stopRecording()

                                recording = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (recording) {
                            ButtonDefaults.buttonColors(
                                containerColor = CamConRed,
                                contentColor = Color.White
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = CamConCyan,
                                contentColor = Color.Black
                            )
                        }
                    ) {

                        Text(
                            if (recording)
                                "Stop Recording"
                            else
                                "Start Recording"
                        )
                    }
                }
            }


            /*
             * VIEWER REQUEST
             */

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            if (
                pendingRequest != null &&
                !viewerApproved
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text =
                                "Someone wants to watch",
                            style =
                                MaterialTheme.typography.titleMedium,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            Button(
                                onClick = {

                                    pendingRequest?.requestId?.let {
                                        CameraServer.approveRequest(it)
                                        viewerApproved = true
                                        viewerDenied = false
                                    }
                                },
                                modifier =
                                    Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CamConGreen,
                                    contentColor = Color.White
                                )
                            ) {

                                Text("Allow")
                            }

                            Button(
                                onClick = {

                                    pendingRequest?.requestId?.let {
                                        CameraServer.denyRequest(it)
                                    }

                                    viewerDenied = true
                                    viewerApproved = false
                                    pendingRequest = null
                                },
                                modifier =
                                    Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CamConRed,
                                    contentColor = Color.White
                                )
                            ) {

                                Text("Deny")
                            }
                        }
                    }
                }
            }


            /*
             * APPROVED VIEWER
             *
             * After Allow:
             * only Deny / Disconnect
             */

            if (viewerApproved) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text =
                                "Viewer is connected",
                            style =
                                MaterialTheme.typography.titleMedium,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        Button(
                            onClick = {

                                pendingRequest?.requestId?.let {
                                    CameraServer.denyRequest(it)
                                }

                                viewerApproved = false
                                viewerDenied = true
                                pendingRequest = null
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CamConRed,
                                contentColor = Color.White
                            )
                        ) {

                            Text("Deny / Disconnect")
                        }
                    }
                }
            }


            /*
             * DENIED
             */

            if (viewerDenied) {

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text =
                        "Viewer access denied.",
                    color = CamConRed,
                    textAlign = TextAlign.Center
                )
            }
        }


        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CamConCyan
            )
        ) {

            Text("Back")
        }
    }
}


/* =========================================================
   IP ADDRESS CARD
   ========================================================= */
@Composable
fun IpAddressCard(
    ipAddress: String,
    copied: Boolean,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Open on another device",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "http://$ipAddress:8080/viewer",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCopy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CamConCyan
                )
            ) {
                Text(
                    text = if (copied) "Copied" else "Copy Link"
                )
            }
        }
    }
}

/* =========================================================
   VIEWER SCREEN
   ========================================================= */

@Composable
fun ViewerScreen(
    onBack: () -> Unit
) {

    val scope = rememberCoroutineScope()

    var ipAddress by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var connected by remember {
        mutableStateOf(false)
    }

    var waiting by remember {
        mutableStateOf(false)
    }

    var denied by remember {
        mutableStateOf(false)
    }

    var connectionError by remember {
        mutableStateOf(false)
    }

    var requestId by remember {
        mutableStateOf("")
    }


    /*
     * Check approval status.
     */

    LaunchedEffect(waiting) {

        while (waiting) {

            if (requestId.isNotBlank()) {

                val status =
                    CameraServer.checkApproval(
                        ipAddress,
                        requestId
                    )

                when (status) {

                    CameraServer.ApprovalResult.WAITING -> Unit

                    CameraServer.ApprovalResult.APPROVED -> {
                        connected = true
                        waiting = false
                        denied = false
                    }

                    CameraServer.ApprovalResult.DENIED -> {
                        denied = true
                        waiting = false
                        connected = false
                    }

                    CameraServer.ApprovalResult.ERROR -> {
                        denied = true
                        waiting = false
                        connected = false
                    }
                }
            }

            delay(1.seconds)
        }
    }


    if (connected) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->

                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = WebViewClient()
                        loadUrl(
                            CameraServer.getViewerUrl(
                                ipAddress,
                                requestId
                            )
                        )
                    }
                }
            )

            Button(
                onClick = {
                    connected = false
                    waiting = false
                    requestId = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CamConRed,
                    contentColor = Color.White
                )
            ) {
                Text("Disconnect")
            }
        }

        return
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp)
            .verticalScroll(
                rememberScrollState()
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "See Other Feed",
            style =
                MaterialTheme.typography.headlineMedium,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )


        OutlinedTextField(
            value = ipAddress,
            onValueChange = {
                ipAddress = it
            },
            label = {
                Text("Host IP Address")
            },
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )


        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
            },
            label = {
                Text("Password")
            },
            singleLine = true,
            visualTransformation =
                PasswordVisualTransformation(),
            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )


        Button(
            onClick = {

                if (
                    ipAddress.isBlank() ||
                    password.isBlank()
                ) {
                    return@Button
                }

                waiting = true
                denied = false
                connectionError = false

                scope.launch {
                    when (
                        val result =
                            CameraServer.requestAccess(
                                ipAddress,
                                password
                            )
                    ) {

                        is CameraServer.AccessResult.Requested -> {
                            requestId = result.requestId
                            waiting = true
                            denied = false
                        }

                        CameraServer.AccessResult.WrongPassword -> {
                            waiting = false
                            denied = true
                            connectionError = false
                        }

                        CameraServer.AccessResult.Error -> {
                            waiting = false
                            denied = false
                            connectionError = true
                        }
                    }
                }
            },
            modifier =
                Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CamConCyan,
                contentColor = Color.Black
            )
        ) {

            Text("Request Access")
        }


        Spacer(
            modifier = Modifier.height(18.dp)
        )


        if (waiting) {

            Text(
                text =
                    "Waiting for the owner to Allow access...",
                textAlign =
                    TextAlign.Center
            )
        }


        if (denied) {

            Text(
                text =
                    "Wrong password or access denied.",
                color = CamConRed,
                textAlign =
                    TextAlign.Center
            )
        }

        if (connectionError) {

            Text(
                text =
                    "Could not connect. Check the IP and make sure both phones are on the same Wi-Fi network.",
                color = CamConRed,
                textAlign =
                    TextAlign.Center
            )
        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        OutlinedButton(
            onClick = onBack,
            modifier =
                Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CamConCyan
            )
        ) {

            Text("Back")
        }
    }
}
