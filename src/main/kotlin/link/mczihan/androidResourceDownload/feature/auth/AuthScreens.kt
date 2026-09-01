package link.mczihan.androidResourceDownload.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.feature.settings.AboutScreen

// GitHub Octocat 图标（与安卓端登录页一致）
private val GithubLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GithubLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M12,0.297C5.37,0.297 0,5.67 0,12.297c0,5.303 3.438,9.8 8.205,11.385 " +
                "0.6,0.113 0.82,-0.258 0.82,-0.577 0,-0.285 -0.01,-1.04 -0.015,-2.04 " +
                "-3.338,0.724 -4.042,-1.61 -4.042,-1.61C4.422,18.07 3.633,17.7 3.633,17.7 " +
                "c-1.087,-0.744 0.084,-0.729 0.084,-0.729 1.205,0.084 1.838,1.236 1.838,1.236 " +
                "1.07,1.835 2.809,1.305 3.495,0.998 0.108,-0.776 0.417,-1.305 0.76,-1.605 " +
                "-2.665,-0.3 -5.466,-1.332 -5.466,-5.93 0,-1.31 0.465,-2.38 1.235,-3.22 " +
                "-0.135,-0.303 -0.54,-1.523 0.105,-3.176 0,0 1.005,-0.322 3.3,1.23 " +
                "0.96,-0.267 1.98,-0.399 3,-0.405 1.02,0.006 2.04,0.138 3,0.405 " +
                "2.28,-1.552 3.285,-1.23 3.285,-1.23 0.645,1.653 0.24,2.873 0.12,3.176 " +
                "0.765,0.84 1.23,1.91 1.23,3.22 0,4.61 -2.805,5.625 -5.475,5.92 " +
                "0.42,0.36 0.81,1.096 0.81,2.22 0,1.606 -0.015,2.896 -0.015,3.286 " +
                "0,0.315 0.21,0.69 0.825,0.57C20.565,22.092 24,17.592 24,12.297 " +
                "24,5.67 18.627,0.297 12,0.297Z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
fun LoginScreen(
    onGithubLogin: () -> Unit,
    onQqLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    message: String? = null,
    onPolicyAccepted: () -> Unit = {},
) {
    var agreementAccepted by remember { mutableStateOf(false) }
    var showAgreementError by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "资源下载",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "任意 GitHub 账号均可登录，访问权限由服务端分配。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (BuildConfig.DEMO_MODE) {
                    "当前为演示模式，登录后使用演示账号和数据。"
                } else {
                    "真实登录已启用：GitHub 授权由后端验证。"
                },
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (BuildConfig.DEMO_MODE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    if (agreementAccepted) onGithubLogin() else showAgreementError = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Icon(GithubLogo, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("使用 GitHub 登录")
            }
            OutlinedButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = false,
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("使用 QQ 登录")
            }
            Text(
                text = "因网站备案未完成，QQ 登录暂不可用",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = { showAbout = true },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("关于", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = agreementAccepted,
                    onCheckedChange = {
                        agreementAccepted = it
                        if (it) {
                            showAgreementError = false
                            onPolicyAccepted()
                        }
                    },
                )
                Text(
                    text = "我已阅读并同意",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                TextButton(
                    onClick = { showPolicy = true },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = "用户协议与隐私政策",
                        maxLines = 1,
                    )
                }
            }
            if (showAgreementError) {
                Text(
                    text = "请先同意用户协议与隐私政策",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showAbout) {
        // 登录页的"关于"直接复用设置页的完整关于界面
        AboutScreen(
            onNavigateBack = { showAbout = false },
            onOpenUrl = { url ->
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }.isSuccess
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showPolicy) {
        AlertDialog(
            onDismissRequest = { showPolicy = false },
            title = { Text("用户协议与隐私政策") },
            text = {
                Text(
                    "登录即表示你同意必要的账号验证与文件访问规则。" +
                        "使用纯数字 QQ 邮箱登录时，QQ 号将发送给腾讯 QQ 服务，" +
                        "仅用于加载头像和昵称；获取失败时显示默认头像和邮箱前缀。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        agreementAccepted = true
                        onPolicyAccepted()
                        showAgreementError = false
                        showPolicy = false
                    },
                ) {
                    Text("同意")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPolicy = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailVerificationScreen(
    onBack: () -> Unit,
    onVerified: (email: String, role: Role) -> Unit,
    modifier: Modifier = Modifier,
    onRequestCode: ((email: String) -> Unit)? = null,
    onLogin: ((email: String, code: String) -> Unit)? = null,
    busy: Boolean = false,
    message: String? = null,
    codeSentEmail: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var localCodeSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val effectiveCodeSent = localCodeSent || codeSentEmail?.equals(email.trim(), ignoreCase = true) == true

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("邮箱登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("邮箱验证", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "支持 qq.com 和 mczihan.link 邮箱",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = {
                    if (it.trim() != email.trim()) {
                        localCodeSent = false
                        code = ""
                    }
                    email = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = errorMessage != null && roleForAllowedEmail(email) == null,
            )
            OutlinedButton(
                onClick = {
                    if (roleForAllowedEmail(email) == null) {
                        errorMessage = "请输入允许登录的邮箱"
                    } else if (onRequestCode != null) {
                        onRequestCode(email.trim())
                    } else {
                        localCodeSent = true
                        errorMessage = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (effectiveCodeSent) "重新获取验证码" else "获取验证码")
            }
            if (effectiveCodeSent) {
                Text(
                    text = "验证码已发送，请在 5 分钟内输入",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.filter(Char::isDigit).take(6)
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("6 位验证码") },
                singleLine = true,
                enabled = effectiveCodeSent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            (errorMessage ?: message)?.let { text ->
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    val role = roleForAllowedEmail(email)
                    when {
                        role == null -> errorMessage = "邮箱域名不受支持"
                        !effectiveCodeSent -> errorMessage = "请先获取验证码"
                        code.length != 6 -> errorMessage = "请输入 6 位验证码"
                        onLogin != null -> onLogin(email.trim(), code)
                        else -> onVerified(email.trim(), role)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (busy) "登录中…" else "登录")
            }
        }
    }
}

private fun openGitHubRepo() {
    runCatching {
        java.awt.Desktop.getDesktop().browse(
            java.net.URI("https://github.com/zhuzhuzihan/AndroidResourceDownload"),
        )
    }
}
