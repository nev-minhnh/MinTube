package com.hhst.youtubelite.gallery

import com.hhst.youtubelite.diagnostics.*

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryActivity : ComponentActivity() {
    private var pendingSave by mutableStateOf<File?>(null)
    private val saveImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val image = pendingSave; pendingSave = null
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && image != null && uri != null) lifecycleScope.launch {
            val operation = AppLog.operation(AppLog.Category.GALLERY, "image_save")
            val success = withContext(Dispatchers.IO) { runCatching {
                contentResolver.openOutputStream(uri)?.use { output -> image.inputStream().use { it.copyTo(output) } }
                    ?: error("No output stream")
            }.onFailure { operation.finish(DiagnosticOutcome.FAILURE, "image_save_failed", it) }.isSuccess }
            if (success) operation.finish(DiagnosticOutcome.SUCCESS, fields = mapOf("bytes" to image.length()))
            Toast.makeText(this@GalleryActivity, if (success) R.string.gallery_saved else R.string.gallery_failed, Toast.LENGTH_SHORT).show()
        } else AppLog.event(AppLog.Category.GALLERY, "image_save_cancelled", mapOf("outcome" to "CANCELLED"))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The viewer is always dark: light system-bar icons in both app themes.
        enableEdgeToEdge(androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        com.hhst.youtubelite.ui.PhoneOrientation.lockOnPhone(this)
        pendingSave = savedInstanceState?.getString("pending_save")?.let(::File)
            ?.takeIf { it.isFile && it.parentFile == File(cacheDir, "gallery") }
        val urls = intent.getStringArrayListExtra("images").orEmpty()
        if (urls.size !in 1..30 || urls.any { !GalleryImages.allowed(it) }) { finish(); return }
        lifecycleScope.launch(Dispatchers.IO) { GalleryImages.clean(this@GalleryActivity) }
        setContent { com.hhst.youtubelite.ui.design.AppTheme { Gallery(urls, intent.getIntExtra("index", 0).coerceIn(urls.indices)) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_save", pendingSave?.path)
        super.onSaveInstanceState(outState)
    }

    private fun saveCurrent(image: GalleryImages.Image) {
        pendingSave = image.file
        runCatching { saveImage.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = image.mime
            putExtra(Intent.EXTRA_TITLE, "LiTube-${System.currentTimeMillis()}.${image.extension}")
        }) }.onFailure {
            AppLog.event(AppLog.Category.GALLERY, "image_save_picker_failed", failure = it)
            pendingSave = null
            Toast.makeText(this@GalleryActivity, R.string.gallery_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun shareCurrent(image: GalleryImages.Image) {
        val operation = AppLog.operation(AppLog.Category.GALLERY, "image_share")
        runCatching {
            val shared = withContext(Dispatchers.IO) {
                File(image.file.parentFile, "share-${System.currentTimeMillis()}.${image.extension}").also { image.file.copyTo(it) }
            }
            val uri = FileProvider.getUriForFile(this@GalleryActivity, "$packageName.download.fileprovider", shared)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = image.mime; putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.share)))
            operation.finish(DiagnosticOutcome.SUCCESS, "chooser_launched")
        }.onFailure { operation.finish(DiagnosticOutcome.FAILURE, "image_share_failed", it); Toast.makeText(this@GalleryActivity, R.string.gallery_failed, Toast.LENGTH_SHORT).show() }
    }

    /**
     * Full-screen viewer, always dark whatever the app theme: images edge to edge, a top chrome of
     * 48dp controls (close, page count, save, share) over a gradient, and page dots at the bottom.
     */
    @Composable private fun Gallery(urls: List<String>, initial: Int) {
        val pager = rememberPagerState(initialPage = initial, pageCount = { urls.size })
        var pageImage by remember { mutableStateOf<Pair<Int, GalleryImages.Image>?>(null) }
        val current = pageImage?.takeIf { it.first == pager.currentPage }?.second
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var scaledPage by remember { mutableStateOf<Pair<Int, Float>?>(null) }
        val zoomed = scaledPage?.let { it.first == pager.currentPage && it.second > 1f } == true
        val actionsEnabled = current != null && !busy && pendingSave == null
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                GalleryPage(urls[page], page, pager.currentPage == page) { image, scale ->
                    pageImage = image?.let { page to it }; scaledPage = page to scale
                }
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = MinSpace.xs, vertical = MinSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(AppIcon.Close, stringResource(R.string.close), color = Color.White) { finish() }
                Box(Modifier.weight(1f).padding(horizontal = MinSpace.sm)) {
                    AppText("${pager.currentPage + 1} / ${urls.size}", MinType.Label,
                        Modifier.background(Color.White.copy(alpha = 0.16f), MinRadius.Full)
                            .padding(horizontal = MinSpace.md, vertical = MinSpace.xs).testTag("gallery-page-count"),
                        color = Color.White, maxLines = 1)
                }
                AppIconButton(AppIcon.Download, stringResource(R.string.gallery_save), color = Color.White, enabled = actionsEnabled) {
                    current?.let(::saveCurrent)
                }
                AppIconButton(AppIcon.Share, stringResource(R.string.share), color = Color.White, enabled = actionsEnabled, pending = busy) {
                    val image = current ?: return@AppIconButton
                    busy = true
                    scope.launch { shareCurrent(image); busy = false }
                }
            }
            if (urls.size in 2..12) Row(
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = MinSpace.lg)
                    .background(Color.Black.copy(alpha = 0.45f), MinRadius.Full)
                    .padding(horizontal = MinSpace.sm, vertical = MinSpace.sm)
                    .clearAndSetSemantics {}.testTag("gallery-page-dots"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(urls.size) { index ->
                    Box(Modifier.size(if (index == pager.currentPage) 8.dp else 6.dp)
                        .background(if (index == pager.currentPage) Color.White else Color.White.copy(alpha = 0.45f), CircleShape))
                }
            }
        }
    }

    @Composable private fun GalleryPage(url: String, page: Int, active: Boolean, onState: (GalleryImages.Image?, Float) -> Unit) {
        var retry by remember { mutableIntStateOf(0) }
        var image by remember { mutableStateOf<GalleryImages.Image?>(null) }
        var failed by remember { mutableStateOf(false) }
        var scale by rememberSaveable { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(url, retry) {
            failed = false
            image = withContext(Dispatchers.IO) { runCatching { GalleryImages.load(this@GalleryActivity, url) }.getOrNull() }
            failed = image == null
        }
        LaunchedEffect(active, image, scale) { if (active) onState(image, scale) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val loaded = image
            if (loaded != null) {
                val zoomLabel = stringResource(R.string.gallery_zoom)
                val resetLabel = stringResource(R.string.gallery_reset_zoom)
                Image(loaded.bitmap.asImageBitmap(), stringResource(R.string.gallery_image, page + 1),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(zoomLabel) { scale = (scale * 1.5f).coerceAtMost(5f); true },
                                CustomAccessibilityAction(resetLabel) { scale = 1f; offset = Offset.Zero; true },
                            )
                        }
                        .pointerInput(loaded) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                        .pointerInput(loaded) { awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    offset = if (scale <= 1f) Offset.Zero else (offset + event.calculatePan()).let {
                                        Offset(it.x.coerceIn(-size.width*(scale-1)/2, size.width*(scale-1)/2), it.y.coerceIn(-size.height*(scale-1)/2, size.height*(scale-1)/2))
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        } }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
            } else if (failed) Column(Modifier.padding(MinSpace.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(AppIcon.Error, Modifier.size(32.dp), color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(MinSpace.md))
                MinButton(stringResource(R.string.gallery_retry), { retry++ }, kind = MinButtonKind.Tonal,
                    icon = AppIcon.Refresh, testTag = "gallery-retry")
            }
            else ActionProgress(Modifier.size(32.dp), color = Color.White)
        }
    }

    companion object {
        fun open(context: Context, urls: List<String>, index: Int) {
            context.startActivity(Intent(context, GalleryActivity::class.java).apply {
                putStringArrayListExtra("images", ArrayList(urls)); putExtra("index", index)
            })
        }
    }
}
