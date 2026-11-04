package au.com.shiftyjelly.pocketcasts.settings.notes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowOutlinedButton
import au.com.shiftyjelly.pocketcasts.compose.components.FormField
import au.com.shiftyjelly.pocketcasts.compose.components.HorizontalDivider
import au.com.shiftyjelly.pocketcasts.compose.components.TextC70
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextH70
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.notes.AiProvider
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesConfig
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesCrashLog
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesExport
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.NotesSettingsScreen(
    onNavigate: (NotesScreen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config = remember { NotesConfig.get(context) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.theme.colors

    var elevenLabsKey by remember { mutableStateOf(config.elevenLabsKey) }
    var provider by remember { mutableStateOf(config.aiProvider) }
    var claudeKey by remember { mutableStateOf(config.claudeKey) }
    var claudeModel by remember { mutableStateOf(config.claudeModel) }
    var openAiKey by remember { mutableStateOf(config.openAiKey) }
    var openAiModel by remember { mutableStateOf(config.openAiModel) }
    var clientId by remember { mutableStateOf(config.tickTickClientId) }
    var clientSecret by remember { mutableStateOf(config.tickTickClientSecret) }
    var pastedUrl by remember { mutableStateOf("") }
    var tickTickStatus by remember { mutableStateOf(tickTickStatusText(config)) }
    var crashLog by remember { mutableStateOf(NotesCrashLog.read(context)) }

    ThemedTopAppBar(title = "聽記設定", onNavigationClick = onBack)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        TextP60(
            text = "API key 只存在這支手機上，不會上傳或同步到其他地方。",
            color = colors.primaryText02,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        SettingsGroup(title = "轉錄") {
            LabeledField(label = "ElevenLabs API key", hint = "用來把前後兩分鐘轉成文字（Scribe v2）") {
                SecretField(value = elevenLabsKey, placeholder = "sk_…") {
                    elevenLabsKey = it
                    config.elevenLabsKey = it
                }
            }
        }

        SettingsGroup(title = "整理重點的 AI") {
            ProviderOption("Claude", "預設模型 ${NotesConfig.DEFAULT_CLAUDE_MODEL}", provider == AiProvider.Claude) {
                provider = AiProvider.Claude
                config.aiProvider = AiProvider.Claude
            }
            ProviderOption("OpenAI", "預設模型 ${NotesConfig.DEFAULT_OPENAI_MODEL}", provider == AiProvider.OpenAi) {
                provider = AiProvider.OpenAi
                config.aiProvider = AiProvider.OpenAi
            }
            TextP60(text = "選的那個沒有 key 時，會自動改用另一個。", color = colors.primaryText02)
            if (provider == AiProvider.Claude) {
                LabeledField(label = "Claude API key") {
                    SecretField(value = claudeKey, placeholder = "sk-ant-…") {
                        claudeKey = it
                        config.claudeKey = it
                    }
                }
                LabeledField(label = "模型（留空用預設）") {
                    FormField(value = claudeModel, placeholder = NotesConfig.DEFAULT_CLAUDE_MODEL, onValueChange = {
                        claudeModel = it
                        config.claudeModel = it
                    })
                }
            } else {
                LabeledField(label = "OpenAI API key") {
                    SecretField(value = openAiKey, placeholder = "sk-…") {
                        openAiKey = it
                        config.openAiKey = it
                    }
                }
                LabeledField(label = "模型（留空用預設）") {
                    FormField(value = openAiModel, placeholder = NotesConfig.DEFAULT_OPENAI_MODEL, onValueChange = {
                        openAiModel = it
                        config.openAiModel = it
                    })
                }
            }
        }

        SettingsGroup(title = "TickTick") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextH50(text = "狀態", modifier = Modifier.weight(1f))
                NotePill(
                    text = tickTickStatus,
                    color = if (config.isTickTickConnected) colors.primaryInteractive01 else colors.primaryText02,
                )
            }
            SetupSteps(
                steps = listOf(
                    "到 developer.ticktick.com 登入，按 Manage Apps 建立一個 app。",
                    "在 app 的編輯頁把下面這串填進「OAuth redirect URL」並儲存。少了這步會出現 redirect_uri 的錯誤。",
                    "把 Client ID 和 Client Secret 貼到下面，按「連接 TickTick」。",
                ),
            )
            CopyRow(value = NotesExport.tickTickRedirectUri)
            LabeledField(label = "Client ID") {
                FormField(value = clientId, placeholder = "Client ID", onValueChange = {
                    clientId = it
                    config.tickTickClientId = it
                })
            }
            LabeledField(label = "Client Secret") {
                SecretField(value = clientSecret, placeholder = "Client Secret") {
                    clientSecret = it
                    config.tickTickClientSecret = it
                }
            }
            RowButton(
                text = if (config.isTickTickConnected) "重新連接 TickTick" else "連接 TickTick",
                onClick = { onNavigate(NotesScreen.TickTickSignIn) },
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
                includePadding = false,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextH50(text = "登入畫面卡住時")
            TextP60(
                text = "在瀏覽器完成授權後，網址列會跳到一個打不開的 localhost 頁面。把那串網址整個貼到這裡也可以完成連接。",
                color = colors.primaryText02,
            )
            FormField(value = pastedUrl, placeholder = "https://localhost/tingji-ticktick?code=…", onValueChange = { pastedUrl = it })
            RowOutlinedButton(
                text = "用網址完成連接",
                includePadding = false,
                onClick = {
                    val code = Uri.parse(pastedUrl.trim()).getQueryParameter("code")
                    if (code.isNullOrBlank()) {
                        tickTickStatus = "網址裡找不到 code"
                    } else {
                        scope.launch {
                            tickTickStatus = try {
                                NotesExport.completeTickTickSignIn(context, code)
                                pastedUrl = ""
                                tickTickStatusText(config)
                            } catch (e: Exception) {
                                NotesExport.userMessage(e)
                            }
                        }
                    }
                },
            )
        }
        crashLog?.let { log ->
            SettingsGroup(title = "上次閃退紀錄") {
                TextP60(
                    text = "app 閃退時，原因會自動記在這裡。按「複製」後貼給 Claude 就能查原因。",
                    color = colors.primaryText02,
                )
                TextP60(text = log.take(900), color = colors.primaryText01)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowOutlinedButton(
                        text = "清除",
                        includePadding = false,
                        onClick = {
                            NotesCrashLog.clear(context)
                            crashLog = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RowButton(
                        text = "複製",
                        onClick = { copyToClipboard(context, log) },
                        includePadding = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
internal fun ColumnScope.TickTickSignInScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { UUID.randomUUID().toString() }
    val authorizeUrl = remember { NotesExport.tickTickAuthorizeUrl(context, state) }
    var status by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(false) }

    fun handleRedirect(url: String): Boolean {
        if (!url.startsWith(NotesExport.tickTickRedirectUri)) return false
        if (handled) return true
        handled = true
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank() || uri.getQueryParameter("state") != state) {
            status = "授權沒有完成，請返回再試一次"
            return true
        }
        status = "連接中…"
        scope.launch {
            try {
                NotesExport.completeTickTickSignIn(context, code)
                onBack()
            } catch (e: Exception) {
                status = NotesExport.userMessage(e)
            }
        }
        return true
    }

    ThemedTopAppBar(title = "連接 TickTick", onNavigationClick = onBack)
    status?.let {
        TextP50(text = it, modifier = Modifier.padding(16.dp))
    }
    if (authorizeUrl == null) {
        TextP50(text = "請先在設定填入 Client ID", modifier = Modifier.padding(16.dp))
        return
    }
    AndroidView(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize(),
        factory = { viewContext ->
            createSignInWebView(viewContext, authorizeUrl) { url -> handleRedirect(url) }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSignInWebView(context: Context, url: String, onUrl: (String) -> Boolean): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return onUrl(request.url.toString())
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (onUrl(url)) {
                    view.stopLoading()
                } else {
                    super.onPageStarted(view, url, favicon)
                }
            }
        }
        loadUrl(url)
    }
}

private fun tickTickStatusText(config: NotesConfig): String {
    if (!config.isTickTickConnected) return "尚未連接"
    val expiresAt = config.tickTickTokenExpiresAt
    return if (expiresAt > 0) {
        "已連接 · 到 ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(expiresAt))}"
    } else {
        "已連接"
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextC70(text = title, isUpperCase = false, modifier = Modifier.padding(top = 8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.theme.colors.primaryUi01, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun LabeledField(label: String, hint: String? = null, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextH50(text = label)
        if (hint != null) {
            TextP60(text = hint, color = MaterialTheme.theme.colors.primaryText02)
        }
        field()
    }
}

@Composable
private fun SecretField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val toggle: @Composable () -> Unit = {
        TextH70(
            text = if (visible) "隱藏" else "顯示",
            color = MaterialTheme.theme.colors.primaryInteractive01,
            modifier = Modifier
                .clickable { visible = !visible }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    FormField(
        value = value,
        placeholder = placeholder,
        onValueChange = onValueChange,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = if (value.isNotEmpty()) toggle else null,
    )
}

@Composable
private fun ProviderOption(label: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.theme.colors.primaryInteractive01),
        )
        Spacer(Modifier.width(4.dp))
        Column {
            TextH40(text = label)
            TextP60(text = detail, color = MaterialTheme.theme.colors.primaryText02)
        }
    }
}

@Composable
private fun SetupSteps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row {
                TextH50(
                    text = "${index + 1}",
                    color = MaterialTheme.theme.colors.primaryInteractive01,
                    modifier = Modifier.width(20.dp),
                )
                TextP50(text = step)
            }
        }
    }
}

@Composable
private fun CopyRow(value: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.primaryUi02, RoundedCornerShape(8.dp))
            .clickable { copyToClipboard(context, value) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TextP50(text = value, modifier = Modifier.weight(1f), maxLines = 1)
        TextH70(text = "複製", color = colors.primaryInteractive01)
    }
}
