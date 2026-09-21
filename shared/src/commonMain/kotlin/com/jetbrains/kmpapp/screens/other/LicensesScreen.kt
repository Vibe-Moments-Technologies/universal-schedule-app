package com.jetbrains.kmpapp.screens.other

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.AppVersion
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)
    val uriHandler = LocalUriHandler.current

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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Лицензии и источники",
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "Лицензия приложения") {
                LicenseItem(
                    title = "GNU GPL v3",
                    description = "Приложение — свободное ПО с открытым исходным кодом.",
                    onClick = { uriHandler.openUri("${AppVersion.GITHUB_REPO_URL}/blob/main/LICENSE") }
                )
            }

            SectionCard(title = "Сторонние библиотеки") {
                LicenseItem(
                    title = "Compose Multiplatform, Kotlin, Coroutines, Ktor, kotlinx.serialization",
                    description = "Apache License 2.0 — JetBrains s.r.o. и участники Kotlin."
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LicenseItem(
                    title = "Coil 3",
                    description = "Apache License 2.0 — Coil Contributors."
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LicenseItem(
                    title = "AppMetrica SDK",
                    description = "Проприетарная лицензия Yandex."
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LicenseItem(
                    title = "Material Design 3",
                    description = "Apache License 2.0 — Google LLC."
                )
            }

            SectionCard(title = "Данные") {
                LicenseItem(
                    title = "Данные расписания занятий",
                    description = "Официальный Schedule API университета."
                )
            }

            SectionCard(title = "Юридические документы") {
                LicenseItem(
                    title = "Условия использования",
                    onClick = { uriHandler.openUri("${AppVersion.GITHUB_REPO_URL}/blob/main/TERMS.md") }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LicenseItem(
                    title = "Политика конфиденциальности",
                    onClick = { uriHandler.openUri("${AppVersion.GITHUB_REPO_URL}/blob/main/PRIVACY.md") }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LicenseItem(
                    title = "Политика обработки персональных данных",
                    onClick = { uriHandler.openUri("${AppVersion.GITHUB_REPO_URL}/blob/main/PDP_POLICY.md") }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LicenseItem(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            description?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Открыть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
