package com.jetbrains.kmpapp.screens.other

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import com.jetbrains.kmpapp.data.qr.QrImageGenerator
import com.jetbrains.kmpapp.data.qr.QrMatrix
import com.jetbrains.kmpapp.data.qr.SemesterQrCodec
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.data.files.SemesterFileExchange
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler

/**
 * Экран «Моё расписание»: единственный локальный семестр.
 *
 * Карточка заголовка + файловый импорт/экспорт JSON + удаление;
 * ниже — акцентная кнопка веб-конфигуратора (сборка семестра).
 */
private const val WEB_CONFIGURATOR_URL =
    "https://vibe-moments-technologies.github.io/universal-schedule-app/configurator/"

@Composable
fun ManageSchedulesScreen(
    viewModel: OtherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)
    val semester by viewModel.semester.collectAsState()
    val uriHandler = LocalUriHandler.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    // QR: показать код семестра (передача) — payload или ошибка размера.
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var qrTooBig by remember { mutableStateOf(false) }
    // QR: приём — поле вставки отсканированной строки.
    var showQrImport by remember { mutableStateOf(false) }
    var qrImportDraft by remember { mutableStateOf("") }
    var qrImportError by remember { mutableStateOf<String?>(null) }

    fun importFromFile() {
        SemesterFileExchange.pickJsonFile { text ->
            if (text == null) return@pickJsonFile
            val error = viewModel.importSemesterJson(text)
            importError = error
        }
    }

    fun exportToFile() {
        val json = viewModel.exportSemesterJson() ?: return
        val group = semester?.group?.takeIf { it.isNotBlank() }?.replace(Regex("[^\\wа-яА-Я-]+"), "_")
            ?: "semester"
        SemesterFileExchange.shareFile("schedule-$group.json", json)
    }

    fun showQr() {
        val json = viewModel.exportSemesterJson()
        if (json == null) {
            qrTooBig = false
            return
        }
        val payload = SemesterQrCodec.encode(json)
        if (payload == null) {
            qrTooBig = true
        } else {
            qrTooBig = false
            qrPayload = payload
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Моё расписание",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (semester == null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Семестр не собран", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Соберите семестр в веб-конфигураторе — на компьютере или прямо здесь, — скачайте JSON и импортируйте его кнопкой ниже. Дальше расписание живёт на устройстве.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val info = semester
                if (info != null) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "${info.group} · ${info.semesterNumber} сем.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                listOfNotNull(
                                    info.university.ifBlank { null },
                                    "Курс ${info.course}",
                                    "Недель: ${info.weeksCount}",
                                    "Начало: ${info.startDate}",
                                    "Занятий в шаблоне: ${info.entries.size}"
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { exportToFile() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Экспортировать в файл")
                    }

                    OutlinedButton(
                        onClick = { showQr() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Передать по QR")
                    }

                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Удалить семестр")
                    }
                }
            }

            Button(
                onClick = { importFromFile() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Импортировать из файла")
            }

            OutlinedButton(
                onClick = { showQrImport = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Получить по QR")
            }
            importError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Акцентная кнопка: сборка семестра в вебе.
            Button(
                onClick = { uriHandler.openUri(WEB_CONFIGURATOR_URL) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (semester == null) "Открыть веб-конфигуратор" else "Пересобрать в веб-конфигураторе")
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить семестр?") },
            text = { Text("Расписание и недельный шаблон будут удалены. Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSemester()
                    showDeleteDialog = false
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    // QR-передача: код семестра на весь экран — наведите камеру другого
    // устройства или отсканируйте из веб-конфигуратора.
    if (qrPayload != null) {
        QrShowDialog(
            payload = qrPayload ?: "",
            onDismiss = { qrPayload = null }
        )
    }

    if (qrTooBig) {
        AlertDialog(
            onDismissRequest = { qrTooBig = false },
            title = { Text("Семестр не влезает в QR") },
            text = {
                Text("Расписание слишком большое для одного QR-кода. Передайте его файлом или через веб-конфигуратор.")
            },
            confirmButton = {
                TextButton(onClick = { qrTooBig = false }) { Text("Понятно") }
            }
        )
    }

    // QR-приём: вставка строки из системного QR-ридера (Камера/Пикер
    // распознают QR и позволяют скопировать текст).
    if (showQrImport) {
        AlertDialog(
            onDismissRequest = { showQrImport = false },
            title = { Text("Получить по QR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Отсканируйте QR камерой телефона (или стандартным сканером) и вставьте строку сюда — она начинается с USCH1:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = qrImportDraft,
                        onValueChange = {
                            qrImportDraft = it
                            qrImportError = null
                        },
                        placeholder = { Text("USCH1:…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    qrImportError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val json = SemesterQrCodec.decode(qrImportDraft.trim())
                    if (json == null) {
                        qrImportError = "Не удалось прочитать код: проверьте, что строка скопирована целиком"
                    } else {
                        val error = viewModel.importSemesterJson(json)
                        if (error == null) {
                            showQrImport = false
                            qrImportDraft = ""
                            qrImportError = null
                        } else {
                            qrImportError = error
                        }
                    }
                }) { Text("Импортировать") }
            },
            dismissButton = {
                TextButton(onClick = { showQrImport = false }) { Text("Отмена") }
            }
        )
    }
}

/** Полноэкранный показ QR-кода семестра. */
@Composable
private fun QrShowDialog(
    payload: String,
    onDismiss: () -> Unit
) {
    val matrix = remember(payload) { QrImageGenerator.generate(payload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR-код расписания") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (matrix == null) {
                    Text("Не удалось построить код")
                } else {
                    QrCanvas(matrix = matrix, modifier = Modifier.size(280.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Наведите камеру другого устройства с приложением или веб-конфигуратором",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/** Рисуем QR-матрицу Canvas'ом: белая подложка + тёмные модули. */
@Composable
private fun QrCanvas(matrix: QrMatrix, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawRect(androidx.compose.ui.graphics.Color.White)
        val cell = size.width / matrix.width
        val dark = androidx.compose.ui.graphics.Color.Black
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = dark,
                        topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell)
                    )
                }
            }
        }
    }
}
