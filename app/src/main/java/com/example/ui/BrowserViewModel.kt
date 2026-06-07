package com.example.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrowserDatabase
import com.example.data.BrowserRepository
import com.example.data.Bookmark
import com.example.data.History
import com.example.data.CustomScript
import com.example.data.StudyNote
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class TabState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "صفحة رئيسية جديدة",
    val url: String = "about:blank",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val isIncognito: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BrowserRepository
    private val prefs = application.getSharedPreferences("browser_prefs", Application.MODE_PRIVATE)

    // Theme Mode
    val themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    // Database Flows
    val bookmarks: StateFlow<List<Bookmark>>
    val history: StateFlow<List<History>>
    val scripts: StateFlow<List<CustomScript>>
    val studyNotes: StateFlow<List<StudyNote>>

    // Browser State
    private val _tabs = MutableStateFlow<List<TabState>>(emptyList())
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String>("")
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    // Active Tab Helper Flow
    val currentTab: StateFlow<TabState?> = combine(_tabs, _currentTabId) { tabsList, activeId ->
        tabsList.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Global App UI States
    val searchInput = MutableStateFlow("")

    // Settings Toggle States
    val adBlockEnabled = MutableStateFlow(true)
    val forceHttpsEnabled = MutableStateFlow(true)

    // Slow Internet Mode state (وضع الإنترنت البطيء - تصفية وحجب الصور لتسريع التحميل وتوفير البيانات)
    val slowInternetEnabled = MutableStateFlow(prefs.getBoolean("slow_internet_mode", false))

    fun setSlowInternetEnabled(enabled: Boolean) {
        slowInternetEnabled.value = enabled
        prefs.edit().putBoolean("slow_internet_mode", enabled).apply()
        
        // Command all WebView settings to change loadsImagesAutomatically
        webViewCache.values.forEach { view ->
            Handler(Looper.getMainLooper()).post {
                view.settings.loadsImagesAutomatically = !enabled
            }
        }
    }

    // AI Smart Assistant states (المساعد الذكي المدمج)
    val isAiPanelOpen = MutableStateFlow(false)
    val aiResponseText = MutableStateFlow("")
    val isAiLoading = MutableStateFlow(false)
    val aiError = MutableStateFlow<String?>(null)
    val aiChatQuestion = MutableStateFlow("")

    // Smart Reader Mode states (قارئ المحتوى الذكي بمساعدة الذكاء الاصطناعي مع القراءة الصوتية)
    val isReaderModeOpen = MutableStateFlow(false)
    val readerTitle = MutableStateFlow("")
    val readerText = MutableStateFlow("")
    val readerFontSizeMultiplier = MutableStateFlow(1.0f)
    val readerTheme = MutableStateFlow("Sepia") // "Light", "Sepia", "Dark"

    // Study & Research Center states (مركز الدراسة والبحث للبحث وحفظ وتصدير ملخصات الطلاب)
    val isStudyCenterOpen = MutableStateFlow(false)
    val isNotesManagerOpen = MutableStateFlow(false)

    // Unified Search State (البحث الموحد)
    val isUnifiedSearchActive = MutableStateFlow(false)

    // Sheet / Dialog states
    val isTabsManagerOpen = MutableStateFlow(false)
    val isHistoryOpen = MutableStateFlow(false)
    val isBookmarksOpen = MutableStateFlow(false)
    val isScriptsManagerOpen = MutableStateFlow(false)
    val isAddScriptOpen = MutableStateFlow(false)
    val isSettingsOpen = MutableStateFlow(false)

    // Browser WebViews map (caches active page engines per tab so state carries over visually!)
    // We update this dynamically based on compose cycles
    val webViewCache = mutableMapOf<String, android.webkit.WebView>()

    init {
        val database = BrowserDatabase.getDatabase(application)
        repository = BrowserRepository(database.browserDao())

        bookmarks = repository.allBookmarks.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        history = repository.allHistory.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        scripts = repository.allScripts.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        studyNotes = repository.allStudyNotes.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

        // Seed default extension scripts on startup if none exist
        viewModelScope.launch {
            scripts.collectLatest { list ->
                if (list.isEmpty()) {
                    seedDefaultScripts()
                }
            }
        }

        // Initialize first home screen tab
        createNewTab(url = "about:blank", isIncognito = false)
    }

    private suspend fun seedDefaultScripts() {
        val defaultList = listOf(
            CustomScript(
                name = "حاجب الإعلانات الفائق (Super AdBlock)",
                description = "يفحص ويحجب طلبات الإعلانات وعناصر التتبع تلقائيًا لتسريع التصفح وحفظ البيانات.",
                scriptCode = """
                    // Ad blocker content cleanup
                    (function() {
                        var adsPatterns = [
                            '[id*="google_ads"]', '[class*="sponsored"]', '.ad-box', '.banner-ad', 
                            'iframe[src*="doubleclick"]', 'iframe[src*="ads"]', '.ad-banner', '.adsbygoogle'
                        ];
                        function hideAds() {
                            adsPatterns.forEach(function(selector) {
                                document.querySelectorAll(selector).forEach(function(el) {
                                    el.style.display = 'none';
                                });
                            });
                        }
                        hideAds();
                        setInterval(hideAds, 1000);
                    })();
                """.trimIndent(),
                isEnabled = true,
                isBuiltIn = true
            ),
            CustomScript(
                name = "قارئ الوضع المظلم (Dark Mode Reader)",
                description = "يحمي عينيك عبر تحويل خلفيات المواقع للون الداكن والنصوص للون الفضي المريح تلقائيًا.",
                scriptCode = """
                    (function() {
                        var style = document.createElement('style');
                        style.id = 'browser-dark-mode';
                        style.innerHTML = 'html, body, p, div, span, h1, h2, h3, h4, h5, h6 { background-color: #121212 !important; color: #E0E0E0 !important; border-color: #333333 !important; } a { color: #80cbc4 !important; }';
                        document.head.appendChild(style);
                    })();
                """.trimIndent(),
                isEnabled = false,
                isBuiltIn = true
            ),
            CustomScript(
                name = "المترجم الفوري للعربية (Arabic Translator)",
                description = "يضيف زرًا عائمًا لترجمة الكلمات والمقالات الأجنبية إلى اللغة العربية بلمسة واحدة.",
                scriptCode = """
                    (function() {
                        var div = document.createElement('div');
                        div.id = 'google_translate_element';
                        div.style.position = 'fixed';
                        div.style.bottom = '80px';
                        div.style.right = '20px';
                        div.style.zIndex = '99999';
                        div.style.boxShadow = '0px 2px 10px rgba(0,0,0,0.3)';
                        div.style.borderRadius = '50px';
                        document.body.appendChild(div);

                        var script = document.createElement('script');
                        script.src = '//translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
                        document.body.appendChild(script);

                        window.googleTranslateElementInit = function() {
                            new google.translate.TranslateElement({
                                pageLanguage: 'auto', 
                                layout: google.translate.TranslateElement.InlineLayout.SIMPLE
                            }, 'google_translate_element');
                        };
                    })();
                """.trimIndent(),
                isEnabled = false,
                isBuiltIn = true
            ),
            CustomScript(
                name = "مسرع تحميل الصفحات (Turbo Engine)",
                description = "يوقف تحميل الصور ومقاطع الفيديو التلقائية لتوفير طاقة المعالج وسرعة التنزيل.",
                scriptCode = """
                    (function() {
                        // Defer image loading
                        document.querySelectorAll('img').forEach(function(img) {
                            if (!img.src) return;
                            img.setAttribute('loading', 'lazy');
                        });
                    })();
                """.trimIndent(),
                isEnabled = false,
                isBuiltIn = true
            )
        )

        for (script in defaultList) {
            repository.insertRawScript(script)
        }
    }

    // --- Tab Actions ---

    fun createNewTab(url: String = "about:blank", isIncognito: Boolean = false) {
        val newTab = TabState(
            id = UUID.randomUUID().toString(),
            url = url,
            isIncognito = isIncognito,
            title = if (url == "about:blank") {
                if (isIncognito) "تصفح خفي جديد" else "صفحة رئيسية جديدة"
            } else url
        )
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newTab.id
        searchInput.value = if (url == "about:blank") "" else url
    }

    fun removeTab(tabId: String) {
        val currentList = _tabs.value
        if (currentList.size <= 1) {
            // Keep at least one tab, reset it instead
            val defaultIncognito = currentList.find { it.id == tabId }?.isIncognito ?: false
            val resetTab = TabState(id = tabId, url = "about:blank", isIncognito = defaultIncognito)
            _tabs.value = listOf(resetTab)
            _currentTabId.value = tabId
            searchInput.value = ""
            // Clean webview cache
            webViewCache[tabId]?.let {
                it.clearHistory()
                it.loadUrl("about:blank")
            }
            return
        }

        // Remove from list
        _tabs.value = currentList.filter { it.id != tabId }
        // Remove from webview cache
        webViewCache.remove(tabId)

        // Select another tab if the closed one was active
        if (_currentTabId.value == tabId) {
            val lastTab = _tabs.value.lastOrNull()
            if (lastTab != null) {
                _currentTabId.value = lastTab.id
                searchInput.value = if (lastTab.url == "about:blank") "" else lastTab.url
            }
        }
    }

    fun selectTab(tabId: String) {
        _currentTabId.value = tabId
        val activeTab = _tabs.value.find { it.id == tabId }
        searchInput.value = if (activeTab?.url == "about:blank") "" else (activeTab?.url ?: "")
    }

    fun updateTabState(tabId: String, update: (TabState) -> TabState) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                val updated = update(tab)
                // Cache search input sync if it's the current active tab
                if (tabId == _currentTabId.value) {
                    searchInput.value = if (updated.url == "about:blank") "" else updated.url
                }
                updated
            } else tab
        }
    }

    fun loadUrlInActiveTab(query: String) {
        val activeId = _currentTabId.value
        val normalizedUrl = formatUrl(query)

        // Sync ViewModel input state
        searchInput.value = normalizedUrl

        updateTabState(activeId) { tab ->
            tab.copy(url = normalizedUrl, progress = 0)
        }

        // Command the WebView to load
        val cachedWebView = webViewCache[activeId]
        if (cachedWebView != null) {
            cachedWebView.loadUrl(normalizedUrl)
        }
    }

    private fun formatUrl(query: String): String {
        val trimmed = query.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://") || trimmed.startsWith("about:")) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            // Check force https
            return if (forceHttpsEnabled.value) "https://$trimmed" else "http://$trimmed"
        }
        // Fallback to Google Search or DuckDuckGo for safety
        val searchProvider = "https://www.google.com/search?q="
        return searchProvider + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }

    // --- Bookmarks Functions ---

    fun toggleBookmarkOfActiveTab() {
        val activeTab = currentTab.value ?: return
        if (activeTab.url == "about:blank" || activeTab.url.isEmpty()) return

        viewModelScope.launch {
            val isBookmarked = repository.isBookmarked(activeTab.url)
            if (isBookmarked) {
                repository.deleteBookmarkByUrl(activeTab.url)
            } else {
                repository.addBookmark(activeTab.title, activeTab.url)
            }
        }
    }

    fun addBookmarkManual(title: String, url: String) {
        viewModelScope.launch {
            repository.addBookmark(title, url)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    // --- History Functions ---

    fun recordVisit(title: String, url: String) {
        val activeTab = currentTab.value ?: return
        // Never log history in Incognito Mode!
        if (activeTab.isIncognito) return

        if (url == "about:blank" || url.isEmpty() || url.startsWith("file://")) return

        viewModelScope.launch {
            repository.addHistory(title, url)
        }
    }

    fun deleteHistoryItem(historyItem: History) {
        viewModelScope.launch {
            repository.deleteHistory(historyItem)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // --- Extension Custom Scripts ---

    fun addCustomExtensionScript(name: String, description: String, scriptCode: String) {
        viewModelScope.launch {
            repository.addScript(name, description, scriptCode, isEnabled = true)
        }
    }

    fun deleteExtensionScript(script: CustomScript) {
        viewModelScope.launch {
            repository.deleteScript(script)
        }
    }

    fun toggleExtensionScript(script: CustomScript) {
        viewModelScope.launch {
            repository.toggleScript(script.id, !script.isEnabled)
        }
    }

    // --- Privacy Security Tools ---

    fun clearBrowsingMemory() {
        viewModelScope.launch {
            // Clean cookie manager
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()

            // Clear Web databases
            WebStorage.getInstance().deleteAllData()

            // Clear current tabs cache
            webViewCache.values.forEach { view ->
                Handler(Looper.getMainLooper()).post {
                    view.clearCache(true)
                    view.clearFormData()
                    view.clearHistory()
                    view.clearMatches()
                }
            }
        }
    }

    // --- AI Assistant, Reader Mode & Study Notes Actions ---

    fun getWebPageContent(tabId: String, callback: (String) -> Unit) {
        val webView = webViewCache[tabId]
        if (webView != null) {
            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript("(function() { return document.body.innerText || document.textContent || ''; })()") { value ->
                    var cleanText = ""
                    if (value != null && value != "null") {
                        cleanText = try {
                            org.json.JSONTokener(value).nextValue().toString()
                        } catch (e: Exception) {
                            value.trim('"')
                        }
                    }
                    callback(cleanText.trim())
                }
            }
        } else {
            callback("")
        }
    }

    fun runAiAction(actionType: String, tabId: String, userQuestion: String = "") {
        isAiLoading.value = true
        aiError.value = null
        isAiPanelOpen.value = true

        getWebPageContent(tabId) { pageContent ->
            viewModelScope.launch {
                val contentToUse = if (pageContent.isBlank()) {
                    "الصفحة الحالية لا تحتوى نصوص قابلة للقراءة أو أن المتصفح في صفحة الترحيب."
                } else if (pageContent.length > 8000) {
                    pageContent.take(8000) + "\n... [تم اقتطاع بقية النص للمحافظة على موارد النظام]"
                } else {
                    pageContent
                }

                val prompt = when (actionType) {
                    "SUMMARIZE" -> {
                        "لخص المقال التالي باللغة العربية الفصحى بشكل نقاط رئيسية واضحة ومتبوعة بسطور تلخيص عامة:\n\n$contentToUse"
                    }
                    "EXPLAIN" -> {
                        "اشرح هذا المقال بالتفصيل والتبسيط المناسب للطلاب والباحثين، مع تفصيل المصطلحات المعقدة باللغة العربية بأسلوب راقٍ:\n\n$contentToUse"
                    }
                    "TRANSLATE_DIALECT" -> {
                        "ترجم النص التالي إلى لغة عربية فصحى مبسطة (إذا كان باللهجة العراقية مثل 'شلونك'، 'هواية'، 'شكو ماكو'، ترجمها بدقة واقتدار، وإذا كان بلغة أخرى ترجمه فوراً للعربية):\n\n$contentToUse"
                    }
                    "Q_A" -> {
                        "أجب عن سؤال المستخدم بذكاء وود باللغة العربية بناءً على محتوى صفحة الويب المرفقة أدناه:\nسؤال المستخدم: $userQuestion\n\nالمحتوى المرجعي للمقال:\n$contentToUse"
                    }
                    else -> "الرجاء المساعدة في قراءة وتحليل الصفحة المفتوحة."
                }

                val systemPrompt = "أنت مساعد ذكي ومستشار تصفح مدمج في متصفح سريع الذكي (Sarie Web & AI). تجيب باللغة العربية الفصحى الجميلة والمفهومة وتدعم ترجمة اللهجات العراقية والمطالعة العلمية."

                GeminiHelper.generateContent(prompt, systemPrompt)
                    .onSuccess { text ->
                        aiResponseText.value = text
                        isAiLoading.value = false
                    }
                    .onFailure { exception ->
                        aiError.value = exception.message ?: "حدث خطأ غير معروف."
                        isAiLoading.value = false
                    }
            }
        }
    }

    fun launchReaderMode(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        readerTitle.value = tab.title
        isReaderModeOpen.value = true
        readerText.value = "جاري تصفية المقال وتنظيفه لتجربة قراءة مثالية..."

        getWebPageContent(tabId) { pageText ->
            if (pageText.isBlank()) {
                readerText.value = "عذراً، لا يوجد نصوص مستخلصة كافية لعرضها في قارئ المحتوى المصفى."
                return@getWebPageContent
            }

            viewModelScope.launch {
                val cleanPrompt = """
                    قم باستخراج المقال الأساسي فقط من النص التالي وتجاهل كل الهياكل الترويجية والإعلانية والجانبية. 
                    رتبه بشكل رائع من فقرات وعناوين ممتعة باللغة العربية الفصحى:
                    
                    النص الخام المستخرج:
                    $pageText
                """.trimIndent()

                GeminiHelper.generateContent(cleanPrompt, "أنت قارئ ذكي ومصفٍ للمقالات والنصوص الإخبارية والعلمية.")
                    .onSuccess { cleaned ->
                        readerText.value = cleaned
                    }
                    .onFailure {
                        readerText.value = if (pageText.length > 2500) pageText.take(2500) + "..." else pageText
                    }
            }
        }
    }

    fun autoCreateStudyNote(tabId: String) {
        isAiLoading.value = true
        getWebPageContent(tabId) { pageText ->
            if (pageText.isBlank()) {
                isAiLoading.value = false
                return@getWebPageContent
            }
            viewModelScope.launch {
                val prompt = """
                    قم بصياغة ملخص دراسي عالي الفائدة والترتيب للطلاب من المقال التالي.
                    رتّب الملخص إلى:
                    1. عناوين عريضة وأفكار فلسفية/موضوعية واضحة.
                    2. أسئلة اختبار ذاتي وأجوبتها مقتبسة من المضمون.
                    3. بنود رئيسية مركزة للبحث السريع.
                    
                    المقال المتاح:
                    $pageText
                """.trimIndent()

                GeminiHelper.generateContent(prompt, "أنت مساعد علمي وبحثي ذكي للطلاب في الجامعات والمؤسسات التعليمية العربية.")
                    .onSuccess { noteContent ->
                        val currentTabObj = _tabs.value.find { it.id == tabId }
                        val noteTitle = "دراسة: " + (currentTabObj?.title ?: "صفحة ويب")
                        val noteUrl = currentTabObj?.url ?: ""
                        viewModelScope.launch {
                            repository.addStudyNote(noteTitle, noteContent, noteUrl)
                            isAiLoading.value = false
                            isNotesManagerOpen.value = true
                        }
                    }
                    .onFailure {
                        isAiLoading.value = false
                    }
            }
        }
    }

    // Study notes manipulation
    fun addManualStudyNote(title: String, content: String, url: String = "") {
        viewModelScope.launch {
            repository.addStudyNote(title, content, url)
        }
    }

    fun deleteStudyNote(note: StudyNote) {
        viewModelScope.launch {
            repository.deleteStudyNote(note)
        }
    }

    fun clearAllStudyNotes() {
        viewModelScope.launch {
            repository.clearAllStudyNotes()
        }
    }

    fun getStudyNoteExportBody(note: StudyNote): String {
        return """
            =========================================
            📝 ${note.title}
            🔗 المصدر: ${note.url}
            📅 التاريخ: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.timestamp))}
            =========================================
            
            ${note.content}
            
            -----------------------------------------
            تم التلخيص والحفظ بواسطة مساعد متصفح سريع الذكي
        """.trimIndent()
    }

    // Safe Link Scan / Phishing alert
    fun isUrlSuspicious(url: String): Boolean {
        if (url == "about:blank" || !url.startsWith("http")) return false
        val dangerousKeywords = listOf(
            "phishing", "scam", "classic-bank-update", "free-iraq-tokens", 
            "malware-download", "win-gift-card", "urgent-account-verify",
            "asiacell-free-gigas-fake", "zain-iraq-rewards-fake"
        )
        return dangerousKeywords.any { url.contains(it, ignoreCase = true) }
    }
}
