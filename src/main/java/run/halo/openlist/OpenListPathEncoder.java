package run.halo.openlist;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OpenList 路径编码工具。
 */
final class OpenListPathEncoder {

    private OpenListPathEncoder() {
    }

    /**
     * File-Path 请求头需要按路径语义编码：保留 / 作为目录分隔符，
     * 只编码每个路径段内的空格、中文、%、? 等特殊字符。
     */
    static String encodeFilePathHeader(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        var encoded = new StringBuilder();
        var segment = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            var ch = path.charAt(i);
            if (ch == '/') {
                encoded.append(encodePathSegment(segment.toString()));
                encoded.append('/');
                segment.setLength(0);
            } else {
                segment.append(ch);
            }
        }
        encoded.append(encodePathSegment(segment.toString()));
        return encoded.toString();
    }

    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~");
    }
}
