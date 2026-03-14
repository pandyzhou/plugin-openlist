package run.halo.openlist;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenList API 客户端，封装登录、上传、删除等操作。
 * Token 按 siteUrl + username 缓存，过期或 401 时自动刷新。
 */
public class OpenListClient {

    private static final Logger log =
        LoggerFactory.getLogger(OpenListClient.class);

    private static final Duration TOKEN_TTL = Duration.ofHours(48);

    private static final Map<String, TokenEntry> TOKEN_CACHE =
        new ConcurrentHashMap<>();

    private final WebClient webClient;

    public OpenListClient() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs()
                .maxInMemorySize(256 * 1024 * 1024))
            .build();
    }

    /**
     * 生成缓存 key：siteUrl + username，避免同站不同账号冲突。
     */
    private static String cacheKey(OpenListProperties props) {
        return props.getNormalizedSiteUrl() + "|" + props.getUsername();
    }

    /**
     * 获取 token，优先从缓存读取。
     */
    public Mono<String> getToken(OpenListProperties props) {
        var key = cacheKey(props);
        var cached = TOKEN_CACHE.get(key);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.token());
        }
        return login(props).doOnNext(token ->
            TOKEN_CACHE.put(key, new TokenEntry(token, Instant.now()))
        );
    }

    /**
     * 清除缓存并重新登录（用于 401 重试）。
     */
    private Mono<String> refreshToken(OpenListProperties props) {
        TOKEN_CACHE.remove(cacheKey(props));
        return login(props).doOnNext(token ->
            TOKEN_CACHE.put(cacheKey(props),
                new TokenEntry(token, Instant.now()))
        );
    }

    private Mono<String> login(OpenListProperties props) {
        var url = props.getNormalizedSiteUrl() + props.getTokenEndpoint();
        return webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "username", props.getUsername(),
                "password", props.getPassword()
            ))
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .flatMap(resp -> {
                if (resp.code() == 200 && resp.data() != null) {
                    var token = resp.data().get("token");
                    if (token != null) {
                        return Mono.just(token.toString());
                    }
                }
                return Mono.error(new RuntimeException(
                    "OpenList login failed: " + resp.message()));
            });
    }

    /**
     * 上传文件到 OpenList（PUT /api/fs/put）。
     * content 是已缓冲的 Flux，可安全重复订阅。
     */
    public Mono<Void> upload(OpenListProperties props, String remotePath,
                             Flux<DataBuffer> content, long fileSize) {
        return getToken(props).flatMap(token ->
            doUpload(props, token, remotePath, content, fileSize)
                .onErrorResume(WebClientResponseException.Unauthorized.class,
                    e -> refreshToken(props).flatMap(newToken ->
                        doUpload(props, newToken, remotePath,
                            content, fileSize)))
        );
    }

    private Mono<Void> doUpload(OpenListProperties props, String token,
                                 String remotePath,
                                 Flux<DataBuffer> content, long fileSize) {
        var url = props.getNormalizedSiteUrl() + "/api/fs/put";
        var encodedPath = URLEncoder.encode(remotePath,
            StandardCharsets.UTF_8).replace("+", "%20");
        return webClient.put()
            .uri(url)
            .header("Authorization", token)
            .header("File-Path", encodedPath)
            .header("Content-Length", String.valueOf(fileSize))
            .body(BodyInserters.fromDataBuffers(content))
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .flatMap(resp -> {
                if (resp.code() == 200) {
                    return Mono.<Void>empty();
                }
                return Mono.error(new RuntimeException(
                    "OpenList upload failed: " + resp.message()));
            });
    }

    /**
     * 删除 OpenList 上的文件（POST /api/fs/remove）。
     */
    public Mono<Void> delete(OpenListProperties props, String dir,
                             String filename) {
        return getToken(props).flatMap(token ->
            doDelete(props, token, dir, filename)
                .onErrorResume(WebClientResponseException.Unauthorized.class,
                    e -> refreshToken(props).flatMap(newToken ->
                        doDelete(props, newToken, dir, filename)))
        );
    }

    private Mono<Void> doDelete(OpenListProperties props, String token,
                                 String dir, String filename) {
        var url = props.getNormalizedSiteUrl() + "/api/fs/remove";
        return webClient.post()
            .uri(url)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "dir", dir,
                "names", new String[]{filename}
            ))
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .flatMap(resp -> {
                if (resp.code() == 200) {
                    return Mono.<Void>empty();
                }
                return Mono.error(new RuntimeException(
                    "OpenList delete failed: " + resp.message()));
            });
    }

    /**
     * 创建目录（POST /api/fs/mkdir）。
     */
    public Mono<Void> mkdir(OpenListProperties props, String path) {
        return getToken(props).flatMap(token ->
            doMkdir(props, token, path)
                .onErrorResume(WebClientResponseException.Unauthorized.class,
                    e -> refreshToken(props).flatMap(newToken ->
                        doMkdir(props, newToken, path)))
        );
    }

    private Mono<Void> doMkdir(OpenListProperties props, String token,
                                String path) {
        var url = props.getNormalizedSiteUrl() + "/api/fs/mkdir";
        return webClient.post()
            .uri(url)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("path", path))
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .flatMap(resp -> {
                if (resp.code() == 200) {
                    return Mono.<Void>empty();
                }
                return Mono.error(new RuntimeException(
                    "OpenList mkdir failed: " + resp.message()));
            });
    }

    /**
     * 将 Flux 缓冲到内存，返回可重复订阅的 Flux 和总大小。
     */
    public static Mono<BufferedContent> bufferContent(
        Flux<DataBuffer> content) {
        return DataBufferUtils.join(content)
            .map(joined -> {
                var size = joined.readableByteCount();
                var bytes = new byte[size];
                joined.read(bytes);
                DataBufferUtils.release(joined);
                var factory = new DefaultDataBufferFactory();
                return new BufferedContent(
                    Flux.defer(() -> Flux.just(
                        factory.wrap(bytes))),
                    size
                );
            });
    }

    /**
     * 列出 OpenList 目录下的文件（POST /api/fs/list）。
     */
    @SuppressWarnings("unchecked")
    public Mono<java.util.List<FileItem>> listFiles(
        OpenListProperties props, String path) {
        return getToken(props).flatMap(token ->
            doListFiles(props, token, path)
                .onErrorResume(
                    WebClientResponseException.Unauthorized.class,
                    e -> refreshToken(props).flatMap(newToken ->
                        doListFiles(props, newToken, path)))
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<java.util.List<FileItem>> doListFiles(
        OpenListProperties props, String token, String path) {
        var url = props.getNormalizedSiteUrl() + "/api/fs/list";
        return webClient.post()
            .uri(url)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "path", path,
                "page", 1,
                "per_page", 10000,
                "refresh", false
            ))
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .map(resp -> {
                if (resp.code() != 200 || resp.data() == null) {
                    return java.util.List.<FileItem>of();
                }
                var content = resp.data().get("content");
                if (!(content instanceof java.util.List<?> list)) {
                    return java.util.List.<FileItem>of();
                }
                var items = new java.util.ArrayList<FileItem>();
                for (var item : list) {
                    if (item instanceof Map<?, ?> m) {
                        var name = String.valueOf(m.get("name"));
                        var isDir = Boolean.TRUE.equals(m.get("is_dir"));
                        var size = 0L;
                        if (m.get("size") instanceof Number n) {
                            size = n.longValue();
                        }
                        items.add(new FileItem(name, isDir, size));
                    }
                }
                return java.util.List.copyOf(items);
            });
    }

    public record FileItem(String name, boolean isDir, long size) {
    }

    public record BufferedContent(Flux<DataBuffer> content, long size) {
    }

    record TokenEntry(String token, Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(TOKEN_TTL));
        }
    }

    record ApiResponse(int code, String message,
                       Map<String, Object> data) {
    }
}
