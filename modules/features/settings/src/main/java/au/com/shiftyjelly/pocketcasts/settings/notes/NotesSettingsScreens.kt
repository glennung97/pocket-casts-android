package au.com.shiftyjelly.pocketcasts.settings.notes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.notes.AiProvider
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesConfig
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesExport
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun NotesSettingsScreen(
    onNavigate: (NotesScreen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config = remember { NotesConfig.get(context) }
    val scope = rememberCoroutineScope()

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

    ThemedTopAppBar(title = "聽記設定", onNavigationClick = onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Hint("API key 只存在這支手機上，不會上傳到任何地方。")

        SectionTitle("轉錄")
        SecretField("ElevenLabs API key", elevenLabsKey) {
            elevenLabsKey = it
            config.elevenLabsKey = it
        }

        SectionTitle("整理重點的 AI")
        ProviderOption("Claude", provider == AiProvider.Claude) {
            provider = AiProvider.Claude
            config.aiProvider = AiProvider.Claude
        }
        ProviderOption("OpenAI", provider == AiProvider.OpenAi) {
            provider = AiProvider.OpenAi
            config.aiProvider = AiProvider.OpenAi
        }
        Hint("選的那個沒有 key 時，會自動改用另一個。")
        SecretField("Claude API key", claudeKey) {
            claudeKey = it
            config.claudeKey = it
        }
        PlainField("Claude 模型（留空用 ${NotesConfig.DEFAULT_CLAUDE_MODEL}）", claudeModel) {
            claudeModel = it
            config.claudeModel = it
        }
        SecretField("OpenAI API key", openAiKey) {
            openAiKey = it
            config.openAiKey = it
        }
        PlainField("OpenAI 模型（留空用 ${NotesConfig.DEFAULT_OPENAI_MODEL}）", openAiModel) {
            openAiModel = it
            config.openAiModel = it
        }

        SectionTitle("TickTick")
        Hint(
            "1. 到 developer.ticktick.com 建立一個 app\n" +
                "2. Redirect URL 填下面這串（點一下複製）\n" +
                "3. 把 Client ID 和 Client Secret 貼到這裡，再按「連接 TickTick」",
        )
        Text(
            NotesExport.tickTickRedirectUri,
            fontSize = 13.sp,
            color = MaterialTheme.theme.colors.primaryInteractive01,
            modifier = Modifier.clickable { copyToClipboard(context, NotesExport.tickTickRedirectUri) },
        )
        PlainField("Client ID", clientId) {
            clientId = it
            config.tickTickClientId = it
        }
        SecretField("Client Secret", clientSecret) {
            clientSecret = it
            config.tickTickClientSecret = it
        }
        Text(tickTickStatus, fontSize = 13.sp, color = MaterialTheme.theme.colors.primaryText02)
        Button(
            onClick = { onNavigate(NotesScreen.TickTickSignIn) },
            enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
        ) { Text(if (config.isTickTickConnected) "重新連接 TickTick" else "連接 TickTick") }
        Hint("如果登入畫面卡住：在瀏覽器完成授權後，把跳轉後的網址整串貼到下面。")
        PlainField("授權後的網址", pastedUrl) { pastedUrl = it }
        TextButton(
            onClick = {
                val code = Uri.parse(pastedUrl.trim()).getQueryParameter("code")
                if (code.isNullOrBlank()) {
                    tickTickStatus = "網址裡找不到 code，請確認貼上的是跳轉後的整串網址"
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
            enabled = pastedUrl.isNotBlank(),
        ) { Text("用網址完成連接") }
    }
}

@Composable
internal fun TickTickSignInScreen(onBack: () -> Unit) {
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
        Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.theme.colors.primaryText01)
    }
    if (authorizeUrl == null) {
        Text("請先在設定填入 Client ID", modifier = Modifier.padding(16.dp), color = MaterialTheme.theme.colors.primaryText01)
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
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
        "已連接，授權到 ${DateFormat.getDateInstance().format(Date(expiresAt))}"
    } else {
        "已連接"
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.theme.colors.primaryText01,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.theme.colors.primaryText02)
}

@Composable
private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isNotEmpty()) {
            Text(
                if (visible) "隱藏" else "顯示",
                fontSize = 12.sp,
                color = MaterialTheme.theme.colors.primaryInteractive01,
                modifier = Modifier
                    .clickable { visible = !visible }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PlainField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun NoteField(value: String, onValueChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text("我的筆記") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProviderOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, color = MaterialTheme.theme.colors.primaryText01)
    }
}
