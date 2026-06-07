package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Bookmark
import com.example.data.CustomScript
import com.example.data.History
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.currentTabId.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchInput by viewModel.searchInput.collectAsStateWithLifecycle()

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val historyList by viewModel.history.collectAsStateWithLifecycle()
    val scriptsList by viewModel.scripts.collectAsStateWithLifecycle()

    val isTabsMgrOpen by viewModel.isTabsManagerOpen.collectAsStateWithLifecycle()
    val isHistoryOpen by viewModel.isHistoryOpen.collectAsStateWithLifecycle()
    val isBookmarksOpen by viewModel.isBookmarksOpen.collectAsStateWithLifecycle()
    val isScriptsOpen by viewModel.isScriptsManagerOpen.collectAsStateWithLifecycle()
    val isAddScriptOpen by viewModel.isAddScriptOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()

    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
    val forceHttpsEnabled by viewModel.forceHttpsEnabled.collectAsStateWithLifecycle()

    val isAiPanelOpen by viewModel.isAiPanelOpen.collectAsStateWithLifecycle()
    val isReaderModeOpen by viewModel.isReaderModeOpen.collectAsStateWithLifecycle()
    val isStudyCenterOpen by viewModel.isStudyCenterOpen.collectAsStateWithLifecycle()
    val isNotesManagerOpen by viewModel.isNotesManagerOpen.collectAsStateWithLifecycle()
    val studyNotes by viewModel.studyNotes.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val spokenText = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.searchInput.value = spokenText
                viewModel.loadUrlInActiveTab(spokenText)
            }
        }
    }

    // Detect back presses to navigate backwards or fall back
    BackHandler(enabled = currentTab != null && (currentTab?.url != "about:blank" && currentTab?.canGoBack == true)) {
        currentTab?.let { tab ->
            viewModel.webViewCache[tab.id]?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        }
    }

    // Colors mapping based on normal vs Incognito mode
    val isTabIncognito = currentTab?.isIncognito == true
    val themePrimary = if (isTabIncognito) Color(0xFF9C27B0) else Color(0xFF00ADB5)
    val themeDarkBG = if (isTabIncognito) Color(0xFF0F0B1E) else Color(0xFF1E2022)
    val themeSurface = if (isTabIncognito) Color(0xFF1A152E) else Color(0xFF2D3238)
    val themeAccent = if (isTabIncognito) Color(0xFFFF4081) else Color(0xFF00F5FF)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTabIncognito) themeDarkBG else MaterialTheme.colorScheme.background),
        bottomBar = {
            BrowserBottomBar(
                viewModel = viewModel,
                currentTab = currentTab,
                isIncognito = isTabIncognito,
                themePrimary = themePrimary,
                tabsCount = tabs.size
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Address Location Area & Safety Indicator
                AddressBarArea(
                    searchInput = searchInput,
                    currentTab = currentTab,
                    adBlockEnabled = adBlockEnabled,
                    forceHttps = forceHttpsEnabled,
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    onUrlSubmit = { query ->
                        viewModel.loadUrlInActiveTab(query)
                        focusManager.clearFocus()
                    },
                    onSearchInputChanged = { viewModel.searchInput.value = it },
                    onRefreshClick = {
                        currentTab?.let { tab ->
                            viewModel.webViewCache[tab.id]?.reload()
                        }
                    },
                    onAdBlockToggle = { viewModel.adBlockEnabled.value = !adBlockEnabled },
                    onHTTPSToggle = { viewModel.forceHttpsEnabled.value = !forceHttpsEnabled },
                    onBookmarkToggle = { viewModel.toggleBookmarkOfActiveTab() },
                    isBookmarked = bookmarks.any { it.url == currentTab?.url },
                    onVoiceSearchClick = {
                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar")
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                        }
                        try {
                            voiceLauncher.launch(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "التعرف على الصوت غير مدعوم على جهازك.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // --- Unified Search Dropdown Suggestions ---
                if (searchInput.isNotEmpty() && currentTab?.url == "about:blank") {
                     UnifiedSearchPanel(
                         query = searchInput,
                         bookmarks = bookmarks,
                         notes = studyNotes,
                         themePrimary = themePrimary,
                         isIncognito = isTabIncognito,
                         onNavigate = { url ->
                             viewModel.loadUrlInActiveTab(url)
                             focusManager.clearFocus()
                         },
                         onViewNote = { note ->
                             viewModel.isNotesManagerOpen.value = true
                         }
                     )
                }

                // Page Loading Line Indicator
                currentTab?.let { tab ->
                    if (tab.isLoading && tab.progress < 100) {
                        LinearProgressIndicator(
                            progress = { tab.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = themeAccent,
                            trackColor = themePrimary.copy(alpha = 0.2f),
                        )
                    }
                }

                // Core Main Screen: Switch between Web content-viewport and beautifully illustrated HomePage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (currentTab != null) {
                        if (currentTab?.url == "about:blank") {
                            // Render Native Arabic StartPage of the browser
                            BrowserStartPage(
                                isIncognito = isTabIncognito,
                                themePrimary = themePrimary,
                                themeAccent = themeAccent,
                                adBlockEnabled = adBlockEnabled,
                                onUrlNavigate = { url ->
                                    viewModel.loadUrlInActiveTab(url)
                                },
                                onToggleIncognito = {
                                    // Switch current tab incognito mode
                                    val targetId = currentTab?.id ?: return@BrowserStartPage
                                    viewModel.updateTabState(targetId) { tab ->
                                        tab.copy(isIncognito = !tab.isIncognito)
                                    }
                                },
                                bookmarks = bookmarks,
                                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                                activeTabCount = tabs.size,
                                onOpenScripts = { viewModel.isScriptsManagerOpen.value = true },
                                onOpenBookmarks = { viewModel.isBookmarksOpen.value = true }
                            )
                        } else {
                            // Render Multi-instance cached WebViews
                            tabs.forEach { tab ->
                                val isActive = tab.id == activeTabId
                                if (isActive) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        WebViewContainer(
                                            tab = tab,
                                            viewModel = viewModel,
                                            scriptsList = scriptsList,
                                            isIncognito = tab.isIncognito
                                        )

                                        // Web safety scanner warning overlay
                                        val activeUrl = tab.url
                                        val isSuspicious = remember(activeUrl) { viewModel.isUrlSuspicious(activeUrl) }
                                        if (isSuspicious) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp)
                                                    .align(Alignment.TopCenter),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = Color(0xFFFFEBEE)
                                                ),
                                                border = BorderStroke(1.dp, Color.Red),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(onClick = { viewModel.loadUrlInActiveTab("about:blank") }) {
                                                        Icon(Icons.Filled.Home, contentDescription = "الهروب للصفحة الرئيسية", tint = Color.Red)
                                                    }

                                                    Column(
                                                        horizontalAlignment = Alignment.End,
                                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                                    ) {
                                                        Text(
                                                            "تحذير: رابط مشبوه أو ضار!",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red,
                                                            textAlign = TextAlign.Right
                                                        )
                                                        Text(
                                                            "تحذير فائق الحماية: تم رصد هذا النطاق كونه خطراً أو يحتوي على ملفات تتبع خبيثة ومحاولات تصيد.",
                                                            fontSize = 9.sp,
                                                            color = Color.DarkGray,
                                                            textAlign = TextAlign.Right,
                                                            lineHeight = 11.sp
                                                        )
                                                    }

                                                    Icon(
                                                        imageVector = Icons.Filled.Warning,
                                                        contentDescription = null,
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Secure Loading fallback
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = themePrimary)
                        }
                    }
                }
            }

            // --- BOTTOM DRAWERS AND OVERLAY SHEETS ---

            // 1. Multiple Windows Tab Manager Sheet (تعدد التبويبات)
            if (isTabsMgrOpen) {
                TabManagerSheet(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    isIncognito = isTabIncognito,
                    themePrimary = themePrimary,
                    onTabSelect = { id ->
                        viewModel.selectTab(id)
                        viewModel.isTabsManagerOpen.value = false
                    },
                    onTabClose = { id -> viewModel.removeTab(id) },
                    onNewTab = { incognito ->
                        viewModel.createNewTab(isIncognito = incognito)
                        viewModel.isTabsManagerOpen.value = false
                    },
                    onDismiss = { viewModel.isTabsManagerOpen.value = false }
                )
            }

            // 2. Bookmarks Sidebar drawer (العلامات المرجعية)
            BookmarksSidebar(
                isOpen = isBookmarksOpen,
                bookmarks = bookmarks,
                themePrimary = themePrimary,
                isIncognito = isTabIncognito,
                currentTab = currentTab,
                onNavigate = { url ->
                    viewModel.loadUrlInActiveTab(url)
                    viewModel.isBookmarksOpen.value = false
                },
                onDelete = { viewModel.deleteBookmark(it) },
                onAddBookmark = { name, url ->
                    viewModel.addBookmarkManual(name, url)
                },
                onDismiss = { viewModel.isBookmarksOpen.value = false }
            )

            // 3. History Logs Drawer (سجل التصفح)
            if (isHistoryOpen) {
                HistoryManagerDialog(
                    historyList = historyList,
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    onNavigate = { url ->
                        viewModel.loadUrlInActiveTab(url)
                        viewModel.isHistoryOpen.value = false
                    },
                    onDelete = { viewModel.deleteHistoryItem(it) },
                    onClearAll = { viewModel.clearAllHistory() },
                    onDismiss = { viewModel.isHistoryOpen.value = false }
                )
            }

            // 4. Chrome-like Extension Custom Scripts Management Panel (مدير إضافات كروم)
            if (isScriptsOpen) {
                ExtensionScriptsManagerDialog(
                    scripts = scriptsList,
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    onToggleEnabled = { viewModel.toggleExtensionScript(it) },
                    onDelete = { viewModel.deleteExtensionScript(it) },
                    onAddNewClick = { viewModel.isAddScriptOpen.value = true },
                    onDismiss = { viewModel.isScriptsManagerOpen.value = false }
                )
            }

            // 5. Add Custom Scripts / User-Extensions Form (صنع إضافة مخصصة)
            if (isAddScriptOpen) {
                AddCustomScriptDialog(
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    onSave = { name, desc, code ->
                        viewModel.addCustomExtensionScript(name, desc, code)
                        viewModel.isAddScriptOpen.value = false
                    },
                    onDismiss = { viewModel.isAddScriptOpen.value = false }
                )
            }

            // 6. Settings Drawer (إعدادات المتصفح)
            SettingsDrawer(
                isOpen = isSettingsOpen,
                viewModel = viewModel,
                themePrimary = themePrimary,
                isIncognito = isTabIncognito,
                currentTab = currentTab,
                bookmarks = bookmarks,
                historyList = historyList,
                onDismiss = { viewModel.isSettingsOpen.value = false }
            )

            // 7. AI Assistant Overlay (المساعد الذكي المدمج)
            if (isAiPanelOpen) {
                AiAssistantDialog(
                    viewModel = viewModel,
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    currentTabId = currentTab?.id ?: "",
                    onDismiss = { viewModel.isAiPanelOpen.value = false }
                )
            }

            // 8. Smart Reader Overlay (القارئ النظيف)
            if (isReaderModeOpen) {
                SmartReaderScreen(
                    viewModel = viewModel,
                    isIncognito = isTabIncognito,
                    themePrimary = themePrimary,
                    onDismiss = { viewModel.isReaderModeOpen.value = false }
                )
            }

            // 9. Study Center Overlay (مركز البحوث التلقائي)
            if (isStudyCenterOpen) {
                StudyCenterDialog(
                    viewModel = viewModel,
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    currentTabId = currentTab?.id ?: "",
                    onDismiss = { viewModel.isStudyCenterOpen.value = false }
                )
            }

            // 10. Study Notes Manager Dialog (خزانة الملاحظات المطورة)
            if (isNotesManagerOpen) {
                StudyNotesManagerDialog(
                    viewModel = viewModel,
                    themePrimary = themePrimary,
                    isIncognito = isTabIncognito,
                    onDismiss = { viewModel.isNotesManagerOpen.value = false }
                )
            }
        }
    }
}

