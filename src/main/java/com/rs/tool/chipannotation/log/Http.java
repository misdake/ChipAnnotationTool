package com.rs.tool.chipannotation.log;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class Http {
    private final static Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
    private final static OkHttpClient client = new OkHttpClient.Builder().proxy(proxy).build();
    public final static Gson gson = new Gson();

    public static String get(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String string = response.body() != null ? response.body().string() : null;
            return string;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T get(String url, Class<T> clazz) {
        try {
            Thread.sleep(1000 * 7); //sleep 7 seconds. github has a 10req/min limit.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String string = response.body() != null ? response.body().string() : null;
            if (response.code() == 403) {
                System.out.println("403!");
                System.out.println(string);
            }
            T r = gson.fromJson(string, clazz);
            return r;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
