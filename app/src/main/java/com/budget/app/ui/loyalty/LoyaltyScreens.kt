package com.budget.app.ui.loyalty

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.budget.app.data.remote.LoyaltyCardDto
import com.budget.app.ui.phone.ActionButton
import com.budget.app.ui.phone.DropdownField
import com.budget.app.ui.phone.PageCard
import com.budget.app.ui.setup.BarcodeScanner
import com.budget.app.ui.setup.CARD_FORMATS
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream
import java.io.File

/** Tile colors, the same as the web's. */
val CARD_COLORS = listOf("#ffffff", "#7c2d12", "#b91c1c", "#b45309", "#166534", "#0f766e", "#1e3a8a", "#2851d6", "#6b21a8", "#be185d", "#334155", "#111827")

private val FORMAT_LABELS = linkedMapOf(
    "CODE_128" to "Code 128 (most stores)", "CODE_39" to "Code 39", "CODE_93" to "Code 93", "CODABAR" to "Codabar",
    "EAN_13" to "EAN-13", "EAN_8" to "EAN-8", "UPC_A" to "UPC-A", "UPC_E" to "UPC-E", "ITF" to "ITF",
    "QR_CODE" to "QR code", "PDF_417" to "PDF417", "AZTEC" to "Aztec", "DATA_MATRIX" to "Data Matrix",
)

private fun colorOf(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF334155))

/** Light colors (like white) get dark text and an outline. */
private fun isLight(hex: String): Boolean {
    val c = colorOf(hex)
    return 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.67f
}

/** PNG and WebP images may be see-through (a logo); JPEGs are pictures. */
private fun isLogo(file: File): Boolean = runCatching {
    val b = file.inputStream().use { s -> ByteArray(12).also { s.read(it) } }
    (b[0] == 0x89.toByte() && b[1] == 0x50.toByte()) || (b[0] == 0x52.toByte() && b[8] == 0x57.toByte())
}.getOrDefault(false)

private fun initials(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/**
 * The Loyalty side of the Cards tab: the grid of cards, one card shown big to scan, and adding or
 * changing a card. [openId] is set by a long-press shortcut ("" for the list).
 */
@Composable
fun LoyaltyTab(vm: LoyaltyViewModel, ui: LoyaltyUi, toggle: @Composable () -> Unit, openId: String?, onOpened: () -> Unit) {
    var showing by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LoyaltyCardDto?>(null) }
    LaunchedEffect(openId, ui.cards) {
        if (openId != null) {
            if (openId.isNotEmpty() && ui.cards.any { it.id == openId }) showing = openId
            if (openId.isEmpty() || ui.loaded) onOpened()
        }
    }
    val card = ui.cards.firstOrNull { it.id == showing }
    when {
        editing != null -> CardEdit(vm, ui, editing!!, onBack = { editing = null; vm.clearMessage() }, onSaved = { saved ->
            editing = null
            showing = saved?.id
        })
        card != null -> CardShow(card, vm.imageFile(card), onBack = { showing = null }, onEdit = { editing = card }, onShown = { vm.used(card) })
        else -> CardGrid(ui, vm, toggle, onOpen = { showing = it.id }, onAdd = { editing = LoyaltyCardDto() })
    }
}

