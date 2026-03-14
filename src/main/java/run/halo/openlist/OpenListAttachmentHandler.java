package run.halo.openlist;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Attachment.AttachmentSpec;
import run.halo.app.core.extension.attachment.Attachment.AttachmentStatus;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenList 附件处理器，实现 Halo 的 AttachmentHandler 扩展点。
 * 通过 PolicyTemplate name "openlist" 路由，每个 Policy 对应一个 OpenList 实例。
 */
@Component
public class OpenListAttachmentHandler implements AttachmentHandler {

    private static final Logger log =
        LoggerFactory.getLogger(OpenListAttachmentHandler.class);

    static final String TEMPLATE_NAME = "openlist";

    private static final String REMOTE_PATH_ANNO =
        "storage.halo.run/openlist-remote-path";

    private static final DateTimeFormatter YEAR_FMT =
        DateTimeFormatter.ofPattern("yyyy");

    private static final DateTimeFormatter MONTH_FMT =
        DateTimeFormatter.ofPattern("MM");

    private static final JsonMapper JSON_MAPPER =
        JsonMapper.builder().build();

    private final OpenListClient client = new OpenListClient();

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(ctx -> shouldHandle(ctx.policy()))
            .flatMap(ctx -> {
                var props = resolveProperties(ctx.configMap());
                var file = ctx.file();
                var originalName = file.filename();
                var now = LocalDate.now();
                var year = now.format(YEAR_FMT);
                var month = now.format(MONTH_FMT);
                var basePath = props.getNormalizedUploadPath();
                var dirPath = basePath + "/" + year + "/" + month;

                // 用 UUID 前缀避免同名覆盖
                var safeName = UUID.randomUUID().toString()
                    .substring(0, 8) + "-" + originalName;
                var remotePath = dirPath + "/" + safeName;

                // 从请求头获取文件大小，直接流式转发，不缓冲
                var contentLength = file.headers().getContentLength();

                return client.mkdir(props, dirPath)
                    .onErrorResume(e -> {
                        log.debug(
                            "mkdir may already exist: {}",
                            e.getMessage());
                        return Mono.empty();
                    })
                    .then(client.upload(
                        props, remotePath,
                        file.content(),
                        contentLength > 0 ? contentLength : -1))
                    .then(Mono.defer(() ->
                        buildAttachment(
                            props, remotePath,
                            originalName,
                            Math.max(contentLength, 0),
                            file.headers()
                                .getContentType())))
                    ;
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter(ctx -> shouldHandle(ctx.policy()))
            .flatMap(ctx -> {
                var props = resolveProperties(ctx.configMap());
                var attachment = ctx.attachment();
                var annotations =
                    attachment.getMetadata().getAnnotations();
                if (annotations == null
                    || !annotations.containsKey(REMOTE_PATH_ANNO)) {
                    return Mono.just(attachment);
                }
                var remotePath = annotations.get(REMOTE_PATH_ANNO);
                var lastSlash = remotePath.lastIndexOf('/');
                var dir = lastSlash > 0
                    ? remotePath.substring(0, lastSlash) : "/";
                var name = remotePath.substring(lastSlash + 1);

                return client.delete(props, dir, name)
                    .doOnSuccess(v -> log.info(
                        "Deleted from OpenList: {}", remotePath))
                    .onErrorResume(e -> {
                        log.warn("OpenList delete failed: {}",
                            e.getMessage());
                        return Mono.empty();
                    })
                    .thenReturn(attachment);
            });
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment,
                                   Policy policy,
                                   ConfigMap configMap) {
        if (!shouldHandle(policy)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(
            buildPermalinkUri(attachment, configMap));
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment,
                                   Policy policy,
                                   ConfigMap configMap,
                                   Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    private boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null
            || !StringUtils.hasText(
            policy.getSpec().getTemplateName())) {
            return false;
        }
        return TEMPLATE_NAME.equals(
            policy.getSpec().getTemplateName());
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
                    log.warn("Failed to parse OpenList config", e);
                    return null;
                }
            })
            .orElseGet(OpenListProperties::new);
    }

    private Mono<Attachment> buildAttachment(
        OpenListProperties props,
        String remotePath,
        String displayName,
        long fileSize,
        MediaType mediaType) {

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(Map.of(
            REMOTE_PATH_ANNO, remotePath,
            Constant.EXTERNAL_LINK_ANNO_KEY,
            buildPermalink(props, remotePath)
        ));

        var spec = new AttachmentSpec();
        spec.setDisplayName(displayName);
        spec.setSize(fileSize);
        if (mediaType != null) {
            spec.setMediaType(mediaType.toString());
        }

        var status = new AttachmentStatus();
        status.setPermalink(buildPermalink(props, remotePath));

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        attachment.setStatus(status);
        return Mono.just(attachment);
    }

    /**
     * 构建 permalink，对路径中每段做 URL 编码以支持中文文件名。
     */
    private String buildPermalink(OpenListProperties props,
                                   String remotePath) {
        var segments = remotePath.split("/");
        var sb = new StringBuilder();
        for (var seg : segments) {
            if (seg.isEmpty()) {
                continue;
            }
            sb.append("/");
            sb.append(URLEncoder.encode(seg, StandardCharsets.UTF_8)
                .replace("+", "%20"));
        }
        return props.getNormalizedSiteUrl() + "/d" + sb;
    }

    private Optional<URI> buildPermalinkUri(Attachment attachment,
                                             ConfigMap configMap) {
        var props = resolveProperties(configMap);
        var annotations =
            attachment.getMetadata().getAnnotations();
        if (annotations == null
            || !annotations.containsKey(REMOTE_PATH_ANNO)) {
            return Optional.empty();
        }
        var remotePath = annotations.get(REMOTE_PATH_ANNO);
        var permalink = buildPermalink(props, remotePath);
        return Optional.of(URI.create(permalink));
    }
}
