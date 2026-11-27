package com.budget.app.ui.setup

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The QR scanner, all inside the app: CameraX shows the camera and ZXing reads
 * the code. Both are open source; nothing goes through Google Play services, so
 * it works on phones without them. Needs the camera permission, which the
 * caller asks for first. Calls [onCode] once with the code's text.
 */
@Composable
fun QrScanner(onCode: (String) -> Unit, onClose: () -> Unit) = BarcodeScanner(
    formats = listOf(BarcodeFormat.QR_CODE),
    hint = "Point at the QR code under Settings → Sign-in & people → Add a phone on the web.",
    onCode = { text, _ -> onCode(text) },
    onClose = onClose,
)

/** Every barcode type store cards use; what the loyalty card scanner looks for. */
val CARD_FORMATS = listOf(
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR,
    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF,
    BarcodeFormat.QR_CODE, BarcodeFormat.PDF_417, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX,
)

/** The camera, reading the first barcode of [formats] it sees: its text and type. */
@Composable
fun BarcodeScanner(formats: List<BarcodeFormat>, hint: String, onCode: (String, BarcodeFormat) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode = rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val reader = CodeReader(formats)
            analysis.setAnalyzer(executor) { image ->
                val found = if (done.get()) null else reader.read(image)
                image.close()
                if (found != null && done.compareAndSet(false, true)) {
                    ContextCompat.getMainExecutor(context).execute { latestOnCode.value(found.first, found.second) }
                }
            }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.w("QrScanner", "Camera unavailable", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // The frame to line the code up in: square for a QR code, wide for a card's barcode.
            val square = formats == listOf(BarcodeFormat.QR_CODE)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(if (square) 260.dp else 300.dp, if (square) 260.dp else 150.dp)
                    .border(3.dp, Color.White, RoundedCornerShape(24.dp)),
            )

            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) { PhIcon(Ph.X, "Close scanner", Color.White) }

                Text(
                    hint,
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                )
            }
        }
    }
}

/**
 * Reads a barcode from a camera frame's brightness plane: as it comes, turned a quarter (a card held
 * sideways to the camera), and light-on-dark. Returns its text and type, or null.
 */
private class CodeReader(formats: List<BarcodeFormat>) {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to formats,
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private val oneD = formats.any { it !in listOf(BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417) }

    fun read(image: ImageProxy): Pair<String, BarcodeFormat>? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        if (data.size < rowStride * (height - 1) + width) return null
        val source = PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
        decode(source)?.let { return it }
        decode(source.invert())?.let { return it }
        if (!oneD) return null
        // Turned a quarter: the camera frame is sideways to how the phone is held.
        val turned = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) turned[x * height + (height - 1 - y)] = data[y * rowStride + x]
        return decode(PlanarYUVLuminanceSource(turned, height, width, 0, 0, height, width, false))
    }

    private fun decode(source: com.google.zxing.LuminanceSource): Pair<String, BarcodeFormat>? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).let { it.text to it.barcodeFormat }
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}
