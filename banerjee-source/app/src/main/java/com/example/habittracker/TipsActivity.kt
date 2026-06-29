package com.example.habittracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

class TipsActivity : AppCompatActivity() {
    companion object {
        private const val allowedUrl = "https://jamesclear.com/habit-guide"
        private const val allowedHost = "jamesclear.com"
    }

    private lateinit var tipsWebView: WebView
    private lateinit var loadingText: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tips)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tipsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar: MaterialToolbar = findViewById(R.id.tipsToolbar)
        loadingText = findViewById(R.id.loadingText)
        errorText = findViewById(R.id.errorText)
        tipsWebView = findViewById(R.id.tipsWebView)

        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationContentDescription(R.string.tips_back)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tipsWebView.canGoBack()) {
                    tipsWebView.goBack()
                } else {
                    finish()
                }
            }
        })

        configureWebView()
        tipsWebView.loadUrl(allowedUrl)
    }

    override fun onDestroy() {
        tipsWebView.destroy()
        super.onDestroy()
    }

    private fun configureWebView() {
        with(tipsWebView.settings) {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        tipsWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url ?: return true
                return if (isAllowedInternalUrl(targetUrl)) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, targetUrl))
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingText.visibility = View.VISIBLE
                errorText.visibility = View.GONE
                tipsWebView.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingText.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadingText.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    tipsWebView.visibility = View.GONE
                }
            }
        }
    }

    private fun isAllowedInternalUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase().orEmpty()
        val httpsScheme = uri.scheme.equals("https", ignoreCase = true)
        return httpsScheme && (host == allowedHost || host.endsWith(".$allowedHost"))
    }
}
