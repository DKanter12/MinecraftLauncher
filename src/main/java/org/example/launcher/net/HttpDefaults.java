package org.example.launcher.net;

import java.net.http.HttpClient;
import java.time.Duration;

import com.google.gson.Gson;

/**
 * Общие HTTP-настройки по умолчанию, чтобы все серверные части использовали одинаковые
 * таймауты и политику перенаправлений вместо разбросанных магических чисел.
 */
public final class HttpDefaults {

    /** Таймаут установления соединения. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /** Таймаут одного обмена запрос/ответ. */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private HttpDefaults() {
    }

    /**
     * @return клиент с общими таймаутами и политикой перенаправлений.
     */
    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * @return простой экземпляр Gson для полезных нагрузок лаунчера.
     */
    public static Gson newGson() {
        return new Gson();
    }
}
