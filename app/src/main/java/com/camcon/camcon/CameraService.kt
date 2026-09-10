package com.camcon.camcon


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.view.Surface
import android.util.Log

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent

import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
class CameraService :
    Service(),
    LifecycleOwner {


    enum class Facing {
        BACK,
        FRONT
    }


    data class LensInfo(
        val id: String,
        val label: String,
        val logicalCameraId: String,
        val physicalCameraId: String?
    )


    private lateinit var lifecycleRegistry:
            LifecycleRegistry


    private var cameraExecutor:
            ExecutorService? = null


    private var cameraProvider:
            ProcessCameraProvider? = null


    private var videoCapture:
            VideoCapture<Recorder>? = null


    private var recording:
            Recording? = null


    @Volatile
    private var audioRunning = false

    private var audioThread: Thread? = null

    private var audioRecord: AudioRecord? = null


    override val lifecycle: Lifecycle
        get() = lifecycleRegistry


    override fun onCreate() {

        super.onCreate()


        lifecycleRegistry =
            LifecycleRegistry(this)


        lifecycleRegistry.currentState =
            Lifecycle.State.CREATED


        createNotificationChannel()


        val notification =
            createNotification()


        if (
            Build.VERSION.SDK_INT >= 29
        ) {

            startForeground(

                NOTIFICATION_ID,

                notification,

                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }


        lifecycleRegistry.currentState =
            Lifecycle.State.STARTED


        cameraExecutor =
            Executors.newFixedThreadPool(2)


        startAudioCapture()


        val future =
            ProcessCameraProvider.getInstance(
                this
            )


        future.addListener({

            try {

                cameraProvider =
                    future.get()


                discoverLenses()


                bindCamera()
                startAudioCapture()

                CameraServer.startServer()
                Log.i(TAG, "CameraService ready: camera bound and server starting")

            } catch (_: Exception) {
            }

        }, ContextCompat.getMainExecutor(this))
    }


    override fun onStartCommand(

        intent: Intent?,

        flags: Int,

        startId: Int

    ): Int {

        when (
            intent?.action
        ) {

            ACTION_SET_FACING -> {

                val value =
                    intent.getStringExtra(
                        EXTRA_FACING
                    )


                currentFacing =
                    if (
                        value ==
                        Facing.FRONT.name
                    ) {
                        Facing.FRONT
                    } else {
                        Facing.BACK
                    }


                if (
                    currentFacing ==
                    Facing.FRONT
                ) {

                    selectedLensId = null
                }


                discoverLenses()

                bindCamera()
            }


            ACTION_SET_LENS -> {

                if (!isRecording) {

                    selectedLensId =
                        intent.getStringExtra(
                            EXTRA_LENS_ID
                        )

                    bindCamera()
                }
            }


            ACTION_START_RECORDING -> {

                startRecordingInternal()
            }


            ACTION_STOP_RECORDING -> {

                stopRecordingInternal()
            }
        }


        return START_STICKY
    }


    private fun startAudioCapture() {
        if (audioRunning) return

        if (
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val minimumBuffer =
                AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AUDIO_CHANNEL_MASK,
                    AUDIO_ENCODING
                )

            if (minimumBuffer <= 0) return

            val bufferSize =
                kotlin.math.max(
                    minimumBuffer * 2,
                    4096
                )

            val recorder =
                AudioRecord.Builder()
                    .setAudioSource(
                        MediaRecorder.AudioSource.MIC
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(
                                AUDIO_SAMPLE_RATE
                            )
                            .setEncoding(
                                AUDIO_ENCODING
                            )
                            .setChannelMask(
                                AUDIO_CHANNEL_MASK
                            )
                            .build()
                    )
                    .setBufferSizeInBytes(
                        bufferSize
                    )
                    .build()

            if (
                recorder.state !=
                AudioRecord.STATE_INITIALIZED
            ) {
                recorder.release()
                return
            }

            audioRecord = recorder
            audioRunning = true

            recorder.startRecording()

            audioThread =
                Thread {
                    val buffer =
                        ByteArray(3200)

                    try {
                        while (audioRunning) {
                            val count =
                                recorder.read(
                                    buffer,
                                    0,
                                    buffer.size,
                                    AudioRecord.READ_BLOCKING
                                )

                            if (count > 0) {
                                CameraServer.setAudioPcm(buffer.copyOf(count))
                            }
                        }
                    } catch (_: Exception) {
                    }
                }.apply {
                    name = "CamCon-Audio"
                    start()
                }
        } catch (_: Exception) {
            audioRunning = false
            audioRecord?.release()
            audioRecord = null
        }
    }


    private fun stopAudioCapture() {
        audioRunning = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null
        audioThread = null
        CameraServer.clearAudio()
    }


    private fun bindCamera() {

        val provider =
            cameraProvider ?: return


        val executor =
            cameraExecutor ?: return


        try {

            provider.unbindAll()


            val selector =
                buildCameraSelector()


            val analysis =
                ImageAnalysis.Builder()

                    .setBackpressureStrategy(
                        ImageAnalysis
                            .STRATEGY_KEEP_ONLY_LATEST
                    )

                    .setOutputImageFormat(
                        ImageAnalysis
                            .OUTPUT_IMAGE_FORMAT_YUV_420_888
                    )

                    .setTargetRotation(
                        Surface.ROTATION_0
                    )

                    .setOutputImageRotationEnabled(
                        true
                    )

                    .build()


            analysis.setAnalyzer(

                executor,

                FrameAnalyzer()
            )


            val recorder =
                Recorder.Builder()
                    .build()


            val capture =
                VideoCapture.withOutput(
                    recorder
                )


            videoCapture =
                capture


            provider.bindToLifecycle(

                this,

                selector,

                analysis,

                capture
            )

        } catch (e: Exception) {
            CameraServer.latestJpeg = null
            Log.e(TAG, "Camera bind failed", e)
        }
    }
    private fun buildCameraSelector(): CameraSelector {
        return if (currentFacing == Facing.FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun discoverLenses() {
        if (currentFacing == Facing.FRONT) {
            lenses = emptyList()
            selectedLensId = null
            return
        }

        val mainLens =
            LensInfo(
                id = "default-back",
                label = "Main",
                logicalCameraId = "default",
                physicalCameraId = null
            )

        lenses = listOf(mainLens)
        selectedLensId = mainLens.id
    }

    private inner class FrameAnalyzer :
        ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            try {
                val jpeg = imageProxyToJpeg(image)
                if (jpeg != null && jpeg.isNotEmpty()) {
                    CameraServer.latestJpeg = jpeg
                    if (frameLogCounter++ % 30 == 0L) {
                        Log.i(TAG, "Video frame ready: ${image.width}x${image.height}, JPEG=${jpeg.size} bytes")
                    }
                } else {
                    Log.w(TAG, "JPEG conversion returned null/empty")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis failed", e)
            } finally {
                image.close()
            }
        }
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray? {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        if (width <= 0 || height <= 0) return null

        val nv21 = yuv420ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val output = ByteArrayOutputStream()
        val success = yuv.compressToJpeg(
            android.graphics.Rect(0, 0, width, height),
            80,
            output
        )
        return if (success) output.toByteArray() else null
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val output = ByteArray(width * height + chromaWidth * chromaHeight * 2)
        val planes = image.planes

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        var offset = 0
        for (row in 0 until height) {
            val rowStart = (crop.top + row) * yPlane.rowStride + crop.left * yPlane.pixelStride
            for (col in 0 until width) {
                output[offset++] = yBuffer.get(rowStart + col * yPlane.pixelStride)
            }
        }

        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val chromaLeft = crop.left / 2
        val chromaTop = crop.top / 2

        for (row in 0 until chromaHeight) {
            val uRowStart = (chromaTop + row) * uPlane.rowStride + chromaLeft * uPlane.pixelStride
            val vRowStart = (chromaTop + row) * vPlane.rowStride + chromaLeft * vPlane.pixelStride
            for (col in 0 until chromaWidth) {
                output[offset++] = vBuffer.get(vRowStart + col * vPlane.pixelStride)
                output[offset++] = uBuffer.get(uRowStart + col * uPlane.pixelStride)
            }
        }
        return output
    }

    private fun startRecordingInternal() {

        val capture =
            videoCapture ?: return


        if (
            recording != null
        ) {

            return
        }


        try {

            val fileName =
                "CamCon_" +
                        SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.US
                        ).format(
                            Date()
                        ) +
                        ".mp4"


            val values =
                ContentValues().apply {

                    put(
                        MediaStore
                            .Video
                            .Media
                            .DISPLAY_NAME,

                        fileName
                    )


                    put(
                        MediaStore
                            .Video
                            .Media
                            .MIME_TYPE,

                        "video/mp4"
                    )


                    if (
                        Build.VERSION.SDK_INT >= 29
                    ) {

                        put(
                            MediaStore
                                .Video
                                .Media
                                .RELATIVE_PATH,

                            "Movies/CamCon"
                        )
                    }
                }


            val output =
                MediaStoreOutputOptions
                    .Builder(

                        contentResolver,

                        MediaStore
                            .Video
                            .Media
                            .EXTERNAL_CONTENT_URI
                    )

                    .setContentValues(
                        values
                    )

                    .build()


            var pendingRecording =
                capture.output
                    .prepareRecording(
                        this,
                        output
                    )

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                pendingRecording =
                    pendingRecording.withAudioEnabled()
            }

            recording =
                pendingRecording
                    .start(

                        ContextCompat
                            .getMainExecutor(this)

                    ) { event ->

                        when (event) {

                            is VideoRecordEvent.Start -> {

                                isRecording =
                                    true
                            }


                            is VideoRecordEvent.Finalize -> {

                                isRecording =
                                    false

                                recording =
                                    null
                            }
                        }
                    }

        } catch (_: Exception) {

            recording =
                null

            isRecording =
                false
        }
    }


    private fun stopRecordingInternal() {

        try {

            recording?.stop()

        } catch (_: Exception) {
        }


        recording =
            null

        isRecording =
            false
    }


    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    CHANNEL_ID,

                    "CamCon Camera",

                    NotificationManager
                        .IMPORTANCE_LOW
                )


            channel.description =
                "CamCon camera sharing"


            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager


            manager.createNotificationChannel(
                channel
            )
        }
    }


    private fun createNotification():
            Notification {

        return androidx.core.app
            .NotificationCompat
            .Builder(

                this,

                CHANNEL_ID
            )

            .setContentTitle(
                "CamCon"
            )

            .setContentText(
                "Camera sharing is active"
            )

            .setSmallIcon(
                android.R.drawable.ic_menu_camera
            )

            .setOngoing(true)

            .build()
    }


    override fun onDestroy() {

        try {
            recording?.stop()
        } catch (_: Exception) {
        }


        recording =
            null

        isRecording =
            false


        cameraProvider?.unbindAll()


        cameraExecutor?.shutdown()


        cameraExecutor =
            null


        stopAudioCapture()


        CameraServer.stopServer()


        CameraServer.latestJpeg =
            null


        lifecycleRegistry.currentState =
            Lifecycle.State.DESTROYED


        super.onDestroy()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }


    private var frameLogCounter = 0L

    companion object {
        private const val TAG = "CamCon"

        private const val CHANNEL_ID =
            "camcon_camera_channel"


        private const val NOTIFICATION_ID =
            1001


        private const val AUDIO_SAMPLE_RATE = 16000

        private const val AUDIO_CHANNEL_MASK =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_ENCODING =
            AudioFormat.ENCODING_PCM_16BIT


        const val ACTION_SET_FACING =
            "com.camcon.app.SET_FACING"


        const val ACTION_SET_LENS =
            "com.camcon.app.SET_LENS"


        const val ACTION_START_RECORDING =
            "com.camcon.app.START_RECORDING"


        const val ACTION_STOP_RECORDING =
            "com.camcon.app.STOP_RECORDING"


        const val EXTRA_FACING =
            "facing"


        const val EXTRA_LENS_ID =
            "lens_id"


        @Volatile
        var currentFacing =
            Facing.BACK


        @Volatile
        var isRecording =
            false


        @Volatile
        private var lenses =
            emptyList<LensInfo>()


        @Volatile
        var selectedLensId:
                String? = null


        fun getLenses():
                List<LensInfo> {

            return lenses.toList()
        }


        fun setFacing(
            facing: Facing
        ) {

            val context =
                AppContextHolder.context
                    ?: return


            val intent =
                Intent(
                    context,
                    CameraService::class.java
                ).apply {

                    action =
                        ACTION_SET_FACING

                    putExtra(
                        EXTRA_FACING,
                        facing.name
                    )
                }


            ContextCompat.startForegroundService(
                context,
                intent
            )
        }


        fun setLens(
            lensId: String
        ) {

            val context =
                AppContextHolder.context
                    ?: return


            val intent =
                Intent(
                    context,
                    CameraService::class.java
                ).apply {

                    action =
                        ACTION_SET_LENS

                    putExtra(
                        EXTRA_LENS_ID,
                        lensId
                    )
                }


            ContextCompat.startForegroundService(
                context,
                intent
            )
        }


        fun startRecording() {

            val context =
                AppContextHolder.context
                    ?: return


            val intent =
                Intent(
                    context,
                    CameraService::class.java
                ).apply {

                    action =
                        ACTION_START_RECORDING
                }


            ContextCompat.startForegroundService(
                context,
                intent
            )
        }


        fun stopRecording() {

            val context =
                AppContextHolder.context
                    ?: return


            val intent =
                Intent(
                    context,
                    CameraService::class.java
                ).apply {

                    action =
                        ACTION_STOP_RECORDING
                }


            ContextCompat.startForegroundService(
                context,
                intent
            )
        }
    }
}



private object AppContextHolder {

    var context:
            Context? = null
}


class CamConApplication :
    android.app.Application() {

    override fun onCreate() {

        super.onCreate()

        AppContextHolder.context =
            applicationContext
    }
}
