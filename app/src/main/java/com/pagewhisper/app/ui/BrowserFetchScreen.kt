package com.pagewhisper.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.text.Extractor
import com.pagewhisper.app.text.TextTools
import org.json.JSONObject
import org.json.JSONTokener

/**
 * For sites that block automatic fetching (bot protection, login). The page opens here, in a real browser view, so you can pass
 * the check yourself; then "Read this page" takes the chapter text out of it. The web view is only a tool for getting the text:
 * nothing of the app's own screens is web content.
 */
@Composable
fun BrowserFetchHost(vm: AppViewModel) {
    val url by vm.browser.collectAsStateWithLifecycle()
    val start = url ?: return
    BrowserFetch(
        start,
        onClose = { vm.browser.value = null },
        onError = { vm.toast(it) },
        onRead = { title, body, next, pageUrl ->
            vm.browser.value = null
            vm.loadChapter(Extractor.cleanTitle(title), body, pageUrl, next.ifEmpty { Extractor.guessNext(pageUrl) })
        },
    )
}

private const val READ_SCRIPT = """
(function(){
  function clean(t){return (t||'').replace(/\s+/g,' ').trim();}
  var sels=['#chapter-content','.chapter-content','[itemprop=articleBody]','.entry-content','.reading-content','#chr-content','article','main','#content','.content'];
  var best=null,i;
  for(i=0;i<sels.length;i++){var el=document.querySelector(sels[i]);if(el&&clean(el.innerText).length>=500){best=el;break;}}
  if(!best){var n=0;for(i=0;i<sels.length;i++){document.querySelectorAll(sels[i]).forEach(function(e){var l=clean(e.innerText).length;if(l>n){n=l;best=e;}});}}
  best=best||document.body;
  var ps=[].slice.call(best.querySelectorAll('p')).map(function(p){return clean(p.innerText);}).filter(Boolean);
  var body=ps.join('\n\n').length>=250?ps.join('\n\n'):best.innerText;
  var next='';var a=document.querySelector('a[rel~=next]');if(a)next=a.href;
  if(!next){[].slice.call(document.querySelectorAll('a[href]')).some(function(x){var t=clean(x.textContent);if(t.length<=30&&/^(next( chapter)?|[→›»>]+)\s*[→›»>]*${'$'}/i.test(t)){next=x.href;return true;}return false;});}
  var h1=document.querySelector('h1');
  return JSON.stringify({title:clean((h1&&h1.textContent)||document.title),body:body,next:next,url:location.href});
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserFetch(startUrl: String, onClose: () -> Unit, onError: (String) -> Unit, onRead: (String, String, String, String) -> Unit) {
    val c = LocalPw.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf(startUrl) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoftButton("Close", onClose)
                SoftButton("Back", { web?.let { if (it.canGoBack()) it.goBack() } })
                PrimaryButton("Read this page", {
                    web?.evaluateJavascript(READ_SCRIPT) { raw ->
                        try {
                            val json = JSONObject(JSONTokener(raw).nextValue() as String)
                            val body = TextTools.clean(json.optString("body"))
                            if (body.length < 250) onError("Could not find the chapter text on this page. Scroll to the chapter and try again.")
                            else onRead(json.optString("title"), body, json.optString("next"), json.optString("url"))
                        } catch (e: Exception) { onError("Could not read this page: ${e.message}") }
                    }
                })
            }
            OutlinedTextField(
                address, { address = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = { SoftButton("Go", { web?.loadUrl(if (address.startsWith("http")) address else "https://$address") }) },
            )
            Text("Pass any check on the page, open the chapter, then tap Read this page.", Modifier.padding(8.dp), color = c.muted)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) { if (url != null) address = url }
                        }
                        loadUrl(startUrl)
                        web = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.destroy() },
            )
        }
    }
}
