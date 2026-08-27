package com.example.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GigaChatAPI {
    private static final String OAUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String CHAT_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";

    private static String accessToken = null;
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static String getKey(String envName, String propName) {
        String value = System.getenv(envName);
        if (value != null && !value.isEmpty()) return value;
        try {
            Path config = FabricLoader.getInstance().getConfigDir().resolve("gigachat.properties");
            if (Files.exists(config)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(config)) {
                    props.load(in);
                }
                value = props.getProperty(propName);
            }
        } catch (Exception ignored) {}
        return value;
    }

    public static void authenticate() throws Exception {
        String clientId = getKey("GIGA_CLIENT_ID", "client_id");
        String clientSecret = getKey("GIGA_CLIENT_SECRET", "client_secret");

        if (clientId == null || clientSecret == null) {
            throw new RuntimeException("Ключи не найдены! Создай файл config/gigachat.properties");
        }

        String auth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String rquid = UUID.randomUUID().toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OAUTH_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + auth)
                .header("RqUID", rquid)
                .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            accessToken = json.get("access_token").getAsString();
        } else {
            throw new RuntimeException("Ошибка авторизации: " + response.body());
        }
    }

    public static CompletableFuture<String> askGigaChat(String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null) authenticate();

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "GigaChat");
                requestBody.addProperty("stream", false);

                JsonObject message = new JsonObject();
                message.addProperty("role", "user");
                message.addProperty("content", userMessage);

                requestBody.add("messages", JsonParser.parseString("[" + message.toString() + "]"));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CHAT_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    return jsonResponse.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .get("message").getAsJsonObject()
                            .get("content").getAsString();
                } else {
                    return "Ошибка API: " + response.statusCode();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Ошибка: " + e.getMessage();
            }
        });
    }
}
