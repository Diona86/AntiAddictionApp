package com.exampl.antiaddiction.cloudbase;

import android.os.Handler;
import android.os.Looper;

import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudBaseClient {

    private final String baseUrl;
    private String accessToken;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public CloudBaseClient(String envId, String accessToken) {
        this.baseUrl = "https://" + envId + ".api.tcloudbasegateway.com";
        this.accessToken = accessToken;
    }

    public void updateAccessToken(String newToken) {
        this.accessToken = newToken;
    }

    /**
     * 统一请求方法
     *
     * @param method        HTTP 方法：GET / POST / PUT / PATCH / DELETE
     * @param path          API 路径，例如 /v1/rdb/rest/table_name
     * @param body          请求体，传 null 则发送 {}
     * @param customHeaders 额外 headers，可传 null
     * @param typeToken     Gson 反序列化类型，例如 new TypeToken<List<Map<String,Object>>>(){}
     * @param callback      结果回调（主线程回调）
     */
    public <T> void request(
            String method,
            String path,
            Object body,
            Map<String, String> customHeaders,
            TypeToken<T> typeToken,
            CloudBaseCallback<T> callback
    ) {
        executor.execute(() -> {
            okhttp3.HttpUrl baseHttpUrl = okhttp3.HttpUrl.parse(baseUrl);
            okhttp3.HttpUrl.Builder urlBuilder = baseHttpUrl.newBuilder();

// 分离 path 和 query 参数
            String[] parts = path.split("\\?", 2);
            urlBuilder.addPathSegments(parts[0].replaceFirst("^/", ""));

            if (parts.length > 1) {
                for (String param : parts[1].split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        try {
                            urlBuilder.addQueryParameter(kv[0],
                                    java.net.URLDecoder.decode(kv[1], "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            String url = urlBuilder.build().toString();

            // 构建请求体
            MediaType json = MediaType.parse("application/json");
            String bodyStr = (body != null) ? gson.toJson(body) : "{}";
            RequestBody requestBody = RequestBody.create(bodyStr, json);

            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken);

            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            switch (method.toUpperCase()) {
                case "GET":    builder.get(); break;
                case "POST":   builder.post(requestBody); break;
                case "PUT":    builder.put(requestBody); break;
                case "PATCH":  builder.patch(requestBody); break;
                case "DELETE": builder.delete(requestBody); break;
            }

            try (Response response = client.newCall(builder.build()).execute()) {
                if (response.isSuccessful()) {
                    String respStr = response.body() != null ? response.body().string() : null;

                    T result;
                    if (respStr == null || respStr.isEmpty()) {
                        result = null;
                    } else if (typeToken != null) {
                        result = gson.fromJson(respStr, typeToken.getType());
                    } else {
                        //noinspection unchecked
                        result = (T) gson.fromJson(respStr, Object.class);
                    }

                    T finalResult = result;
                    mainHandler.post(() -> callback.onSuccess(finalResult));
                } else {
                    String errStr = response.body() != null ? response.body().string() : "";
                    int code = response.code();
                    mainHandler.post(() -> callback.onError(code, errStr));
                }
            } catch (IOException | SecurityException e) {
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> callback.onError(-1, err));
            } catch (Exception e) {
                String err = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> callback.onError(-1, err));
            }
        });
    }
}