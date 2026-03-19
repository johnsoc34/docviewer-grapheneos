package org.grapheneos.docviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var welcomeView: View
    private lateinit var documentWebView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorText: TextView

    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var searchCount: TextView
    private lateinit var searchUp: ImageButton
    private lateinit var searchDown: ImageButton
    private lateinit var searchClose: ImageButton

    private var documentOpen = false
    private var searchVisible = false
    private var currentTitle = ""

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { openDocument(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        welcomeView = findViewById(R.id.welcomeView)
        documentWebView = findViewById(R.id.documentWebView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        errorView = findViewById(R.id.errorView)
        errorText = findViewById(R.id.errorText)

        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
        searchCount = findViewById(R.id.searchCount)
        searchUp = findViewById(R.id.searchUp)
        searchDown = findViewById(R.id.searchDown)
        searchClose = findViewById(R.id.searchClose)

        setSupportActionBar(toolbar)

        documentWebView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            databaseEnabled = false
            domStorageEnabled = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }

        findViewById<View>(R.id.openFileButton).setOnClickListener { pickFile() }
        findViewById<View>(R.id.retryButton).setOnClickListener { pickFile() }

        setupSearch()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchVisible) {
                    hideSearch()
                } else if (documentOpen) {
                    closeDocument()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { openDocument(it) }
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isNotEmpty()) {
                    documentWebView.findAllAsync(query)
                } else {
                    documentWebView.clearMatches()
                    searchCount.text = ""
                }
            }
        })

        documentWebView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                if (numberOfMatches > 0) {
                    val current = activeMatchOrdinal + 1
                    searchCount.text = current.toString() + "/" + numberOfMatches.toString()
                } else {
                    val query = searchInput.text?.toString() ?: ""
                    if (query.isNotEmpty()) {
                        searchCount.text = "0/0"
                    } else {
                        searchCount.text = ""
                    }
                }
            }
        }

        searchUp.setOnClickListener { documentWebView.findNext(false) }
        searchDown.setOnClickListener { documentWebView.findNext(true) }
        searchClose.setOnClickListener { hideSearch() }

        searchInput.setOnEditorActionListener { _, _, _ ->
            documentWebView.findNext(true)
            true
        }
    }

    private fun showSearch() {
        if (!documentOpen) return
        searchVisible = true
        toolbar.title = ""
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        invalidateOptionsMenu()
    }

    private fun hideSearch() {
        searchVisible = false
        searchBar.visibility = View.GONE
        searchInput.text?.clear()
        documentWebView.clearMatches()
        searchCount.text = ""
        toolbar.title = currentTitle
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        invalidateOptionsMenu()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { openDocument(it) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_close)?.isVisible = documentOpen
        menu.findItem(R.id.action_search)?.isVisible = documentOpen && !searchVisible
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open -> { pickFile(); true }
            R.id.action_close -> { closeDocument(); true }
            R.id.action_search -> { showSearch(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun closeDocument() {
        documentOpen = false
        hideSearch()
        documentWebView.loadUrl("about:blank")
        documentWebView.visibility = View.GONE
        errorView.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        welcomeView.visibility = View.VISIBLE
        currentTitle = getString(R.string.app_name)
        toolbar.title = currentTitle
        invalidateOptionsMenu()
    }

    private fun pickFile() {
        openFileLauncher.launch(arrayOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/pdf",
            "text/plain",
            "text/csv",
            "text/tab-separated-values",
            "*/*"
        ))
    }

    private fun openDocument(uri: Uri) {
        showLoading()
        hideSearch()

        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        scope.launch {
            try {
                val mimeType = contentResolver.getType(uri) ?: ""
                val fileName = getFileName(uri)

                currentTitle = fileName.ifEmpty { "Document" }
                toolbar.title = currentTitle

                val html = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open file")
                    inputStream.use { stream ->
                        convertToHtml(stream, mimeType, fileName, cacheDir)
                    }
                }

                showDocument(html)
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun convertToHtml(
        inputStream: InputStream,
        mimeType: String,
        fileName: String,
        cacheDir: File
    ): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mimeType.contains("wordprocessingml") || ext == "docx" ->
                DocxConverter.convert(inputStream)
            mimeType.contains("spreadsheetml") || ext == "xlsx" ->
                XlsxConverter.convert(inputStream)
            mimeType.contains("presentationml") || ext == "pptx" ->
                PptxConverter.convert(inputStream)
            mimeType.contains("msword") || ext == "doc" ->
                DocConverter.convert(inputStream)
            mimeType.contains("ms-excel") || ext == "xls" ->
                XlsConverter.convert(inputStream)
            mimeType.contains("ms-powerpoint") || ext == "ppt" ->
                PptConverter.convert(inputStream)
            mimeType.contains("opendocument") || ext in listOf("odt", "ods", "odp") ->
                OdfConverter.convert(inputStream, ext)
            mimeType.contains("pdf") || ext == "pdf" ->
                PdfConverter.convert(inputStream, cacheDir)
            mimeType.startsWith("text/") || ext in listOf("txt", "csv", "tsv", "log", "md", "json", "xml", "yaml", "yml") ->
                TextConverter.convert(inputStream, fileName)
            else -> throw Exception(getString(R.string.error_unsupported))
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: ""
            }
        }
        return name
    }

    private fun showLoading() {
        welcomeView.visibility = View.GONE
        documentWebView.visibility = View.GONE
        errorView.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE
    }

    private fun showDocument(html: String) {
        loadingIndicator.visibility = View.GONE
        errorView.visibility = View.GONE
        welcomeView.visibility = View.GONE
        documentWebView.visibility = View.VISIBLE
        documentWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        documentOpen = true
        invalidateOptionsMenu()
    }

    private fun showError(message: String) {
        loadingIndicator.visibility = View.GONE
        documentWebView.visibility = View.GONE
        welcomeView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorText.text = message
        documentOpen = false
        invalidateOptionsMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        documentWebView.destroy()
    }
}
