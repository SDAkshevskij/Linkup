package io.linkup.app;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/ui/index.html";
    private static final int BACKGROUND = 0xff101211;
    private static final int FILE_PICKER = 10;
    private final ReleaseUpdater updater = new ReleaseUpdater(this);
    private WebSession session;
    WebView web;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = (WebSession) getLastNonConfigurationInstance();
        boolean firstPage = session == null;
        if (firstPage) session = new WebSession(this);

        web = session.web;
        session.context.setBaseContext(this);
        session.bridge.activity = this;
        attachWebView();
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                return chooseFiles(callback);
            }
        });

        if (firstPage) {
            receive(getIntent());
            web.loadUrl(START_URL);
        }
        CollectJob.schedule(this);
    }

    private void attachWebView() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarContrastEnforced(false);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets viewport = Insets.max(safe, insets.getInsets(WindowInsetsCompat.Type.ime()));
            if (view.getPaddingLeft() != viewport.left || view.getPaddingTop() != viewport.top
                    || view.getPaddingRight() != viewport.right || view.getPaddingBottom() != viewport.bottom) {
                view.setPadding(viewport.left, viewport.top, viewport.right, viewport.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    private boolean chooseFiles(ValueCallback<Uri[]> callback) {
        if (session.chooser != null) session.chooser.onReceiveValue(null);
        session.chooser = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, FILE_PICKER);
        } catch (ActivityNotFoundException unavailable) {
            session.chooser = null;
            callback.onReceiveValue(null);
        }
        return true;
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        ViewCompat.requestApplyInsets((View) web.getParent());
    }

    @Override public Object onRetainNonConfigurationInstance() {
        return session;
    }

    @Override protected void onResume() {
        super.onResume();
        CollectJob.requestForeground(this);
        web.evaluateJavascript("window.linkupActivityResume?.()", null);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receive(intent);
        web.evaluateJavascript("window.linkupImportShare && window.linkupImportShare()", null);
    }

    private void receive(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        synchronized (session) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null) session.shareText = text.toString();
            if (Intent.ACTION_SEND.equals(action)) {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null && "content".equals(uri.getScheme())) session.shares.add(uri);
            } else {
                ArrayList<Uri> files = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (files != null) for (Uri uri : files) {
                    if ("content".equals(uri.getScheme())) session.shares.add(uri);
                }
            }
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (updater.onActivityResult(request, result)) return;
        if (request != FILE_PICKER || session.chooser == null) return;
        ValueCallback<Uri[]> callback = session.chooser;
        session.chooser = null;
        Uri[] files = null;
        if (result == RESULT_OK && data != null) {
            ClipData clips = data.getClipData();
            if (clips != null) {
                files = new Uri[clips.getItemCount()];
                for (int i = 0; i < files.length; i++) files[i] = clips.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                files = new Uri[]{data.getData()};
            }
        }
        callback.onReceiveValue(files);
    }

    @Override protected void onDestroy() {
        updater.close();
        session.bridge.activity = null;
        web.setWebChromeClient(null);
        ((ViewGroup) web.getParent()).removeView(web);
        session.context.setBaseContext(getApplicationContext());
        if (!isChangingConfigurations()) {
            if (session.chooser != null) session.chooser.onReceiveValue(null);
            web.destroy();
            session.collector.close();
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("(()=>{let d=document.querySelector('dialog[open]');if(d){d.close();return true}return false})()",
                result -> { if (!"true".equals(result)) moveTaskToBack(true); });
    }

    // Keep the live document and its transient state in memory during an Activity relaunch.
    private static final class WebSession {
        final MutableContextWrapper context;
        final WebView web;
        final Bridge bridge = new Bridge();
        final CloudProbe cloudProbe = new CloudProbe();
        final Collector collector;
        final Map<String, Uri> downloads = new HashMap<>();
        final ArrayList<Uri> shares = new ArrayList<>();
        String shareText = "";
        ValueCallback<Uri[]> chooser;

        WebSession(Activity activity) {
            Context app = activity.getApplicationContext();
            context = new MutableContextWrapper(activity);
            collector = new Collector(app);
            web = new WebView(context) {
                @Override public InputConnection onCreateInputConnection(EditorInfo out) {
                    InputConnection input = super.onCreateInputConnection(out);
                    out.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
                    return input;
                }
            };
            web.setBackgroundColor(BACKGROUND);
            web.setScrollbarFadingEnabled(true);
            web.setHorizontalScrollBarEnabled(false);
            web.setVerticalScrollbarTrackDrawable(null);
            GradientDrawable thumb = new GradientDrawable();
            thumb.setColor(0xff535e53);
            thumb.setCornerRadius(4 * app.getResources().getDisplayMetrics().density);
            web.setVerticalScrollbarThumbDrawable(thumb);
            web.setScrollBarSize(Math.round(4 * app.getResources().getDisplayMetrics().density));
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSupportMultipleWindows(false);
            web.addJavascriptInterface(bridge, "LinkUpNative");
            web.setWebViewClient(new LocalAssets(app));
        }
    }

    private static final class LocalAssets extends WebViewClient {
        private final Context app;

        LocalAssets(Context app) {
            this.app = app;
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return !"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost());
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!HOST.equals(uri.getHost())) return null;
            String path = uri.getPath();
            if (path == null || !path.startsWith("/ui/") || path.contains("..")) {
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            String file = path.substring(4);
            String mime = file.endsWith(".js") ? "application/javascript"
                    : file.endsWith(".css") ? "text/css"
                    : file.endsWith(".html") ? "text/html"
                    : file.endsWith(".svg") ? "image/svg+xml"
                    : file.endsWith(".png") ? "image/png" : "application/octet-stream";
            try {
                Map<String, String> headers = Collections.singletonMap("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' blob: data:; connect-src https: wss:; object-src 'none'; frame-src 'none'; base-uri 'none'");
                return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, app.getAssets().open(file));
            } catch (IOException missing) {
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                        Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
        }
    }

    private static final class Bridge {
        volatile MainActivity activity;

        @JavascriptInterface public String call(String action, String raw) {
            MainActivity current = activity;
            return current == null ? "{\"error\":\"LinkUp возвращается на экран\"}" : current.callFromWeb(action, raw);
        }
    }

 private String callFromWeb(String action,String raw){try{JSONObject a=new JSONObject(raw);Object v=JSONObject.NULL;switch(action){case "probeSupabase":v=session.cloudProbe.start(a.getString("url"));break;case "probeSupabaseResult":v=session.cloudProbe.result();break;case "checkUpdate":runOnUiThread(updater::check);break;case "activityDiagnostics":v=session.collector.diagnostics();break;case "status":v=session.collector.status();break;case "activity":try{session.collector.collect();}catch(Exception failure){session.collector.p.edit().putString("collect_error","Не удалось считать данные Android").apply();}v=new JSONObject().put("rows",session.collector.rows(a.getLong("from"),a.getLong("to"))).put("status",session.collector.status());break;case "configure":session.collector.configure(a);CollectJob.schedule(MainActivity.this);CollectJob.requestForeground(MainActivity.this);break;case "eraseActivity":session.collector.erase();break;case "disableCloud":session.collector.disableCloud();break;case "permission":runOnUiThread(()->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));break;case "locationPermission":runOnUiThread(()->{if(!session.collector.locationAllowed())requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},20);else if(checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}});break;case "openUrl":Uri u=Uri.parse(a.getString("url"));if(!"https".equals(u.getScheme())||!"www.openstreetmap.org".equals(u.getHost()))throw new Exception("URL не разрешён");runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,u)));break;case "copy":runOnUiThread(()->getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("LinkUp",a.optString("text"))));break;case "paste":{android.content.ClipboardManager cm=getSystemService(android.content.ClipboardManager.class);ClipData clip=cm.getPrimaryClip();v=clip==null?"":clip.getItemAt(0).coerceToText(MainActivity.this).toString();break;}case "downloadBegin":{String name=a.getString("name").replaceAll("[/\\\\\\x00]","_");ContentValues cv=new ContentValues();cv.put(MediaStore.Downloads.DISPLAY_NAME,name);cv.put(MediaStore.Downloads.MIME_TYPE,a.optString("mime","application/octet-stream"));cv.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/LinkUp");cv.put(MediaStore.Downloads.IS_PENDING,1);Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);if(uri==null)throw new Exception("Не удалось создать файл");String id=UUID.randomUUID().toString();session.downloads.put(id,uri);v=id;break;}case "downloadChunk":{Uri uri=session.downloads.get(a.getString("id"));if(uri==null)throw new Exception("Файл не открыт");try(OutputStream out=getContentResolver().openOutputStream(uri,"wa")){out.write(android.util.Base64.decode(a.getString("data"),android.util.Base64.DEFAULT));}break;}case "downloadEnd":case "downloadAbort":{Uri uri=session.downloads.remove(a.getString("id"));if(uri!=null){if(action.equals("downloadAbort"))getContentResolver().delete(uri,null,null);else{ContentValues cv=new ContentValues();cv.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(uri,cv,null,null);}}break;}case "shareList":{JSONArray list=new JSONArray();synchronized(MainActivity.this){for(int i=0;i<session.shares.size();i++){Uri uri=session.shares.get(i);String name="file";long size=-1;try(Cursor q=getContentResolver().query(uri,null,null,null,null)){if(q!=null&&q.moveToFirst()){int n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME),z=q.getColumnIndex(OpenableColumns.SIZE);if(n>=0)name=q.getString(n);if(z>=0)size=q.getLong(z);}}list.put(new JSONObject().put("index",i).put("name",name).put("size",size).put("mime",getContentResolver().getType(uri)));}v=new JSONObject().put("text",session.shareText).put("files",list);}break;}case "shareRead":{int index=a.getInt("index");Uri uri; synchronized(MainActivity.this){uri=session.shares.get(index);}long off=a.getLong("offset");try(InputStream in=getContentResolver().openInputStream(uri)){long skipped=0;while(skipped<off){long n=in.skip(off-skipped);if(n<=0){if(in.read()==-1)break;n=1;}skipped+=n;}byte[] buf=new byte[192*1024];int n=0,x;while(n<buf.length&&(x=in.read(buf,n,buf.length-n))>0)n+=x;v=android.util.Base64.encodeToString(buf,0,n,android.util.Base64.NO_WRAP);}break;}case "shareClear":synchronized(MainActivity.this){session.shareText="";session.shares.clear();}break;default:throw new Exception("Неизвестная операция");}return new JSONObject().put("value",v).toString();}catch(Exception e){try{return new JSONObject().put("error",String.valueOf(e.getMessage())).toString();}catch(Exception ignored){return "{\"error\":\"Unknown\"}";}}}
}