// --- Address Location & Safety Area Composable ---

@Composable
fun AddressBarArea(
    searchInput: String,
    currentTab: TabState?,
    adBlockEnabled: Boolean,
    forceHttps: Boolean,
    themePrimary: Color,
    isIncognito: Boolean,
    onUrlSubmit: (String) -> Unit,
    onSearchInputChanged: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onAdBlockToggle: () -> Unit,
    onHTTPSToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isBookmarked: Boolean,
    onVoiceSearchClick: () -> Unit
) {
    var expandedSecurityMenu by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(if (isIncognito) Color(0xFF141124) else Color(0xFFF7F9FC))
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Safety Padlock Menu / Security Shield Controller
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isIncognito) Color(0xFF231F3A) else Color(0xFFE2E8F0)
                        )
                        .clickable { expandedSecurityMenu = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isIncognito) Icons.Outlined.Security else Icons.Filled.Lock,
                        contentDescription = "قفل الأمان",
                        tint = if (isIncognito) Color(0xFFE040FB) else Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isIncognito) "محمي" else "آمن",
                        color = if (isIncognito) Color(0xFFFF80AB) else Color(0xFF2E7D32),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Security Menu dropdown
                DropdownMenu(
                    expanded = expandedSecurityMenu,
                    onDismissRequest = { expandedSecurityMenu = false },
                    modifier = Modifier
                        .width(280.dp)
                        .background(if (isIncognito) Color(0xFF1F1B35) else Color(0xFFFFFFFF))
                        .border(
                            1.dp,
                            themePrimary.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "درع حمایة المصفح",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncognito) Color.White else Color.Black,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Divider(color = themePrimary.copy(alpha = 0.2f))

                        // Ad Block Toggle Switch in dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = adBlockEnabled,
                                onCheckedChange = { onAdBlockToggle() },
                                colors = SwitchDefaults.colors(checkedThumbColor = themePrimary)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "مانع الإعلانات والدعايات",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isIncognito) Color.LightGray else Color.DarkGray
                                )
                                Text(
                                    "يحظر التسلل والتتبع الخارجي",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // HTTPS Toggle Switch in dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = forceHttps,
                                onCheckedChange = { onHTTPSToggle() },
                                colors = SwitchDefaults.colors(checkedThumbColor = themePrimary)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "تصفح آمن مشفر (HTTPS)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isIncognito) Color.LightGray else Color.DarkGray
                                )
                                Text(
                                    "يحمي هويتك وسرية معاملاتك",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Sandbox privacy description
                        if (isIncognito) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE040FB).copy(alpha = 0.1f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "التصفح الخفي المسرع نشط حاليًا. لا يتم تتبع سجلاتك أو حفظ ملفات الارتباط الكوكيز.",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF80AB),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Normal Address Row Input Text Box
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = onSearchInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("address_input"),
                    placeholder = {
                        Text(
                            text = "اكتب عنوان موقع أو ابحث في جوجل...",
                            fontSize = 12.sp,
                            color = if (isIncognito) Color.LightGray.copy(alpha = 0.6f) else Color.Gray,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isIncognito) Color(0xFF2A2447) else Color(0xFFF1F5F9),
                        unfocusedContainerColor = if (isIncognito) Color(0xFF2A2447) else Color(0xFFF1F5F9),
                        focusedBorderColor = themePrimary,
                        unfocusedBorderColor = if (isIncognito) Color(0xFF453D68) else Color(0xFFCBD5E1),
                        focusedTextColor = if (isIncognito) Color.White else Color.Black,
                        unfocusedTextColor = if (isIncognito) Color.White else Color.Black
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { if (searchInput.isNotEmpty()) onUrlSubmit(searchInput) }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Left, // Keep url aligned English left
                        fontFamily = FontFamily.Monospace
                    ),
                    trailingIcon = {
                        IconButton(onClick = onVoiceSearchClick) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "بحث صوتي",
                                tint = if (isIncognito) Color(0xFF00E676) else themePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    leadingIcon = {
                        // Secure bookmark controller
                        IconButton(onClick = onBookmarkToggle) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "أضف للعلامات",
                                tint = if (isBookmarked) Color(0xFFFFD700) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                // Refresh Status Action Button
                if (currentTab != null && currentTab.url != "about:blank") {
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isIncognito) Color(0xFF231F3A) else Color(0xFFF1F5F9)
                            )
                    ) {
                        Icon(
                            imageVector = if (currentTab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = "تحديث",
                            tint = if (isIncognito) Color.White else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Active WebView Component Container ---

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    tab: TabState,
    viewModel: BrowserViewModel,
    scriptsList: List<CustomScript>,
    isIncognito: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val webView = remember(tab.id) {
        viewModel.webViewCache.getOrPut(tab.id) {
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Enhanced and extremely secure custom WebView Settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                // Custom headers and browser details
                val mobileUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                this.settings.userAgentString = if (tab.desktopMode) desktopUA else mobileUA

                // Incognito specifications: never remember form data or cookies
                if (isIncognito) {
                    this.settings.saveFormData = false
                    this.settings.savePassword = false
                }

                // Attach highly integrated client interfaces
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        viewModel.updateTabState(tab.id) {
                            it.copy(isLoading = true, progress = 0, url = url ?: "")
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val pageTitle = view?.title ?: url ?: "صفحة ويب"
                        viewModel.updateTabState(tab.id) {
                            it.copy(
                                isLoading = false,
                                progress = 100,
                                url = url ?: "",
                                title = pageTitle,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }

                        // Feed history log if not incognito
                        viewModel.recordVisit(pageTitle, url ?: "")

                        // Dynamic injection of active user chrome extensions/script scripts
                        scope.launch {
                            val activeScripts = scriptsList.filter { it.isEnabled }
                            activeScripts.forEach { script ->
                                view?.evaluateJavascript(script.scriptCode, null)
                            }
                        }
                    }

                    // Fast and Secure Ad Blocker integration at engine level
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: return null

                        // If general keyword filter hits and ad blocker is active, block it!
                        if (viewModel.adBlockEnabled.value) {
                            val adKeywords = listOf(
                                "googleads", "doubleclick", "pagead2", "googlesyndication",
                                "adservice", "adnxs", "adsrvr", "adsystem", "popads", "popunder",
                                "adcolony", "applovin", "unityads", "vungle", "admob", "taboola",
                                "outbrain", "smartadserver", "adsbygoogle", "adzerk", "analytics"
                            )
                            val isAd = adKeywords.any { requestUrl.contains(it, ignoreCase = true) }
                            if (isAd) {
                                // Block tracking request instantly sending empty bytes
                                return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream("".toByteArray())
                                )
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    // Handle secure errors
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        viewModel.updateTabState(tab.id) {
                            it.copy(progress = newProgress)
                        }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        viewModel.updateTabState(tab.id) {
                            it.copy(title = title ?: tab.url)
                        }
                    }
                }
            }
        }
    }

    // Trigger loads/reloads dynamically based on active selected tab state URL changes
    LaunchedEffect(tab.url) {
        if (tab.url != "about:blank" && tab.url != webView.url) {
            webView.loadUrl(tab.url)
        }
    }

    // Update Desktop vs Mobile Mode agent details instantly
    LaunchedEffect(tab.desktopMode) {
        val mobileUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        webView.settings.userAgentString = if (tab.desktopMode) desktopUA else mobileUA
        if (tab.url != "about:blank") {
            webView.reload()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}

// --- Browser Bottom Bar Navigation Controllers ---

@Composable
fun BrowserBottomBar(
    viewModel: BrowserViewModel,
    currentTab: TabState?,
    isIncognito: Boolean,
    themePrimary: Color,
    tabsCount: Int
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(if (isIncognito) Color(0xFF0F0B1E) else Color(0xFFFFFFFF))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Settings Menu Drawer trigger
            var expandedSettingsMenu by remember { mutableStateOf(false) }

            IconButton(onClick = { expandedSettingsMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "القائمة الرئيسية",
                    tint = if (isIncognito) Color.White else Color.DarkGray
                )
            }

            // Expanded floating menu sheet options
            DropdownMenu(
                expanded = expandedSettingsMenu,
                onDismissRequest = { expandedSettingsMenu = false },
                modifier = Modifier
                    .width(220.dp)
                    .background(if (isIncognito) Color(0xFF1A152E) else Color(0xFFFFFFFF))
                    .border(1.dp, themePrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                // Multi Arabic options
                DropdownMenuItem(
                    text = {
                        Text(
                            "العلامات المرجعية",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD700)) },
                    onClick = {
                        viewModel.isBookmarksOpen.value = true
                        expandedSettingsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "سجل التصفح الأمني",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, tint = themePrimary) },
                    onClick = {
                        viewModel.isHistoryOpen.value = true
                        expandedSettingsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "مدير إضافات كروم",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Extension, contentDescription = null, tint = Color(0xFFFF5722)) },
                    onClick = {
                        viewModel.isScriptsManagerOpen.value = true
                        expandedSettingsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "إعدادات المتصفح",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = themePrimary) },
                    onClick = {
                        viewModel.isSettingsOpen.value = true
                        expandedSettingsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "تحديث لموقع الكمبيوتر",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (currentTab?.desktopMode == true) Icons.Filled.LaptopMac else Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = if (currentTab?.desktopMode == true) themePrimary else Color.Gray
                        )
                    },
                    onClick = {
                        currentTab?.let { tab ->
                            viewModel.updateTabState(tab.id) {
                                it.copy(desktopMode = !tab.desktopMode)
                            }
                        }
                        expandedSettingsMenu = false
                    }
                )
                Divider(color = themePrimary.copy(alpha = 0.2f))
                DropdownMenuItem(
                    text = {
                        Text(
                            "المساعد الذكي المساعد AI",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF9C27B0)) },
                    onClick = {
                        viewModel.isAiPanelOpen.value = true
                        expandedSettingsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "قارئ المقالات الذكي",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Book, contentDescription = null, tint = Color(0xFF4CAF50)) },
                    onClick = {
                        currentTab?.let { tab ->
                            viewModel.launchReaderMode(tab.id)
                        }
                        expandedSettingsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "مركز البحث والدراسة",
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.School, contentDescription = null, tint = Color(0xFF2196F3)) },
                    onClick = {
                        viewModel.isStudyCenterOpen.value = true
                        expandedSettingsMenu = false
                    }
                )
                Divider(color = themePrimary.copy(alpha = 0.2f))
                DropdownMenuItem(
                    text = {
                        Text(
                            "مسح الذاكرة الفوري",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        viewModel.clearBrowsingMemory()
                        expandedSettingsMenu = false
                    }
                )
            }

            // Web navigation back controllers
            IconButton(
                enabled = currentTab != null && currentTab.canGoBack,
                onClick = {
                    currentTab?.let { tab ->
                        viewModel.webViewCache[tab.id]?.goBack()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = if (currentTab?.canGoBack == true) {
                        if (isIncognito) Color(0xFFFF80AB) else themePrimary
                    } else Color.LightGray
                )
            }

            // Core plus button (Add instant tab)
            IconButton(
                onClick = { viewModel.createNewTab(isIncognito = isIncognito) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(themePrimary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "علامة تبويب جديدة",
                    tint = themePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Web navigation forward controllers
            IconButton(
                enabled = currentTab != null && currentTab.canGoForward,
                onClick = {
                    currentTab?.let { tab ->
                        viewModel.webViewCache[tab.id]?.goForward()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "لأمام",
                    tint = if (currentTab?.canGoForward == true) {
                        if (isIncognito) Color(0xFFFF80AB) else themePrimary
                    } else Color.LightGray
                )
            }

            // Multiple tab managers selector UI representation with responsive badge counters
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        2.dp,
                        if (isIncognito) Color(0xFFFF80AB) else themePrimary,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.isTabsManagerOpen.value = true }
            ) {
                Text(
                    text = tabsCount.toString(),
                    color = if (isIncognito) Color(0xFFFF80AB) else themePrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// --- Browser Start Page (Beautiful Custom Illustrated Dashboard in Arabic) ---

@Composable
fun BrowserStartPage(
    isIncognito: Boolean,
    themePrimary: Color,
    themeAccent: Color,
    adBlockEnabled: Boolean,
    onUrlNavigate: (String) -> Unit,
    onToggleIncognito: () -> Unit,
    bookmarks: List<Bookmark>,
    onDeleteBookmark: (Bookmark) -> Unit,
    activeTabCount: Int,
    onOpenScripts: () -> Unit,
    onOpenBookmarks: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isIncognito) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0B1E), Color(0xFF1A1333))
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF8FAFC), Color(0xFFEFF6FF))
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Animated Visual Browser Emblem Glowing Header
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isIncognito) themePrimary.copy(alpha = 0.2f) else Color(0x1F00ADB5)
                    )
                    .border(
                        2.dp,
                        if (isIncognito) Color(0xFFFF80AB) else themePrimary,
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    imageVector = if (isIncognito) Icons.Outlined.PrivacyTip else Icons.Filled.Language,
                    contentDescription = "لوغو المتصفح",
                    tint = if (isIncognito) Color(0xFFFF4081) else themePrimary,
                    modifier = Modifier.size(50.dp)
                )
            }

            // Arabic Display Text Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isIncognito) "وضع التصفح الخفي الآمن" else "متصفح Orvix",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncognito) Color.White else Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isIncognito) "حماية خصوصية مطلقة، لا تتبع لا إعلانات" else "مرحباً بك في تصفح ذكي، فائق ومحمّي",
                    fontSize = 12.sp,
                    color = if (isIncognito) Color.LightGray.copy(alpha = 0.8f) else Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }

            // Multi Privacy / Regular fast toggler
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isIncognito) Color(0xFF251F3D) else Color(0xFFE2E8F0))
                    .clickable { onToggleIncognito() }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "التصفح السري",
                    color = if (isIncognito) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (isIncognito) Color(0xFF9C27B0) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Text(
                    text = "التصفح العادي",
                    color = if (!isIncognito) Color.DarkGray else Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (!isIncognito) Color.White else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Speed Dials List Links (الروابط السريعة)
            Text(
                "الروابط الأكثر زيارة لتبسيط تصفحك",
                color = if (isIncognito) Color.Gray else Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            val speedDials = listOf(
                DialItem("جوجل", "https://www.google.com", Icons.Filled.Search, Color(0xFFEA4335)),
                DialItem("يوتيوب", "https://www.youtube.com", Icons.Filled.PlayCircle, Color(0xFFFF0000)),
                DialItem("ويكيبيديا", "https://ar.wikipedia.org", Icons.Filled.ImportContacts, Color(0xFF607D8B)),
                DialItem("فيسبوك", "https://www.facebook.com", Icons.Filled.Facebook, Color(0xFF1877F2)),
                DialItem("الطقس", "https://www.weather.com", Icons.Filled.WbCloudy, Color(0xFF0288D1)),
                DialItem("الذكاء الاصطناعي", "https://gemini.google.com", Icons.Filled.AutoAwesome, Color(0xFF673AB7))
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                items(speedDials) { dial ->
                    Card(
                        modifier = Modifier
                            .clickable { onUrlNavigate(dial.url) }
                            .shadow(2.dp, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isIncognito) Color(0xFF211B3B) else Color.White
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = dial.icon,
                                contentDescription = dial.title,
                                tint = dial.color,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = dial.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncognito) Color.White else Color(0xFF334155),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Quick Control Dashboard Features List (لوحة التحكم السريعة في الأمان والإضافات)
            Text(
                "أدوات الأمان الحصرية والملحقات",
                color = if (isIncognito) Color.Gray else Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Extensions Addons trigger Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenScripts() }
                        .shadow(1.dp, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIncognito) Color(0xFF251F42) else Color(0xFFECFDF5)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text(
                                "إضافات كروم مخصصة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isIncognito) Color.White else Color(0xFF065F46),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                "صنع وحقن أكواد مخصصة",
                                fontSize = 9.sp,
                                color = if (isIncognito) Color.LightGray else Color(0xFF047857),
                                textAlign = TextAlign.Right
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            tint = if (isIncognito) Color(0xFFE040FB) else Color(0xFF10B981)
                        )
                    }
                }

                // Security Shield Info Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(1.dp, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIncognito) Color(0xFF251F42) else Color(0xFFEFF6FF)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text(
                                "حاجب الإعلانات آمن",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isIncognito) Color.White else Color(0xFF1E40AF),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                if (adBlockEnabled) "الدرع مفعّل وجاهز" else "متوقف مؤقتًا",
                                fontSize = 9.sp,
                                color = if (adBlockEnabled) Color(0xFF2E7D32) else Color.Red,
                                textAlign = TextAlign.Right
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (adBlockEnabled) Icons.Filled.VerifiedUser else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (adBlockEnabled) Color(0xFF3B82F6) else Color.Red
                        )
                    }
                }
            }

            // Quick Bookmarks Horizontal Flow representation (مفضلتك المرجعية)
            if (bookmarks.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenBookmarks) {
                        Text("عرض الكل", fontSize = 11.sp, color = themePrimary)
                    }
                    Text(
                        "مفضلتك السريعة",
                        color = if (isIncognito) Color.Gray else Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isIncognito) Color(0xFF15102F) else Color(0xFFF1F5F9),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bookmarks.take(3).forEach { bookmark ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isIncognito) Color(0xFF2C254B) else Color.White)
                                .clickable { onUrlNavigate(bookmark.url) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bookmark.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isIncognito) Color.White else Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

