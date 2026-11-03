package com.example.checkout;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiHttpClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;

    public InfraiHttpClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String fillForm(String pdfBase64, String fieldsJson) throws IOException, InterruptedException {
        String body = "{\"pdf\":\"" + json(pdfBase64) + "\",\"fields\":" + fieldsJson + ",\"flatten\":true}";
        return extractPdfBase64(send("POST", "/v1/pdf/form/fill", body));
    }

    public String archive(String bucket, String key, String pdfBase64) throws IOException, InterruptedException {
        return send("PUT", "/v1/storage/object/put/" + encode(bucket) + "/" + encode(key),
                "{\"data_base64\":\"" + json(pdfBase64) + "\"}");
    }

    private String send(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429 && attempt < 2) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (Pattern.compile("\"ok\"\\s*:\\s*false").matcher(envelope).find())
                throw new IOException("Infrai rejected request: " + envelope);
            if (response.statusCode() >= 400) throw new IOException("Infrai HTTP response: " + response.statusCode());
            return envelope;
        }
        throw new IOException("Infrai request did not complete");
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        long backoff = 250L * (1L << attempt);
        String retryAfter = response.headers().firstValue("Retry-After").orElse("").trim();
        try {
            long seconds = Long.parseLong(retryAfter);
            if (seconds >= 0) return Math.max(backoff, Math.multiplyExact(seconds, 1000L));
        } catch (NumberFormatException | ArithmeticException ignored) {
            // Retry-After can also be an HTTP date.
        }
        try {
            Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(backoff, Duration.between(Instant.now(), retryAt).toMillis());
        } catch (DateTimeParseException ignored) {
            return backoff;
        }
    }

    private static String extractPdfBase64(String envelope) throws IOException {
        String data = jsonValue(envelope, "data");
        if (data != null && data.startsWith("JVBERi")) return data;
        String pdf = jsonValue(envelope, "pdf_base64");
        if (pdf != null && pdf.startsWith("JVBERi")) return pdf;
        String url = jsonValue(envelope, "url");
        String prefix = "data:application/pdf;base64,";
        if (url != null && url.startsWith(prefix)) return url.substring(prefix.length());
        throw new IOException("Infrai form-fill response did not contain generated PDF data");
    }

    private static String jsonValue(String json, String name) {
        Matcher field = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"").matcher(json);
        if (!field.find()) return null;
        int start = field.end();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { value.append(c); escaped = false; }
            else if (c == '\\') escaped = true;
            else if (c == '\"') return value.toString();
            else value.append(c);
        }
        return null;
    }
}
