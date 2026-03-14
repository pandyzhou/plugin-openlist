package run.halo.openlist;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * 同步 OpenList 文件到 Halo 附件库的 API 端点。
 */
@Component
public class OpenListSyncEndpoint implements CustomEndpoint {

    private static final Logger log =
        LoggerFactory.getLogger(OpenListSyncEndpoint.class);

    private static final String REMOTE_PATH_ANNO =
        "storage.halo.run/openlist-remote-path";

    private static final String EXTERNAL_LINK_ANNO =
        "storage.halo.run/external-link";

    private static final JsonMapper JSON_MAPPER =
        JsonMapper.builder().build();

    private final ReactiveExtensionClient client;
    private final OpenListClient openListClient = new OpenListClient();

    public OpenListSyncEndpoint(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route(POST("/openlist/sync"), this::handleSync);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.console.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> handleSync(ServerRequest request) {
        var policyName = request.queryParam("policyName").orElse("");
        if (!StringUtils.hasText(policyName)) {
            return ServerResponse.badRequest()
                .bodyValue(Map.of("message", "policyName is required"));
        }

        return client.get(Policy.class, policyName)
            .flatMap(policy -> {
                var configMapName = policy.getSpec().getConfigMapName();
                if (!StringUtils.hasText(configMapName)) {
                    return Mono.error(new RuntimeException(
                        "Policy has no configMap"));
                }
                return client.get(ConfigMap.class, configMapName)
                    .map(cm -> Map.entry(policy, cm));
            })
            .flatMap(entry -> {
                var policy = entry.getKey();
                var configMap = entry.getValue();
                var props = resolveProperties(configMap);
                var basePath = props.getNormalizedUploadPath();
                if (!StringUtils.hasText(basePath)) {
                    basePath = "/";
                }

                var added = new AtomicInteger(0);
                var skipped = new AtomicInteger(0);

                return scanAndSync(props, basePath, policyName,
                    added, skipped)
                    .then(Mono.defer(() ->
                        ServerResponse.ok().bodyValue(Map.of(
                            "added", added.get(),
                            "skipped", skipped.get(),
                            "message", "sync completed"
                        ))
                    ));
            })
            .onErrorResume(e -> {
                log.error("Sync failed", e);
                return ServerResponse.status(500)
                    .bodyValue(Map.of("message",
                        "Sync failed: " + e.getMessage()));
            });
    }

    /**
     * 递归扫描目录并同步文件。
     */
    private Mono<Void> scanAndSync(OpenListProperties props,
                                    String dirPath,
                                    String policyName,
                                    AtomicInteger added,
                                    AtomicInteger skipped) {
        return openListClient.listFiles(props, dirPath)
            .flatMapMany(Flux::fromIterable)
            .concatMap(item -> {
                var fullPath = dirPath.endsWith("/")
                    ? dirPath + item.name()
                    : dirPath + "/" + item.name();
                if (item.isDir()) {
                    return scanAndSync(props, fullPath, policyName,
                        added, skipped);
                }
                return syncFile(props, fullPath, item, policyName,
                    added, skipped);
            })
            .then();
    }

    /**
     * 同步单个文件：检查是否已存在，不存在则创建 Attachment。
     */
    private Mono<Void> syncFile(OpenListProperties props,
                                 String remotePath,
                                 OpenListClient.FileItem item,
                                 String policyName,
                                 AtomicInteger added,
                                 AtomicInteger skipped) {
        // 查找是否已有此 remotePath 的附件
        return client.list(Attachment.class,
                a -> {
                    var annos = a.getMetadata().getAnnotations();
                    return annos != null
                        && remotePath.equals(
                        annos.get(REMOTE_PATH_ANNO));
                }, null)
            .collectList()
            .flatMap(existing -> {
                if (!existing.isEmpty()) {
                    skipped.incrementAndGet();
                    return Mono.empty();
                }
                return createAttachment(props, remotePath, item,
                    policyName)
                    .doOnSuccess(a -> added.incrementAndGet())
                    .then();
            });
    }

    private Mono<Attachment> createAttachment(
        OpenListProperties props,
        String remotePath,
        OpenListClient.FileItem item,
        String policyName) {

        var filename = remotePath.contains("/")
            ? remotePath.substring(remotePath.lastIndexOf('/') + 1)
            : remotePath;

        var permalink = buildPermalink(props, remotePath);

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(Map.of(
            REMOTE_PATH_ANNO, remotePath,
            EXTERNAL_LINK_ANNO, permalink
        ));

        var spec = new Attachment.AttachmentSpec();
        spec.setDisplayName(filename);
        spec.setSize(item.size());
        spec.setPolicyName(policyName);
        spec.setMediaType(guessMediaType(filename));

        var status = new Attachment.AttachmentStatus();
        status.setPermalink(permalink);

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        attachment.setStatus(status);

        return client.create(attachment);
    }

    private String buildPermalink(OpenListProperties props,
                                   String remotePath) {
        var segments = remotePath.split("/");
        var sb = new StringBuilder();
        for (var seg : segments) {
            if (seg.isEmpty()) continue;
            sb.append("/");
            sb.append(URLEncoder.encode(seg, StandardCharsets.UTF_8)
                .replace("+", "%20"));
        }
        return props.getNormalizedSiteUrl() + "/d" + sb;
    }

    private String guessMediaType(String filename) {
        var lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    private OpenListProperties resolveProperties(ConfigMap configMap) {
        return Optional.ofNullable(configMap)
            .map(ConfigMap::getData)
            .map(data -> data.get("default"))
            .map(json -> {
                try {
                    return JSON_MAPPER.readValue(
                        json, OpenListProperties.class);
                } catch (Exception e) {
                    log.warn("Failed to parse config", e);
                    return null;
                }
            })
            .orElseGet(OpenListProperties::new);
    }
}