data class DialItem(val title: String, val url: String, val icon: ImageVector, val color: Color)

// --- Dialog: Tab Manager Overlay Panels (إدارة النوافذ والتبويبات) ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabManagerSheet(
    tabs: List<TabState>,
    activeTabId: String,
    isIncognito: Boolean,
    themePrimary: Color,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "النوافذ والتبويبات المفتوحة",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onNewTab(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نافذة خفية", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onNewTab(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ADB5)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نافذة عادية", fontSize = 11.sp)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items = tabs, key = { it.id }) { tab ->
                        val isCurrent = tab.id == activeTabId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) themePrimary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (tab.isIncognito) Color(0xFF271E42) else Color(0xFFF1F5F9)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTabSelect(tab.id) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Close button
                                IconButton(onClick = { onTabClose(tab.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "أغلق النافذة", tint = Color.Red)
                                }

                                // Name / Identity details
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (tab.isIncognito) {
                                            Icon(
                                                Icons.Outlined.PrivacyTip,
                                                contentDescription = null,
                                                tint = Color(0xFFFF4081),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = tab.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (tab.isIncognito) Color.White else Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Text(
                                        text = if (tab.url == "about:blank") "صفحة فارغة" else tab.url,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

// --- Dialog: Custom Bookmarks Sidebar Drawer (العلامات المرجعية) ---

@Composable
fun BookmarksSidebar(
    isOpen: Boolean,
    bookmarks: List<Bookmark>,
    themePrimary: Color,
    isIncognito: Boolean,
    currentTab: TabState?,
    onNavigate: (String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    // 1. Independent Backdrop Fading Cover
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )
    }

    // 2. Sliding Drawer Panel
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            var searchQuery by remember { mutableStateOf("") }
            var showManualAddForm by remember { mutableStateOf(false) }
            var manualTitle by remember { mutableStateOf("") }
            var manualUrl by remember { mutableStateOf("") }

            val filteredBookmarks = remember(bookmarks, searchQuery) {
                if (searchQuery.isBlank()) {
                    bookmarks
                } else {
                    bookmarks.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.url.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            val isCurrentBookmarked = remember(bookmarks, currentTab) {
                currentTab != null && currentTab.url != "about:blank" && bookmarks.any { it.url == currentTab.url }
            }

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.85f),
                color = if (isIncognito) Color(0xFF141124) else Color(0xFFFFFFFF),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // --- Header ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إغلاق الجانبية",
                                tint = if (isIncognito) Color.White else Color.DarkGray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Badge indicating bookmarks size
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(themePrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${bookmarks.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themePrimary
                                )
                            }
                            Text(
                                text = "العلامات المرجعية",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncognito) Color.White else Color(0xFF1E293B)
                            )
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- Real-time Search Filter Bar ---
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "ابحث عن علامات مرجعية...",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isIncognito) Color(0xFF231F3B) else Color(0xFFF1F5F9),
                            unfocusedContainerColor = if (isIncognito) Color(0xFF231F3B) else Color(0xFFF1F5F9),
                            focusedBorderColor = themePrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = if (isIncognito) Color.White else Color.Black,
                            unfocusedTextColor = if (isIncognito) Color.White else Color.Black
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = themePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        leadingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "مسح البحث",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- Quick Save Action Section ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isIncognito) Color(0xFF1F1B35) else Color(0xFFF8FAFC),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (isIncognito) Color(0xFF2C254B) else Color(0xFFE2E8F0),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentTab != null && currentTab.url != "about:blank") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrentBookmarked) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "محفوظة بالفعل",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            onAddBookmark(currentTab.title, currentTab.url)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = themePrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("حفظ الآن", fontSize = 11.sp, color = Color.White)
                                    }
                                }

                                Text(
                                    "الصفحة المفتوحة حالياً",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncognito) Color.LightGray else Color.Gray
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentTab.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isIncognito) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Right
                                )
                                Text(
                                    text = currentTab.url,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Left
                                )
                            }
                            Divider(color = if (isIncognito) Color(0xFF2C254B) else Color(0xFFE2E8F0))
                        }

                        // Toggle manual form visibility
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showManualAddForm = !showManualAddForm }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showManualAddForm) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = themePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "حفظ علامة مرجعية يدوياً",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = themePrimary
                            )
                        }

                        AnimatedVisibility(visible = showManualAddForm) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualTitle,
                                    onValueChange = { manualTitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            "الاسم (مثال: محرك بحث)",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = themePrimary,
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right)
                                )

                                OutlinedTextField(
                                    value = manualUrl,
                                    onValueChange = { manualUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            "العنوان (مثال: example.com)",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = themePrimary,
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Left)
                                )

                                Button(
                                    onClick = {
                                        if (manualTitle.isNotBlank() && manualUrl.isNotBlank()) {
                                            onAddBookmark(manualTitle.trim(), manualUrl.trim())
                                            manualTitle = ""
                                            manualUrl = ""
                                            showManualAddForm = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                                ) {
                                    Text("إضافة للمفضلة", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- Scrollable list of Bookmarks ---
                    Text(
                        text = "علاماتك المفضلة للمرور السريع",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (filteredBookmarks.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.StarRate,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "لم يتم العثور على نتائج للبحث." else "قائمة مفضلتك فارغة حالياً.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredBookmarks) { bookmark ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isIncognito) Color(0xFF231F3B) else Color(0xFFF1F5F9)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigate(bookmark.url) }
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Action to Delete on left
                                            IconButton(
                                                onClick = { onDelete(bookmark) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "حذف العلامة",
                                                    tint = Color.Red.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // 2. Info on right
                                            Column(
                                                horizontalAlignment = Alignment.End,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = bookmark.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isIncognito) Color.White else Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Right,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text(
                                                    text = bookmark.url,
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Left,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            // 3. Site icon placeholder on far-right
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (isIncognito) Color(0xFF2C254B) else Color.White
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Bookmark,
                                                    contentDescription = null,
                                                    tint = themePrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Dialog: Browser History Records Dialog ---

@Composable
fun HistoryManagerDialog(
    historyList: List<History>,
    themePrimary: Color,
    isIncognito: Boolean,
    onNavigate: (String) -> Unit,
    onDelete: (History) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearAll()
                        onDismiss()
                    }) {
                        Text("مسح الكل", color = Color.Red, fontSize = 12.sp)
                    }
                }
                Text(
                    "سجل التصفح الأمني",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .fillMaxWidth()
            ) {
                if (historyList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "لا توجد زيارات مسجلة لحفظ السرية.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(historyList) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isIncognito) Color(0xFF282245) else Color(0xFFF8FAFC)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigate(item.url) }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { onDelete(item) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف السجل", tint = Color.Red)
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isIncognito) Color.White else Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = item.url,
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

// --- Dialog: Extensions Manager UI (مدير الإضافات) ---

@Composable
fun ExtensionScriptsManagerDialog(
    scripts: List<CustomScript>,
    themePrimary: Color,
    isIncognito: Boolean,
    onToggleEnabled: (CustomScript) -> Unit,
    onDelete: (CustomScript) -> Unit,
    onAddNewClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAddNewClick) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "أضف إضافة جديدة", tint = themePrimary, modifier = Modifier.size(28.dp))
                }
                Text(
                    "مدير إضافات کروم والمساعد الذكي",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(scripts) { script ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isIncognito) Color(0xFF282245) else Color(0xFFF8FAFC)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Switches to activate user plugins
                                    Switch(
                                        checked = script.isEnabled,
                                        onCheckedChange = { onToggleEnabled(script) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = themePrimary)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (script.isBuiltIn) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(themePrimary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("نظام", fontSize = 9.sp, color = themePrimary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(
                                            text = script.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isIncognito) Color.White else Color.Black,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Text(
                                    text = script.description,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (!script.isBuiltIn) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        TextButton(
                                            onClick = { onDelete(script) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("حذف الإضافة", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("حفظ وإغلاق") }
        }
    )
}

// --- Dialog: Add Custom Script Code (حقن وصنع إضافة كروم المخصصة) ---

@Composable
fun AddCustomScriptDialog(
    themePrimary: Color,
    isIncognito: Boolean,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var scriptBody by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "برمجة وإضافة ملحق كروم جديد",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.65f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الإضافة (مثال: محوّل الألوان)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("الوصف البرمجي", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary)
                )

                OutlinedTextField(
                    value = scriptBody,
                    onValueChange = { scriptBody = it },
                    label = { Text("كود الحقن البرمجي (JavaScript)", fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            text = "(function() { console.log('مرحباً'); })();",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(themePrimary.copy(alpha = 0.1f))
                        .padding(8.dp)
                ) {
                    Text(
                        "سيتم حقن وتشغيل هذا الكود البرمجي تلقائيًا في كل صفحة ويب وموقع تقوم بزيارته لزيادة خصائص المتصفح.",
                        fontSize = 10.sp,
                        color = themePrimary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty() && scriptBody.isNotEmpty()) onSave(name, desc, scriptBody) },
                colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
            ) {
                Text("حفظ وبدء التشغيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// --- Dialog: Browser Settings drawer (إعدادات المتصفح) ---

@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    viewModel: BrowserViewModel,
    themePrimary: Color,
    isIncognito: Boolean,
    currentTab: TabState?,
    bookmarks: List<com.example.data.Bookmark>,
    historyList: List<com.example.data.History>,
    onDismiss: () -> Unit
) {
    // 1. Independent Backdrop Fading Cover
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )
    }

    // 2. Sliding Drawer Panel
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 350.dp)
                    .fillMaxWidth(0.85f),
                color = if (isIncognito) Color(0xFF141124) else Color(0xFFFFFFFF),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                var selectedTab by remember { mutableStateOf(0) }
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
                val forceHttpsEnabled by viewModel.forceHttpsEnabled.collectAsStateWithLifecycle()
                val slowInternetEnabled by viewModel.slowInternetEnabled.collectAsStateWithLifecycle()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp)
                ) {
                    // --- Drawer Header ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إغلاق الإعدادات",
                                tint = if (isIncognito) Color.White else Color.DarkGray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "إعدادات المتصفح",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncognito) Color.White else Color(0xFF1E293B)
                            )
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = themePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // --- Custom Tabs Row (Arabic) ---
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = if (isIncognito) Color(0xFF1C1835) else Color(0xFFF1F5F9),
                        contentColor = themePrimary,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = themePrimary
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("المظهر والأمن", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("المفضلة", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("السجل", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- Tab Content Box ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // --- Appearance & Security Section ---
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Theme Selection Mode (وضع المظهر المظلم / الليلي)
                                    Text(
                                        text = "مظهر المتصفح (الوضع الليلي)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncognito) Color.LightGray else Color(0xFF475569),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 3 Theme Options Cards
                                        ThemeOptionCard(
                                            title = "تلقائي",
                                            icon = Icons.Filled.Settings,
                                            isSelected = themeMode == ThemeMode.SYSTEM,
                                            isIncognito = isIncognito,
                                            themePrimary = themePrimary,
                                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ThemeOptionCard(
                                            title = "مضيء",
                                            icon = Icons.Filled.LightMode,
                                            isSelected = themeMode == ThemeMode.LIGHT,
                                            isIncognito = isIncognito,
                                            themePrimary = themePrimary,
                                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ThemeOptionCard(
                                            title = "ليلي مظلم",
                                            icon = Icons.Filled.DarkMode,
                                            isSelected = themeMode == ThemeMode.DARK,
                                            isIncognito = isIncognito,
                                            themePrimary = themePrimary,
                                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Divider(color = if (isIncognito) Color(0xFF2C254B) else Color(0xFFE2E8F0))

                                    // Privacy & Security Controls Toggles
                                    Text(
                                        text = "الحماية والأمان",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncognito) Color.LightGray else Color(0xFF475569),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // AdBlock toggle
                                    SettingsToggleRow(
                                        title = "حاجب الإعلانات الفائق",
                                        subtitle = "حجب النوافذ المنبثقة والإعلانات الفضولية تلقائياً وتسريع التصفح.",
                                        checked = adBlockEnabled,
                                        isIncognito = isIncognito,
                                        themePrimary = themePrimary,
                                        onCheckedChange = { viewModel.adBlockEnabled.value = it }
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Force HTTPS toggle
                                    SettingsToggleRow(
                                        title = "فرض تشفير HTTPS آمن",
                                        subtitle = "تحويل تلقائي لمواقع التصفح غير المشفرة لحماية بياناتك.",
                                        checked = forceHttpsEnabled,
                                        isIncognito = isIncognito,
                                        themePrimary = themePrimary,
                                        onCheckedChange = { viewModel.forceHttpsEnabled.value = it }
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Slow Internet Mode toggle (وضع الإنترنت البطيء للعراق)
                                    SettingsToggleRow(
                                        title = "وضع الإنترنت البطيء (العراق)",
                                        subtitle = "تحسين ذكي بضغط الصور وإيقاف تحميلها تلقائياً لتسريع فتح الصفحات في مناطق ضعف الاتصال وتوفير استهلاك الباقة.",
                                        checked = slowInternetEnabled,
                                        isIncognito = isIncognito,
                                        themePrimary = themePrimary,
                                        onCheckedChange = { viewModel.setSlowInternetEnabled(it) }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Divider(color = if (isIncognito) Color(0xFF2C254B) else Color(0xFFE2E8F0))

                                    // Cache clean container
                                    Button(
                                        onClick = { viewModel.clearBrowsingMemory() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isIncognito) Color(0xFF3B1E1E) else Color(0xFFFEE2E2),
                                            contentColor = Color.Red
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مسح ذاكرة التصفح والمؤقتة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            1 -> {
                                // --- Bookmarks Panel ---
                                var bookmarkSearch by remember { mutableStateOf("") }
                                val filteredBookmarks = remember(bookmarks, bookmarkSearch) {
                                    if (bookmarkSearch.isBlank()) bookmarks else bookmarks.filter {
                                        it.title.contains(bookmarkSearch, ignoreCase = true) ||
                                        it.url.contains(bookmarkSearch, ignoreCase = true)
                                    }
                                }

                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Search Bar
                                    OutlinedTextField(
                                        value = bookmarkSearch,
                                        onValueChange = { bookmarkSearch = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                "ابحث في المفضلة...",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = if (isIncognito) Color(0xFF1E193C) else Color(0xFFF8FAFC),
                                            unfocusedContainerColor = if (isIncognito) Color(0xFF1E193C) else Color(0xFFF8FAFC),
                                            focusedBorderColor = themePrimary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = if (isIncognito) Color.White else Color.Black,
                                            unfocusedTextColor = if (isIncognito) Color.White else Color.Black
                                        ),
                                        trailingIcon = {
                                            Icon(Icons.Filled.Search, contentDescription = null, tint = themePrimary, modifier = Modifier.size(16.dp))
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (filteredBookmarks.isEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("لا توجد صفحات محفوظة لعرضها.", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filteredBookmarks) { bookmark ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isIncognito) Color(0xFF221E3D) else Color(0xFFF1F5F9)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                viewModel.loadUrlInActiveTab(bookmark.url)
                                                                onDismiss()
                                                            }
                                                            .padding(8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(onClick = { viewModel.deleteBookmark(bookmark) }) {
                                                            Icon(Icons.Filled.Delete, contentDescription = "حذف العلامة", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                        }

                                                        Column(
                                                            horizontalAlignment = Alignment.End,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                text = bookmark.title,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                color = if (isIncognito) Color.White else Color.Black,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = bookmark.url,
                                                                fontSize = 9.sp,
                                                                color = Color.Gray,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // --- History Panel ---
                                var historySearch by remember { mutableStateOf("") }
                                val filteredHistory = remember(historyList, historySearch) {
                                    if (historySearch.isBlank()) historyList else historyList.filter {
                                        it.title.contains(historySearch, ignoreCase = true) ||
                                        it.url.contains(historySearch, ignoreCase = true)
                                    }
                                }

                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (historyList.isNotEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.clearAllHistory()
                                                }
                                            ) {
                                                Text("مسح الكل", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Text(
                                            text = "المواقع المزارة مؤخراً",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // History Search Input
                                    OutlinedTextField(
                                        value = historySearch,
                                        onValueChange = { historySearch = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                "ابحث في سجل التصفح...",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = if (isIncognito) Color(0xFF1E193C) else Color(0xFFF8FAFC),
                                            unfocusedContainerColor = if (isIncognito) Color(0xFF1E193C) else Color(0xFFF8FAFC),
                                            focusedBorderColor = themePrimary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = if (isIncognito) Color.White else Color.Black,
                                            unfocusedTextColor = if (isIncognito) Color.White else Color.Black
                                        ),
                                        trailingIcon = {
                                            Icon(Icons.Filled.Search, contentDescription = null, tint = themePrimary, modifier = Modifier.size(16.dp))
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (filteredHistory.isEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Filled.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("سجل التاريخ فارغ حالياً.", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filteredHistory) { hist ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isIncognito) Color(0xFF221E3D) else Color(0xFFF1F5F9)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                viewModel.loadUrlInActiveTab(hist.url)
                                                                onDismiss()
                                                            }
                                                            .padding(8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(onClick = { viewModel.deleteHistoryItem(hist) }) {
                                                            Icon(Icons.Filled.Delete, contentDescription = "حذف سجل محلي", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                        }

                                                        Column(
                                                            horizontalAlignment = Alignment.End,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                text = hist.title.ifBlank { "زيارة ويب" },
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                color = if (isIncognito) Color.White else Color.Black,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = hist.url,
                                                                fontSize = 9.sp,
                                                                color = Color.Gray,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    isIncognito: Boolean,
    themePrimary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) themePrimary else if (isIncognito) Color(0xFF2D2650) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themePrimary.copy(alpha = 0.15f) else if (isIncognito) Color(0xFF1B1733) else Color(0xFFF8FAFC)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) themePrimary else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) themePrimary else if (isIncognito) Color.White else Color.DarkGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isIncognito: Boolean,
    themePrimary: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isIncognito) Color(0xFF1E193C) else Color(0xFFF8FAFC),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (isIncognito) Color(0xFF2C254B) else Color(0xFFE2E8F0),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = themePrimary,
                checkedTrackColor = themePrimary.copy(alpha = 0.3f)
            )
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f).padding(end = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncognito) Color.White else Color.Black,
                textAlign = TextAlign.Right
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = Color.Gray,
                textAlign = TextAlign.Right,
                lineHeight = 11.sp
            )
        }
    }
}

// =========================================================================
// ==================== PREMIUM FEATURES COMPOSE VIEWS =====================
// =========================================================================

// --- 1. AI Intelligent Assistant Dialog ---
@Composable
fun AiAssistantDialog(
    viewModel: BrowserViewModel,
    themePrimary: Color,
    isIncognito: Boolean,
    currentTabId: String,
    onDismiss: () -> Unit
) {
    val aiResponseText by viewModel.aiResponseText.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    val aiChatQuestion by viewModel.aiChatQuestion.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    androidx.compose.ui.window.Dialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            color = if (isIncognito) Color(0xFF141124) else Color(0xFFFFFFFF),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "إغلاق",
                            tint = if (isIncognito) Color.White else Color.DarkGray
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "المساعد الذكي المدمج",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncognito) Color.White else Color(0xFF1E293B)
                        )
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions Grid
                Text(
                    text = "اختر إجراءً ذكياً لتحليل الصفحة المفتوحة:",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Summarize
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.runAiAction("SUMMARIZE", currentTabId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isIncognito) Color(0xFF2C1C3A) else Color(0xFFF3E5F5)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.ListAlt, contentDescription = null, tint = Color(0xFF8E24AA))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("تلخيص سريع", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color(0xFF4A148C))
                        }
                    }

                    // Action 2: Explain
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.runAiAction("EXPLAIN", currentTabId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isIncognito) Color(0xFF1A237E).copy(alpha = 0.3f) else Color(0xFFE8EAF6)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = Color(0xFF3F51B5))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("تبسيط وشرح", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color(0xFF1A237E))
                        }
                    }

                    // Action 3: Translate Dialects
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.runAiAction("TRANSLATE_DIALECT", currentTabId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isIncognito) Color(0xFF004D40).copy(alpha = 0.3f) else Color(0xFFE0F2F1)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null, tint = Color(0xFF00796B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ترجمة الفصحى", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color(0xFF004D40))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto Create Study Notes Row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.autoCreateStudyNote(currentTabId) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIncognito) Color(0xFF0D47A1).copy(alpha = 0.3f) else Color(0xFFE3F2FD)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = Color(0xFF1976D2))
                        Text(
                            "دراسة وتحليل: تلخيص وحفظ تلقائي في الملاحظات الطلابية",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncognito) Color.White else Color(0xFF0D47A1)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Response Area
                Text(
                    text = "نتائج التحليل بذكاء علمي:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncognito) Color.LightGray else Color(0xFF475569),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            if (isIncognito) Color(0xFF1F1A3A) else Color(0xFFF8FAFC),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (isIncognito) Color(0xFF2E265A) else Color(0xFFE2E8F0),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    if (isAiLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF9C27B0))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("جاري تشغيل وتفكير محرك الذكاء الاصطناعي لتلبية طلبك...", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else if (aiError != null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(aiError ?: "", fontSize = 11.sp, color = Color.Red, textAlign = TextAlign.Center)
                        }
                    } else if (aiResponseText.isBlank()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("المساعد جاهز ومستعد لمراجعة الصفحة وتلخيصها فوراً بضغطة واحدة من الأزرار أعلاه.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Text Area
                            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                Text(
                                    text = aiResponseText,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = if (isIncognito) Color.White else Color(0xFF1E293B),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Share / Copy row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Share Button
                                Button(
                                    onClick = {
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_TEXT, aiResponseText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "تصدير النتيجة"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاركة وتصدير", fontSize = 11.sp)
                                }

                                // Copy Button
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("AI Content", aiResponseText)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "تم النسخ للحافظة بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نسخ النص", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chat Input box Q&A
                OutlinedTextField(
                    value = aiChatQuestion,
                    onValueChange = { viewModel.aiChatQuestion.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            "اسأل المساعد بخصوص المقال المفتوح...",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isIncognito) Color(0xFF211D42) else Color(0xFFF1F5F9),
                        unfocusedContainerColor = if (isIncognito) Color(0xFF211D42) else Color(0xFFF1F5F9),
                        focusedBorderColor = Color(0xFF9C27B0),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = if (isIncognito) Color.White else Color.Black,
                        unfocusedTextColor = if (isIncognito) Color.White else Color.Black
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (aiChatQuestion.isNotBlank()) {
                                    viewModel.runAiAction("Q_A", currentTabId, aiChatQuestion)
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "إرسال",
                                tint = Color(0xFF9C27B0)
                            )
                        }
                    }
                )
            }
        }
    }
}

// --- 2. Smart Kindle-style Clean Reader Mode with voice Arabic synthesis ---
@Composable
fun SmartReaderScreen(
    viewModel: BrowserViewModel,
    isIncognito: Boolean,
    themePrimary: Color,
    onDismiss: () -> Unit
) {
    val readerTitle by viewModel.readerTitle.collectAsStateWithLifecycle()
    val readerText by viewModel.readerText.collectAsStateWithLifecycle()
    val fontSizeMultiplier by viewModel.readerFontSizeMultiplier.collectAsStateWithLifecycle()
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Initialize Speech voice engine
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.setLanguage(java.util.Locale("ar"))
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    val containerColor = when (readerTheme) {
        "Sepia" -> Color(0xFFFDFBF7)
        "Dark" -> Color(0xFF141414)
        else -> Color(0xFFFFFFFF)
    }

    val textColor = when (readerTheme) {
        "Dark" -> Color(0xFFE0E0E0)
        "Sepia" -> Color(0xFF433422)
        else -> Color(0xFF222222)
    }

    val dividerColor = when (readerTheme) {
        "Dark" -> Color(0xFF2E2E2E)
        "Sepia" -> Color(0xFFEADECA)
        else -> Color(0xFFE2E8F0)
    }

    androidx.compose.ui.window.Dialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {
            tts?.stop()
            onDismiss()
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = containerColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            tts?.stop()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "رجوع", tint = textColor)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "القارئ النظيف المريح",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Icon(Icons.Filled.Book, contentDescription = null, tint = themePrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toolbar settings: theme switcher, text zoom, TTS voice reading aloud
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (readerTheme == "Dark") Color(0xFF202020) else Color(0xFFF1F5F9)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio Arabic speak
                            Button(
                                onClick = {
                                    if (isSpeaking) {
                                        tts?.stop()
                                        isSpeaking = false
                                    } else {
                                        if (readerText.isNotBlank()) {
                                            val ttsPayload = readerText.replace("*", "").replace("#", "")
                                            tts?.speak(ttsPayload, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
                                            isSpeaking = true
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSpeaking) Color.Red else Color(0xFF00E676)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isSpeaking) "إيقاف القراءة" else "قراءة بصوت وبث",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }

                            // Theme Selector Option row
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Light", "Sepia", "Dark").forEach { candidate ->
                                    val chipBg = when (candidate) {
                                        "Sepia" -> Color(0xFFF4ECD8)
                                        "Dark" -> Color(0xFF2A2A2A)
                                        else -> Color(0xFFFFFFFF)
                                    }
                                    val chipBorder = if (readerTheme == candidate) themePrimary else Color.Transparent
                                    Box(
                                        modifier = Modifier
                                            .size(width = 56.dp, height = 30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(chipBg)
                                            .border(1.5.dp, chipBorder, RoundedCornerShape(6.dp))
                                            .clickable { viewModel.readerTheme.value = candidate },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (candidate) {
                                                "Sepia" -> "شاحب"
                                                "Dark" -> "مظلم"
                                                else -> "مضيء"
                                            },
                                            fontSize = 10.sp,
                                            color = if (candidate == "Dark") Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Zoom sizing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    onClick = { viewModel.readerFontSizeMultiplier.value = (fontSizeMultiplier + 0.15f).coerceAtMost(2.0f) }
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "تكبير", tint = textColor)
                                }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("حجم الخط %${(fontSizeMultiplier * 100).toInt()}", fontSize = 11.sp, color = textColor)
                                }
                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    onClick = { viewModel.readerFontSizeMultiplier.value = (fontSizeMultiplier - 0.15f).coerceAtLeast(0.7f) }
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "تصغير", tint = textColor)
                                }
                            }

                            Text("تخصيص العرض:", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scroll view clean reader article content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = readerTitle,
                        fontSize = (20 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        textAlign = TextAlign.Right,
                        lineHeight = (26 * fontSizeMultiplier).sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    Divider(color = dividerColor, thickness = 1.dp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = readerText,
                        fontSize = (15 * fontSizeMultiplier).sp,
                        lineHeight = (24 * fontSizeMultiplier).sp,
                        color = textColor.copy(alpha = 0.9f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// --- 3. Academic Study Center Main Dialog ---
@Composable
fun StudyCenterDialog(
    viewModel: BrowserViewModel,
    themePrimary: Color,
    isIncognito: Boolean,
    currentTabId: String,
    onDismiss: () -> Unit
) {
    var isManualMode by remember { mutableStateOf(false) }
    var manualTitle by remember { mutableStateOf("") }
    var manualContent by remember { mutableStateOf("") }
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isIncognito) Color(0xFF141124) else Color(0xFFFFFFFF),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = if (isIncognito) Color.White else Color.DarkGray)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("مركز الدراسة والبحوث", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color.Black)
                        Icon(Icons.Filled.School, contentDescription = null, tint = Color(0xFF2196F3))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isManualMode) {
                    Text(
                        "مرحباً بك في مركز الدراسة! يمكنك استبدال القراءة المجهدة بتلخيصات علمية دقيقة وحفظها لمراجعتها لاحقاً وتصدير الملاحظات لملفات الطلاب.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Button(
                        onClick = {
                            viewModel.autoCreateStudyNote(currentTabId)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("صناعة وحفظ ملخص دراسي ذكي فوراً", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { isManualMode = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isIncognito) Color(0xFF231F3A) else Color(0xFFF1F5F9), contentColor = if (isIncognito) Color.White else Color.DarkGray),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("كتابة ملاحظة ومراجع يدوياً", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.isNotesManagerOpen.value = true
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isIncognito) Color(0xFF1E2F1E) else Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فتح خزانة الملاحظات المحفوظة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Manual note form drawer
                    Text("إضافة مرجع أو ملاحظة دراسية:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("عنوان الملاحظة الدراسية...", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isIncognito) Color(0xFF1E1E35) else Color(0xFFF1F5F9),
                            unfocusedContainerColor = if (isIncognito) Color(0xFF1E1E35) else Color(0xFFF1F5F9),
                            focusedBorderColor = themePrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualContent,
                        onValueChange = { manualContent = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("اكتب مضمون ملاحظتك الدراسية هنا...", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isIncognito) Color(0xFF1E1E35) else Color(0xFFF1F5F9),
                            unfocusedContainerColor = if (isIncognito) Color(0xFF1E1E35) else Color(0xFFF1F5F9),
                            focusedBorderColor = themePrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (manualTitle.isNotBlank() && manualContent.isNotBlank()) {
                                    val currentTab = viewModel.currentTab.value
                                    viewModel.addManualStudyNote(manualTitle, manualContent, currentTab?.url ?: "")
                                    android.widget.Toast.makeText(context, "تم حفظ الملاحة يدوياً بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("حفظ المرجع", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { isManualMode = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text("إلغاء", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- 4. Study Notes List Drawer and detail explorer ---
@Composable
fun StudyNotesManagerDialog(
    viewModel: BrowserViewModel,
    themePrimary: Color,
    isIncognito: Boolean,
    onDismiss: () -> Unit
) {
    val notes by viewModel.studyNotes.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    var activeNoteToView by remember { mutableStateOf<com.example.data.StudyNote?>(null) }

    androidx.compose.ui.window.Dialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            color = if (isIncognito) Color(0xFF141124) else Color(0xFFFFFFFF),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = if (isIncognito) Color.White else Color.DarkGray)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("خزانة الملاحظات الأكاديمية", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color.Black)
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFF2E7D32))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("ابحث في ملاحظاتك الأكاديمية والمراجع الموثقة...", fontSize = 11.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isIncognito) Color(0xFF1A1A32) else Color(0xFFF1F5F9),
                        unfocusedContainerColor = if (isIncognito) Color(0xFF1A1A32) else Color(0xFFF1F5F9),
                        focusedBorderColor = themePrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredNotes.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("لا توجد مذكرات أو ملاحظات مطابقة لعرضها.", fontSize = 11.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(filteredNotes) { note ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeNoteToView = note },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isIncognito) Color(0xFF221E3F) else Color(0xFFF1F5F9)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                    Text(note.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color.Black)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        if (note.content.length > 120) note.content.take(120) + "..." else note.content,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "عرض التفاصيل", tint = themePrimary, modifier = Modifier.size(16.dp))
                                        Text(
                                            java.text.DateFormat.getDateInstance().format(java.util.Date(note.timestamp)),
                                            fontSize = 9.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Expanded notes viewer dialog
    activeNoteToView?.let { note ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { activeNoteToView = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = if (isIncognito) Color(0xFF141124) else Color(0xFFFFFFFF),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { activeNoteToView = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = if (isIncognito) Color.White else Color.DarkGray)
                        }

                        Text("تفاصيل الملخص العلمي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isIncognito) Color.White else Color.Black)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        note.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = themePrimary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "التاريخ: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.timestamp)),
                        fontSize = 9.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            note.content,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = if (isIncognito) Color.White else Color.Black,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.deleteStudyNote(note)
                                activeNoteToView = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val exportContent = viewModel.getStudyNoteExportBody(note)
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, exportContent)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "تخصيص وكتابة وتصدير ملاحظات المناهج"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصدير ومشاركة", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- 5. Unified Search Dropdown Suggestion List ---
@Composable
fun UnifiedSearchPanel(
    query: String,
    bookmarks: List<com.example.data.Bookmark>,
    notes: List<com.example.data.StudyNote>,
    themePrimary: Color,
    isIncognito: Boolean,
    onNavigate: (String) -> Unit,
    onViewNote: (com.example.data.StudyNote) -> Unit
) {
    val filteredBookmarks = remember(bookmarks, query) {
        bookmarks.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
    }

    val filteredNotes = remember(notes, query) {
        notes.filter { it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(max = 280.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isIncognito) Color(0xFF1E1935) else Color(0xFFFFFFFF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                "البحث الموحد السريع (Iraqi Universal Search):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = themePrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                textAlign = TextAlign.Right
            )

            Divider(color = themePrimary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

            SearchSuggestionRow(
                title = "البحث في مكرر جوجل عن '$query'",
                icon = Icons.Filled.Search,
                color = Color(0xFF4285F4),
                isIncognito = isIncognito,
                onClick = {
                    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                    onNavigate("https://www.google.com/search?q=$encoded")
                }
            )

            SearchSuggestionRow(
                title = "البحث عن مقاطع مرئية وتاريخية في YouTube",
                icon = Icons.Filled.PlayCircle,
                color = Color(0xFFFF0000),
                isIncognito = isIncognito,
                onClick = {
                    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                    onNavigate("https://www.youtube.com/results?search_query=$encoded")
                }
            )

            SearchSuggestionRow(
                title = "البحث في الموسوعة الحرة Wikipedia العربية",
                icon = Icons.Filled.MenuBook,
                color = Color(0xFF757575),
                isIncognito = isIncognito,
                onClick = {
                    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                    onNavigate("https://ar.wikipedia.org/wiki/Special:Search?search=$encoded")
                }
            )

            if (filteredBookmarks.isNotEmpty()) {
                Divider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "العلامات المرجعية المطابقة (${filteredBookmarks.size}):",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    textAlign = TextAlign.Right
                )
                filteredBookmarks.take(3).forEach { bookmark ->
                    SearchSuggestionRow(
                        title = bookmark.title,
                        icon = Icons.Filled.Star,
                        color = Color(0xFFFFD700),
                        isIncognito = isIncognito,
                        onClick = { onNavigate(bookmark.url) }
                    )
                }
            }

            if (filteredNotes.isNotEmpty()) {
                Divider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "الملخصات الدراسية الموازية المطابقة (${filteredNotes.size}):",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    textAlign = TextAlign.Right
                )
                filteredNotes.take(3).forEach { note ->
                    SearchSuggestionRow(
                        title = note.title,
                        icon = Icons.Filled.School,
                        color = Color(0xFF4CAF50),
                        isIncognito = isIncognito,
                        onClick = { onViewNote(note) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSuggestionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isIncognito: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                fontSize = 12.sp,
                color = if (isIncognito) Color.White else Color.Black,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
