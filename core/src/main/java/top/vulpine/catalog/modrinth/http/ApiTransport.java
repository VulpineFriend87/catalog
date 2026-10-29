package top.vulpine.catalog.modrinth.http;

import com.google.gson.JsonObject;
import top.vulpine.catalog.json.Json;
import top.vulpine.catalog.modrinth.ModrinthException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends requests to the Modrinth API.
 *
 * <p>Handles rate limiting, conditional requests, back-off and JSON decoding.</p>
 *
 * <p>Every method returns a future. Every request runs on this transport's own executor.</p>
 */
public final class ApiTransport implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    /** Seconds to wait when Modrinth answers 429 without a Retry-After header. */
    private static final long DEFAULT_BACK_OFF = 10L;

    private final HttpClient http;
    private final ExecutorService executor;
    private final RateLimiter limiter;
    private final ResponseCache cache;
    private final String baseUrl;
    private final String userAgent;
    private final String token;

    public ApiTransport(String baseUrl, String userAgent, String token, Path cacheDirectory,
                        int permitsPerMinute, int threads) {

        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
        this.token = token;
        this.cache = cacheDirectory == null ? null : new ResponseCache(cacheDirectory);
        this.limiter = new RateLimiter(permitsPerMinute);

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "catalog-modrinth-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        this.executor = Executors.newFixedThreadPool(threads, factory);

        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Performs a GET and decodes the body. A cached response is reused when the server reports it
     * unchanged.
     *
     * @param path the path below the API root with the query string
     * @param type the type to decode into
     * @param <T>  the decoded type
     * @return a future of the decoded body
     */
    public <T> CompletableFuture<T> get(String path, Type type) {

        return CompletableFuture.supplyAsync(() -> {

            String url = baseUrl + path;
            ResponseCache.Entry cached = cache == null ? null : cache.lookup(url);

            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET();

            if (cached != null) {
                request.header("If-None-Match", cached.etag());
            }

            HttpResponse<String> response = send(request, url);

            if (response.statusCode() == 304 && cached != null) {
                return Json.gson().fromJson(cached.body(), type);
            }

            if (cache != null) {
                response.headers().firstValue("etag")
                        .ifPresent(etag -> cache.store(url, etag, response.body()));
            }

            return Json.gson().fromJson(response.body(), type);

        }, executor);
    }

    /**
     * Performs a POST with a JSON body and decodes the reply.
     *
     * <p>Not cached.</p>
     *
     * @param path the path below the API root
     * @param body the request body
     * @param type the type to decode into
     * @param <T>  the decoded type
     * @return a future of the decoded body
     */
    public <T> CompletableFuture<T> post(String path, JsonObject body, Type type) {

        return CompletableFuture.supplyAsync(() -> {

            String url = baseUrl + path;

            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

            return Json.gson().fromJson(send(request, url).body(), type);

        }, executor);
    }

    /**
     * Downloads a file to disk.
     *
     * <p>Written to a temporary file and moved into place. Skips the rate limiter.</p>
     *
     * @param url    the absolute file URL Modrinth gave
     * @param target where to write it
     * @return a future of the written file
     */
    public CompletableFuture<Path> download(String url, Path target) {

        return CompletableFuture.supplyAsync(() -> {

            Path partial = target.resolveSibling(target.getFileName() + ".part");

            try {
                Files.createDirectories(target.getParent());
                Files.deleteIfExists(partial);

                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", userAgent)
                        .timeout(DOWNLOAD_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(partial));

                if (response.statusCode() != 200) {
                    Files.deleteIfExists(partial);
                    throw new ModrinthException("Download failed for " + url, response.statusCode(), null);
                }

                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
                return target;

            } catch (IOException e) {
                throw new ModrinthException("Download failed for " + url, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModrinthException("Download interrupted for " + url, e);
            }

        }, executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    /**
     * Sends a prepared request through the rate limiter. Retries once if Modrinth asks to slow
     * down.
     */
    private HttpResponse<String> send(HttpRequest.Builder builder, String url) {

        builder.header("User-Agent", userAgent);

        if (token != null) {
            builder.header("Authorization", token);
        }

        for (int attempt = 0; ; attempt++) {

            try {
                limiter.acquire();

                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 429 && attempt == 0) {
                    limiter.backOff(response.headers().firstValueAsLong("retry-after").orElse(DEFAULT_BACK_OFF));
                    continue;
                }

                if (status >= 400) {
                    throw new ModrinthException("Modrinth answered " + status + " for " + url, status, null);
                }

                return response;

            } catch (IOException e) {
                throw new ModrinthException("Could not reach Modrinth at " + url, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModrinthException("Interrupted while calling " + url, e);
            }
        }
    }

}
