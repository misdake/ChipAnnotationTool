package com.rs.tool.chipannotation.log;

import com.google.gson.Gson;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.util.List;

public class Http {

    private static String credential;

    static {
        try {
            if (new File("login.txt").exists()) {
                List<String> list = Files.readAllLines(new File("login.txt").toPath());
                if (list.size() > 0) {
                    String username = list.get(0);
                    String password = list.get(1);
                    credential = Credentials.basic(username, password);
                    System.out.println("request github api with credential");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (credential == null) {
            System.err.println("need github credential info in 'login.txt' file: `${username}\\n${password}");
            System.exit(1);
        }
    }

    private static Request request(String url) {
        Request.Builder builder = new Request.Builder();
        if (credential != null) {
            builder.header("Authorization", credential);
        }
        return builder.url(url).build();
    }

    private final static Proxy        proxy  = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
    private final static OkHttpClient client = new OkHttpClient.Builder()
            .proxy(proxy)
            .build();
    public final static  Gson         gson   = new Gson();

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
        Request request = request(url);

        int retry = 3;
        while (--retry >= 0) {
            try (Response response = client.newCall(request).execute()) {
                String string = response.body() != null ? response.body().string() : null;
                if (response.code() == 403) {
                    System.out.println("403!");
                    System.out.println(string);
                }
                String apiRemaining = response.header("X-RateLimit-Remaining");
                String apiLimit = response.header("X-RateLimit-Limit");
                if (credential == null && apiRemaining != null) {
                    System.err.println("api left: " + apiRemaining + "/" + apiLimit);
                }
                T r = gson.fromJson(string, clazz);
                return r;
            } catch (Exception e) {
                System.err.println("retry left: " + retry + "url :" + url);
                //noinspection ThrowablePrintedToSystemOut
                System.out.println(e);
            }
        }
        return null;
    }

}
