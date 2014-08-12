/*
 * Copyright 2013-2014 eBay Software Foundation and selendroid committers.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.selendroid.androiddriver;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.net.URISyntaxException;

public class WebViewActivity extends Activity {

  private static final int SELENDROID_MFW_ACTIVITY_REQUEST_CODE = 1;
  private static final String MFW_PACKAGE_PREFIX = "com.felicanetworks.mfw.a";
  // package name of the Osaifu-Keitai Web Plugin
  private static final String MFW_PLUGIN_PACKAGE_NAME = MFW_PACKAGE_PREFIX + ".main";
  // package name used for specify "Next URL" in "intent:" URL format
  private static final String MFW_PACKAGE_NAME_PARAM = MFW_PACKAGE_PREFIX + ".param";
  // URI schme used by Osaifu-Keitai Web Plugin
  private static final String URI_SCHEME_MFW_PLUGIN = "mfwpluginboot:";

  private WebView webview = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_web_view);
    webview = (WebView) findViewById(R.id.webview);
    WebSettings settings = webview.getSettings();
    // viewport meta tag support
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(true);

    webview.setWebViewClient(new AndroidDriverClient());
    webview.loadData("<html><body><h1 id='AndroidDriver'>Android driver webview app</h1>" +
        "</body></html>", "text/html", "UTF-8");
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == SELENDROID_MFW_ACTIVITY_REQUEST_CODE) {
      if (resultCode == RESULT_OK && data != null && data.getData() != null) {
        webview.loadUrl(data.getDataString());
      }
    } else {
      Log.v("SELENDROID", "Unhandled Activity Result: requestCode=" + requestCode + ", resultCode=" + requestCode + ", intent=" + data);
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private class AndroidDriverClient extends WebViewClient {
    @Override
    public void onReceivedSslError (WebView view, SslErrorHandler handler, SslError error) {
      handler.proceed();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      if (url.startsWith("http://") || url.startsWith("https://")) {
        return false;
      }
      if (url.startsWith(URI_SCHEME_MFW_PLUGIN)) {
        // Create Intent to pass to startActivityForMFWPlugin() method
        Intent intent = new Intent();
        // Extract hex-string encoded "next url" from <code>url</code>.
        // <code>url</code> format is "mfwpluginboot:'HEX-STRING ENCODED NEXT URL'"
        String nextUrl = url.substring(URI_SCHEME_MFW_PLUGIN.length());
        intent.putExtra(MFW_PACKAGE_NAME_PARAM, nextUrl);
        if (startActivityForMFWPlugin(view, intent))
          return true;
      }
      else {
        // Create intent from <code>url</code> and do something...
        Intent intent = null;
        try {
          intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
          Log.w("SELENDROID", "BAD URI " + url + ": " + e.getMessage());
          return false;
        }
        // Osaifu-Keitai Web Plugin
        if (intent.getPackage() != null && intent.getPackage().startsWith(MFW_PACKAGE_PREFIX)) {

          Log.d("SELENDROID", "Intent for Osaifu-Keitai Web Plugin created from url: " + url);

          if (startActivityForMFWPlugin(view, intent))
            return true;
        }
      }
      Log.v("SELENDROID", "Unhandled URL: " + url);
      return super.shouldOverrideUrlLoading(view, url);
    }

    private boolean startActivityForMFWPlugin(WebView view, Intent intent) {
      // URL to visit after processing Osaifu-Keitai Web Plugin passed as hex-string format

      if (!intent.hasExtra(MFW_PACKAGE_NAME_PARAM)) {
        Log.w("SELENDROID", "Missing next URL for Osaifu-Keitai Web Plugin");
        return false;
      }
      String hexedNextUrl = intent.getStringExtra(MFW_PACKAGE_NAME_PARAM);

      // Make intent "explicit" to receive activity result of Osaifu-Keitai Web Plugin directly.
      // It seems that the activity class must be a "com.felicanetworks.mfw.a.main.WebPluginActivity"
      // to receive Web plugin result directly.
      intent.setClassName(MFW_PLUGIN_PACKAGE_NAME, MFW_PLUGIN_PACKAGE_NAME + ".WebPluginActivity");

      // Decode Next URL and set it to intent as data
      try {
        byte[] b = (byte[]) new Hex().decode(hexedNextUrl);
        Uri nextUrl = Uri.parse(new String(b));
        if (nextUrl != null) {
          intent.setData(nextUrl);
        } else {
          Log.w("SELENDROID", "BAD Next URI: " + new String(b));
        }
      } catch (DecoderException e) {
        Log.w("SELENDROID", "Failed to decode Next URL from hex-string: " + hexedNextUrl + ": " + e.getMessage());
        return false;
      }
      startActivityForResult(intent, SELENDROID_MFW_ACTIVITY_REQUEST_CODE);
      return true;
    }
  }
}
