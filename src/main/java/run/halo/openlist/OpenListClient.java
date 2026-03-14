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
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenList API 客户端，封装登录、上传、删除、获取文件信息等操作。
 * Token 按 siteUrl 缓存，48 小时过期后自动刷新。
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
     * 获取 token，优先从缓存读取。
     */
    public Mono<String> getToken(OpenListProperties props) {
        var siteUrl = props.getNormalizedSiteUrl();
        var cached = TOKEN_CACHE.get(siteUrl);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.token());
        }
        return login(props).doOnNext(token ->
            TOKEN_CACHE.put(siteUrl, new TokenEntry(token, Instant.now()))
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
     */
    public Mono<Void> upload(OpenListProperties props, String remotePath,
                             Flux<DataBuffer> content, long fileSize) {
        return getToken(props).flatMap(token -> {
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
        });
    }

    /**
     * 删除 OpenList 上的文件（POST /api/fs/remove）。
     */
    public Mono<Void> delete(OpenListProperties props, String dir,
                             String filename) {
        return getToken(props).flatMap(token -> {
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
        });
    }

    /**
     * 创建目录（POST /api/fs/mkdir）。
     */
    public Mono<Void> mkdir(OpenListProperties props, String path) {
        return getToken(props).flatMap(token -> {
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
        });
    }

    /**
     * 清除指定站点的 token 缓存。
     */
    public void evictToken(String siteUrl) {
        TOKEN_CACHE.remove(siteUrl);
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