@Composable
private fun CardGrid(ui: LoyaltyUi, vm: LoyaltyViewModel, toggle: @Composable () -> Unit, onOpen: (LoyaltyCardDto) -> Unit, onAdd: () -> Unit) {
    val c = Budget.colors
    var query by remember { mutableStateOf("") }
    val shown = ui.cards.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) { toggle() }
        if (ui.cards.size > 4) item(span = { GridItemSpan(2) }) {
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search cards") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (ui.loaded && ui.cards.isEmpty()) item(span = { GridItemSpan(2) }) {
            Text("No cards yet. Add one by scanning its barcode.", color = c.muted, modifier = Modifier.padding(4.dp))
        }
        items(shown, key = { it.id }) { card -> Tile(card, vm.imageFile(card), Modifier.clickable(role = Role.Button) { onOpen(card) }) }
        item(key = "add") {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(14.dp)).border(2.dp, c.line, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Text("+ Add a card", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}

/** A card as a tile: its image, or its color with the store's name. A see-through image (a logo) sits whole on the color. */
@Composable
private fun Tile(card: LoyaltyCardDto, image: File?, modifier: Modifier = Modifier, big: Boolean = false) {
    val bitmap = remember(image?.path) { image?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
    val logo = remember(image?.path) { image?.let { isLogo(it) } ?: false }
    val light = isLight(card.color)
    val ink = if (light) Color(0xFF111827) else Color.White
    val shape = RoundedCornerShape(if (big) 18.dp else 14.dp)
    Box(
        modifier.fillMaxWidth().aspectRatio(1.586f).clip(shape).background(colorOf(card.color))
            .then(if (light) Modifier.border(1.dp, Color(0xFFE5E7EB), shape) else Modifier),
    ) {
        if (bitmap != null) {
            if (logo) Image(bitmap, card.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(if (big) 22.dp else 12.dp))
            else Image(bitmap, card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(if (big) 40.dp else 30.dp).background(if (light) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Text(initials(card.name), color = ink, fontWeight = FontWeight.ExtraBold, fontSize = if (big) 15.sp else 12.sp)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                card.memberName?.let { Text(it, color = ink.copy(alpha = 0.85f), fontSize = 11.sp) }
                Text(card.name, color = ink, fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The barcode as a bitmap, or null when the number can't be drawn in that type. */
private fun barcode(number: String, format: String, width: Int): Bitmap? = runCatching {
    val f = BarcodeFormat.valueOf(format)
    val square = f in listOf(BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX)
    val height = if (square) width else if (f == BarcodeFormat.PDF_417) width / 3 else width * 2 / 5
    val m = MultiFormatWriter().encode(number, f, width, height, mapOf(EncodeHintType.MARGIN to 2))
    val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** One card, ready to scan: full brightness and the screen kept on while it's open. */
@Composable
private fun CardShow(card: LoyaltyCardDto, image: File?, onBack: () -> Unit, onEdit: () -> Unit, onShown: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var numberOnly by remember { mutableStateOf(false) }
    val code = remember(card.number, card.format) { barcode(card.number, card.format, 900) }
    LaunchedEffect(card.id) { onShown() }
    DisposableEffect(Unit) {
        val window = context.activity()?.window
        val before = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) { PhIcon(Ph.CARET_LEFT, "Back", Color.Black) }
            Text(card.name, color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(horizontal = 16.dp)) { Tile(card, image, big = true) }
        Column(
            Modifier.padding(16.dp).fillMaxWidth().border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp)).padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!numberOnly && code != null) {
                Image(code.asImageBitmap(), "Barcode", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            } else if (code == null) {
                Text("This number can't be drawn as ${FORMAT_LABELS[card.format] ?: card.format}. Show the number, or change the barcode type.", color = Color(0xFF6B7280), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Text(card.number.chunked(4).joinToString(" "), color = Color.Black, fontSize = if (numberOnly) 34.sp else 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            Chip("Edit", onEdit)
            Chip(if (numberOnly) "Show the barcode" else "Number only") { numberOnly = !numberOnly }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.border(1.5.dp, Color(0xFFE5E7EB), RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** An image, shrunk to at most 1000 px wide: PNG when it has see-through parts (a logo), else JPEG. */
private fun shrink(context: Context, uri: Uri): ByteArray? = runCatching {
    val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val scale = minOf(1f, 1000f / src.width)
    val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
    ByteArrayOutputStream().use { out ->
        if (seeThrough(bmp)) bmp.compress(Bitmap.CompressFormat.PNG, 100, out) else bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}.getOrNull()

private fun seeThrough(bmp: Bitmap): Boolean {
    if (!bmp.hasAlpha()) return false
    val row = IntArray(bmp.width)
    for (y in 0 until bmp.height) {
        bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
        if (row.any { (it ushr 24) < 250 }) return true
    }
    return false
}

/** Add or change a card: scan its barcode or type the number, then the store and its look. */
@Composable
private fun CardEdit(vm: LoyaltyViewModel, ui: LoyaltyUi, card: LoyaltyCardDto, onBack: () -> Unit, onSaved: (LoyaltyCardDto?) -> Unit) {
    BackHandler(onBack = onBack)
    val c = Budget.colors
    val context = LocalContext.current
    val isNew = card.id.isBlank()
    var name by remember { mutableStateOf(card.name) }
    var number by remember { mutableStateOf(card.number) }
    var format by remember { mutableStateOf(card.format) }
    var member by remember { mutableStateOf(card.memberName.orEmpty()) }
    var color by remember { mutableStateOf(card.color) }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var removeImage by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) scanning = true }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) shrink(context, uri)?.let {
            jpeg = it
            removeImage = false
        }
    }
    val startScan = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
        else askCamera.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (isNew) startScan() }
    if (scanning) {
        BarcodeScanner(
            formats = CARD_FORMATS,
            hint = "Line up the barcode on the card.",
            onCode = { text, f ->
                scanning = false
                number = text
                format = f.name
                found = "Found a barcode · ${FORMAT_LABELS[f.name] ?: f.name}"
            },
            onClose = { scanning = false },
        )
    }
    val preview = remember(jpeg) { jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val existing = if (removeImage) null else vm.imageFile(card)

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) { PhIcon(Ph.CARET_LEFT, "Back", c.ink) }
            Text(if (isNew) "Add a Card" else "Edit Card", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.message?.let { Text(it, color = c.warn, fontSize = 13.sp) }
            PageCard {
                ActionButton("Scan the barcode", onClick = startScan)
                found?.let { Text("✓ $it", color = com.budget.app.ui.phone.okColor(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Field("Card number") { OutlinedTextField(value = number, onValueChange = { number = it.take(200) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                Field("Barcode type") { DropdownField(value = format, placeholder = "Code 128", options = FORMAT_LABELS.toList(), onPick = { format = it }) }
            }
            PageCard {
                Field("Store") { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                Field("Member (optional)") { OutlinedTextField(value = member, onValueChange = { member = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                Field("Look") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        CARD_COLORS.forEach { hex ->
                            Box(
                                Modifier.size(32.dp).background(colorOf(hex), CircleShape)
                                    .then(if (color == hex) Modifier.border(3.dp, c.ink, CircleShape) else Modifier.border(1.dp, Color(0xFFE5E7EB), CircleShape))
                                    .clickable(role = Role.RadioButton) { color = hex },
                            )
                        }
                    }
                }
                val tile = LoyaltyCardDto(name = name.ifBlank { "Store" }, color = color, memberName = member.ifBlank { null })
                if (preview != null) {
                    val pngPicked = jpeg!!.size > 1 && jpeg!![0] == 0x89.toByte()
                    Box(Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(14.dp)).background(colorOf(color)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp))) {
                        if (pngPicked) Image(preview, "Card image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                        else Image(preview, "Card image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Tile(tile.copy(imageVersion = if (existing != null) card.imageVersion else 0, id = card.id), existing)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip(if (preview != null || existing != null) "Change image" else "Add an image") {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (preview != null || existing != null) Chip("Remove image") {
                        jpeg = null
                        removeImage = true
                    }
                }
            }
            ActionButton(if (isNew) "Save card" else "Save changes", enabled = !ui.busy && name.isNotBlank() && number.isNotBlank()) {
                vm.save(card.copy(name = name.trim(), number = number.trim(), format = format, color = color, memberName = member.trim().ifBlank { null }), jpeg, removeImage) { onSaved(it) }
            }
            if (!isNew) {
                Text(
                    if (confirmDelete) "Tap again to delete ${card.name}" else "Delete card",
                    color = if (confirmDelete) c.warn else c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        if (confirmDelete) vm.delete(card) { onSaved(null) } else confirmDelete = true
                    }.padding(12.dp),
                )
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Budget.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}
