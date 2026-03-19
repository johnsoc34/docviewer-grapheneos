package org.grapheneos.docviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var welcomeView: View
    private lateinit var documentWebView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorText: TextView

    private var documentOpen = false

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

        // Back button closes document instead of exiting app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (documentOpen) {
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
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open -> { pickFile(); true }
            R.id.action_close -> { closeDocument(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun closeDocument() {
        documentOpen = false
        documentWebView.loadUrl("about:blank")
        documentWebView.visibility = View.GONE
        errorView.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        welcomeView.visibility = View.VISIBLE
        toolbar.title = getString(R.string.app_name)
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
            "*/*"
        ))
    }

    private fun openDocument(uri: Uri) {
        showLoading()

        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        scope.launch {
            try {
                val mimeType = contentResolver.getType(uri) ?: ""
                val fileName = getFileName(uri)

                toolbar.title = fileName.ifEmpty { "Document" }

                val html = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open file")
                    inputStream.use { stream ->
                        convertToHtml(stream, mimeType, fileName)
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
        fileName: String
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
