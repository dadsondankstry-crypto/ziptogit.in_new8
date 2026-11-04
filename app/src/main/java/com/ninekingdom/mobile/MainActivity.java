package com.ninekingdom.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import android.text.InputType;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Hospeda o HTML V8.0 offline e usa o seletor de documentos do Android para backup JSON. */
public final class MainActivity extends Activity {
    private static final int REQ_OPEN_BACKUP = 9001;
    private static final int REQ_SAVE_BACKUP = 9002;
    private static final String GAME_URL = "https://appassets.androidplatform.net/assets/game.html";
    private static final String ALLOWED_HOST = "appassets.androidplatform.net";

    private WebView gameView;
    private ValueCallback<Uri[]> uploadCallback;
    private String pendingBackupJson;
    private String pendingBackupFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        gameView = new WebView(this);
        gameView.setBackgroundColor(Color.rgb(17, 26, 40));
        gameView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // Obrigatório para saveGame()/localStorage.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Apenas URI selecionada pelo usuário via SAF.
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        gameView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        gameView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url != null && "https".equalsIgnoreCase(url.getScheme())
                    && ALLOWED_HOST.equalsIgnoreCase(url.getHost())) {
                    WebResourceResponse local = loader.shouldInterceptRequest(url);
                    if (local != null) return local;
                }
                // Nenhum pedido externo sai do aplicativo.
                return new WebResourceResponse("text/plain", "UTF-8", 403,
                    "Resource blocked", Collections.emptyMap(),
                    new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                return url == null || !"https".equalsIgnoreCase(url.getScheme())
                    || !ALLOWED_HOST.equalsIgnoreCase(url.getHost())
                    || !url.getPath().startsWith("/assets/");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Não foi possível abrir o jogo")
                        .setMessage("O conteúdo local não carregou. Reabra o aplicativo."
                            + " Se persistir, envie o log do GitHub Actions.")
                        .setPositiveButton("OK", null).show();
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Protege contra encerramento inesperado do processo WebView.
                view.destroy();
                gameView = null;
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Jogo interrompido")
                    .setMessage("O Android encerrou o motor WebView. Deseja reabrir o jogo?")
                    .setPositiveButton("Reabrir", (d, which) -> recreate())
                    .setNegativeButton("Sair", (d, which) -> finish())
                    .setCancelable(false).show();
                return true;
            }
        });

        gameView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "application/json", "text/plain", "application/octet-stream"
                });
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(i, REQ_OPEN_BACKUP);
                } catch (Exception ex) {
                    uploadCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Nenhum seletor de arquivos disponível.",
                        Toast.LENGTH_LONG).show();
                }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Nine Kingdom")
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Nine Kingdom")
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setNegativeButton("Cancelar", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                EditText textBox = new EditText(MainActivity.this);
                textBox.setInputType(InputType.TYPE_CLASS_TEXT);
                textBox.setSingleLine(true);
                if (defaultValue != null) textBox.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Nine Kingdom")
                    .setMessage(message)
                    .setView(textBox)
                    .setPositiveButton("OK", (d, w) -> result.confirm(textBox.getText().toString()))
                    .setNegativeButton("Cancelar", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });

        // A ponte só está disponível no HTML empacotado e validado no app.
        gameView.addJavascriptInterface(new BackupBridge(), "NineKingdomAndroid");
        setContentView(gameView);
        gameView.loadUrl(GAME_URL);
    }

    public final class BackupBridge {
        @JavascriptInterface
        public void saveBackup(String json, String requestedName) {
            if (json == null || json.length() == 0 || json.length() > 4_000_000) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Backup vazio ou muito grande.", Toast.LENGTH_LONG).show());
                return;
            }
            final String safeName = sanitizeFilename(requestedName);
            runOnUiThread(() -> {
                if (pendingBackupJson != null) {
                    Toast.makeText(MainActivity.this,
                        "Conclua o salvamento anterior primeiro.", Toast.LENGTH_LONG).show();
                    return;
                }
                pendingBackupJson = json;
                pendingBackupFilename = safeName;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, safeName);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQ_SAVE_BACKUP);
                } catch (Exception ex) {
                    pendingBackupJson = null;
                    pendingBackupFilename = null;
                    Toast.makeText(MainActivity.this,
                        "Nenhum aplicativo disponível para salvar arquivos.",
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private static String sanitizeFilename(String name) {
        String safe = name == null ? "nine-kingdom-backup.json"
            : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 90) safe = safe.substring(0, 90);
        if (safe.length() < 6) safe = "nine-kingdom-backup.json";
        if (!safe.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) safe += ".json";
        return safe;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_BACKUP) {
            if (uploadCallback != null) {
                Uri result = resultCode == RESULT_OK && data != null ? data.getData() : null;
                uploadCallback.onReceiveValue(result != null ? new Uri[] {result} : null);
                uploadCallback = null;
            }
            return;
        }
        if (requestCode == REQ_SAVE_BACKUP) {
            final String content = pendingBackupJson;
            pendingBackupJson = null;
            pendingBackupFilename = null;
            Uri result = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (result == null || content == null) return;
            new Thread(() -> {
                boolean success = false;
                try (OutputStream output = getContentResolver().openOutputStream(result, "wt")) {
                    if (output == null) throw new IllegalStateException("No output stream");
                    output.write(content.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    success = true;
                } catch (Exception ignored) { }
                final boolean saved = success;
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    saved ? "✅ Backup JSON salvo!" : "⚠️ Não foi possível salvar o backup.",
                    Toast.LENGTH_LONG).show());
            }, "nine-kingdom-backup").start();
        }
    }

    @Override
    public void onBackPressed() {
        if (gameView != null) {
            gameView.evaluateJavascript("try { if (typeof saveGame === 'function') saveGame(false); } catch(e) {}", null);
        }
        new AlertDialog.Builder(this)
            .setTitle("Sair do Nine Kingdom?")
            .setMessage("O progresso é guardado neste aplicativo. Exporte um backup JSON antes de desinstalá-lo.")
            .setPositiveButton("Sair", (d, w) -> finish())
            .setNegativeButton("Continuar jogando", null)
            .show();
    }

    @Override
    protected void onPause() {
        if (gameView != null) {
            gameView.evaluateJavascript("try { if (typeof saveGame === 'function') saveGame(false); } catch(e) {}", null);
            gameView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (uploadCallback != null) {
            uploadCallback.onReceiveValue(null);
            uploadCallback = null;
        }
        if (gameView != null) {
            gameView.removeJavascriptInterface("NineKingdomAndroid");
            gameView.destroy();
            gameView = null;
        }
        super.onDestroy();
    }
}
