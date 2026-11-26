package com.jarvis.os.sandbox

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jarvis.os.data.SandboxControlNode
import org.json.JSONArray

class SandboxWebEngine(
    private val onUrlChanged: (String) -> Unit = {},
    private val onControlsScanned: (List<SandboxControlNode>) -> Unit = {},
    private val onTeachElementCaptured: (String) -> Unit = {}
) {

    private var webView: WebView? = null
    private var xrayEnabled: Boolean = false
    private var teachEnabled: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView, initialUrl: String) {
        webView = view
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        view.addJavascriptInterface(NaxJsBridge(), "NaxBridge")

        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                super.onPageFinished(v, url)
                if (!url.isNullOrBlank()) {
                    onUrlChanged(url)
                }
                scanControlBlueprint(showBadges = xrayEnabled)
                if (teachEnabled) {
                    injectTeachListener(true)
                }
            }
        }

        if (view.url.isNullOrBlank()) {
            view.loadUrl(initialUrl)
        }
    }

    fun navigateTo(url: String) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        webView?.post { webView?.loadUrl(normalized) }
    }

    fun goBack() {
        webView?.post {
            if (webView?.canGoBack() == true) webView?.goBack()
        }
    }

    fun reload() {
        webView?.post { webView?.reload() }
    }

    fun setXrayOverlay(enabled: Boolean) {
        xrayEnabled = enabled
        scanControlBlueprint(showBadges = enabled)
    }

    fun setTeachMode(enabled: Boolean) {
        teachEnabled = enabled
        injectTeachListener(enabled)
    }

    fun scanControlBlueprint(showBadges: Boolean = xrayEnabled) {
        val js = """
            (function() {
                document.querySelectorAll('.nax-xray-badge').forEach(el => el.remove());
                const candidates = Array.from(document.querySelectorAll('a, button, input, textarea, [role="button"], [contenteditable="true"]'));
                const results = [];
                let idx = 0;
                for (const el of candidates) {
                    const rect = el.getBoundingClientRect();
                    if (rect.width < 14 || rect.height < 14) continue;
                    if (rect.bottom < 0 || rect.top > window.innerHeight * 1.4) continue;
                    el.setAttribute('data-nax-id', String(idx));
                    const label = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('name') || el.tagName).trim().replace(/\s+/g, ' ').slice(0, 48);
                    results.push({
                        index: idx,
                        tag: el.tagName.toLowerCase(),
                        text: label || el.tagName.toLowerCase(),
                        selector: '[data-nax-id="' + idx + '"]'
                    });
                    if (${if (showBadges) "true" else "false"}) {
                        const badge = document.createElement('div');
                        badge.className = 'nax-xray-badge';
                        badge.innerText = '[' + idx + ']';
                        badge.style.position = 'fixed';
                        badge.style.left = Math.max(2, rect.left) + 'px';
                        badge.style.top = Math.max(2, rect.top) + 'px';
                        badge.style.background = 'rgba(124, 92, 255, 0.92)';
                        badge.style.color = '#ffffff';
                        badge.style.fontSize = '10px';
                        badge.style.fontWeight = '700';
                        badge.style.padding = '1px 4px';
                        badge.style.borderRadius = '4px';
                        badge.style.zIndex = '2147483647';
                        badge.style.pointerEvents = 'none';
                        document.body.appendChild(badge);
                    }
                    idx++;
                    if (idx >= 35) break;
                }
                if (window.NaxBridge && window.NaxBridge.onControls) {
                    window.NaxBridge.onControls(JSON.stringify(results));
                }
            })();
        """.trimIndent()

        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    fun clickControlIndex(index: Int, onResult: (Boolean) -> Unit = {}) {
        val js = """
            (function() {
                const el = document.querySelector('[data-nax-id="$index"]');
                if (!el) return "false";
                el.scrollIntoView({behavior: 'smooth', block: 'center'});
                el.click();
                return "true";
            })();
        """.trimIndent()
        webView?.post {
            webView?.evaluateJavascript(js) { res ->
                onResult(res?.contains("true") == true)
            }
        }
    }

    fun typeIntoControlIndex(index: Int, value: String, submitEnter: Boolean = true, onResult: (Boolean) -> Unit = {}) {
        val escaped = value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        val js = """
            (function() {
                const el = document.querySelector('[data-nax-id="$index"]');
                if (!el) return "false";
                el.focus();
                if ('value' in el) {
                    el.value = '$escaped';
                } else {
                    el.innerText = '$escaped';
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                if (${if (submitEnter) "true" else "false"}) {
                    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
                    if (el.form) el.form.submit();
                }
                return "true";
            })();
        """.trimIndent()
        webView?.post {
            webView?.evaluateJavascript(js) { res ->
                onResult(res?.contains("true") == true)
            }
        }
    }

    fun scrollDown() {
        webView?.post {
            webView?.evaluateJavascript("window.scrollBy({top: 420, behavior: 'smooth'});", null)
        }
    }

    fun clearSandboxSessionData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView?.post {
            webView?.clearCache(true)
            webView?.clearHistory()
        }
    }

    private fun injectTeachListener(enable: Boolean) {
        val js = if (enable) {
            """
            (function() {
                if (window.__naxTeachListener) return;
                window.__naxTeachListener = function(e) {
                    const t = e.target;
                    const label = (t.innerText || t.getAttribute('aria-label') || t.getAttribute('placeholder') || t.tagName || 'Element').trim().slice(0, 45);
                    if (window.NaxBridge && window.NaxBridge.onTeachTap) {
                        window.NaxBridge.onTeachTap(t.tagName.toLowerCase() + ': ' + label);
                    }
                };
                document.addEventListener('click', window.__naxTeachListener, true);
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                if (window.__naxTeachListener) {
                    document.removeEventListener('click', window.__naxTeachListener, true);
                    window.__naxTeachListener = null;
                }
            })();
            """.trimIndent()
        }
        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    private inner class NaxJsBridge {
        @JavascriptInterface
        fun onControls(jsonStr: String) {
            val list = mutableListOf<SandboxControlNode>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        SandboxControlNode(
                            index = o.optInt("index"),
                            tag = o.optString("tag"),
                            text = o.optString("text"),
                            selector = o.optString("selector")
                        )
                    )
                }
            } catch (_: Exception) {
            }
            onControlsScanned(list)
        }

        @JavascriptInterface
        fun onTeachTap(elementDesc: String) {
            if (elementDesc.isNotBlank()) {
                onTeachElementCaptured(elementDesc)
            }
        }
    }
}
