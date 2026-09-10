package com.affinetablet.client

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: ServerPrefs
    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = ServerPrefs(applicationContext)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        onBackPressedDispatcher.addCallback(this) {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setContent {
            MaterialTheme {
                AffineTabletApp(
                    prefs = prefs,
                    onWebViewCreated = { webViewRef = it },
                )
            }
        }
    }
}

private sealed interface UrlState {
    data object Loading : UrlState
    data object Unset : UrlState
    data class Set(val url: String) : UrlState
}

@Composable
private fun AffineTabletApp(prefs: ServerPrefs, onWebViewCreated: (WebView) -> Unit) {
    val urlState by remember {
        prefs.serverUrl.map { if (it.isNullOrBlank()) UrlState.Unset else UrlState.Set(it) }
    }.collectAsState(initial = UrlState.Loading)
    val scope = rememberCoroutineScope()

    when (val state = urlState) {
        is UrlState.Loading -> Box(Modifier.fillMaxSize()) {}
        is UrlState.Unset -> ServerSetupScreen(
            onSave = { url -> scope.launch { prefs.setServerUrl(url) } },
        )
        is UrlState.Set -> AffineScreen(
            prefs = prefs,
            serverUrl = state.url,
            onWebViewCreated = onWebViewCreated,
        )
    }
}

@Composable
private fun ServerSetupScreen(onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errorMessage = stringResource(id = R.string.setup_error_invalid_url)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(id = R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text(stringResource(id = R.string.setup_hint)) },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val normalized = normalizeServerUrl(text)
                    if (normalized == null) error = errorMessage else onSave(normalized)
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val normalized = normalizeServerUrl(text)
                    if (normalized == null) error = errorMessage else onSave(normalized)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = R.string.setup_connect))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AffineScreen(
    prefs: ServerPrefs,
    serverUrl: String,
    onWebViewCreated: (WebView) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val desktopSite by prefs.desktopSite.collectAsState(initial = true)
    var menuExpanded by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var changingServer by remember { mutableStateOf(false) }

    if (changingServer) {
        ServerSetupScreen(onSave = { url ->
            scope.launch {
                prefs.setServerUrl(url)
                changingServer = false
            }
        })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.menu_reload)) },
                            onClick = { menuExpanded = false; webViewInstance?.reload() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.menu_desktop_site)) },
                            trailingIcon = { Switch(checked = desktopSite, onCheckedChange = null) },
                            onClick = {
                                scope.launch { prefs.setDesktopSite(!desktopSite) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.menu_change_server)) },
                            onClick = { menuExpanded = false; changingServer = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.menu_clear_data)) },
                            onClick = {
                                menuExpanded = false
                                webViewInstance?.let { wv ->
                                    wv.clearHistory()
                                    android.webkit.WebStorage.getInstance().deleteAllData()
                                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                }
                                scope.launch { prefs.clear() }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        AffineWebView(
            serverUrl = serverUrl,
            desktopSite = desktopSite,
            modifier = Modifier.fillMaxSize().padding(padding),
            onCreated = {
                webViewInstance = it
                onWebViewCreated(it)
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AffineWebView(
    serverUrl: String,
    desktopSite: Boolean,
    modifier: Modifier = Modifier,
    onCreated: (WebView) -> Unit,
) {
    val context = LocalContext.current
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var lastAppliedDesktopSite by remember { mutableStateOf<Boolean?>(null) }

    fun applyUserAgent(webView: WebView, wantDesktop: Boolean) {
        val base = defaultUserAgent ?: return
        webView.settings.userAgentString = if (wantDesktop) {
            base.replace("Mobile Safari", "Safari").replace("; wv", "").plus(" DesktopTablet")
        } else {
            base
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            PalmRejectingWebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = object : WebViewClient() {}
                webChromeClient = WebChromeClient()

                defaultUserAgent = settings.userAgentString
                applyUserAgent(this, desktopSite)
                lastAppliedDesktopSite = desktopSite

                onCreated(this)
                loadUrl(serverUrl)
            }
        },
        update = { webView ->
            when {
                webView.url == null -> webView.loadUrl(serverUrl)
                lastAppliedDesktopSite != desktopSite -> {
                    applyUserAgent(webView, desktopSite)
                    lastAppliedDesktopSite = desktopSite
                    webView.reload()
                }
            }
        },
    )
}
