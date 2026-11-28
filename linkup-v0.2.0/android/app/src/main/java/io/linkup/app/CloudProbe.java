package io.linkup.app;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;

final class CloudProbe {
    private static final Executor NETWORK = Executors.newFixedThreadPool(2);
    private volatile JSONObject result = new JSONObject();
    private boolean running;

    synchronized void start(String project) throws Exception {
        URI uri = new URI(project);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Укажите HTTPS-адрес проекта");
        }
        if (running) return;
        running = true;
        result = new JSONObject().put("stage", "pending");
        NETWORK.execute(() -> {
            JSONObject measured = measure(uri.resolve("/auth/v1/health"));
            synchronized (this) {
                result = measured;
                running = false;
            }
        });
    }

    JSONObject result() { return result; }

    private static JSONObject measure(URI endpoint) {
        HttpURLConnection connection = null;
        String stage = "network";
        int status = 0;
        try {
            connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            status = connection.getResponseCode();
            stage = "http";
        } catch (UnknownHostException error) {
            stage = "dns";
        } catch (SSLException error) {
            stage = "tls";
        } catch (SocketTimeoutException error) {
            stage = "timeout";
        } catch (Exception ignored) {
            // The result contains a category only. No URL, headers or response body.
        } finally {
            if (connection != null) connection.disconnect();
        }
        JSONObject measured = new JSONObject();
        try { measured.put("stage", stage).put("status", status); }
        catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
        return measured;
    }
}
