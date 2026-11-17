package `in`.vidyadhan.academy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos are kept inside the app's private storage (not in the phone gallery). */
object Photos {
    fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)
    fun delete(ctx: Context, name: String) { if (name.isNotBlank()) file(ctx, name).delete() }

    /** Copies a picture into the app, turned upright and shrunk to a sensible size. Returns its file name. */
    suspend fun import(ctx: Context, uri: Uri, maxSide: Int = 1600): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
        val rotation = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
                }
            }
        }.getOrDefault(0f)
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val name = UUID.randomUUID().toString() + ".jpg"
        file(ctx, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }

    fun load(ctx: Context, name: String, maxSide: Int): Bitmap? {
        val f = file(ctx, name)
        if (name.isBlank() || !f.exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Composable
fun rememberPhoto(name: String, maxSide: Int = 400): ImageBitmap? {
    val ctx = LocalContext.current
    var img by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name, maxSide) { img = if (name.isBlank()) null else withContext(Dispatchers.IO) { Photos.load(ctx, name, maxSide)?.asImageBitmap() } }
    return img
}

/** Opens a sheet to take a photo or pick one from the gallery. */
class PhotoPicker(val open: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPhotoPicker(title: String, hasPhoto: Boolean, onPicked: (String) -> Unit, onRemove: () -> Unit): PhotoPicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = target
        if (ok && u != null) scope.launch { runCatching { Photos.import(ctx, u) }.onSuccess(onPicked) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { runCatching { Photos.import(ctx, uri) }.onSuccess(onPicked) }
    }
    if (sheet) ModalBottomSheet({ sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(title)
            OptionRow(Icons.Rounded.PhotoCamera, Brand.Blue600, "Take a photo", "Use the camera") {
                sheet = false
                val f = File(ctx.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
                val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                target = u
                runCatching { camera.launch(u) }
            }
            OptionRow(Icons.Rounded.PhotoLibrary, Brand.Violet700, "Choose from gallery", "Pick an existing photo") {
                sheet = false
                runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            if (hasPhoto) OptionRow(Icons.Rounded.DeleteOutline, Brand.Red600, "Remove photo", null) { sheet = false; onRemove() }
        }
    }
    return remember { PhotoPicker { sheet = true } }
}

/** Full-screen view of a photo. */
@Composable
fun PhotoViewer(name: String, onClose: () -> Unit) {
    val img = rememberPhoto(name, 2000)
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            Box(Modifier.statusBarsPadding().padding(12.dp)) { GlassIconButton(Icons.Rounded.Close, "Close", onClose) }
        }
    }
}
