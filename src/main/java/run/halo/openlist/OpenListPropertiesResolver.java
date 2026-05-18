package run.halo.openlist;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import run.halo.app.extension.ConfigMap;

/**
 * OpenList 存储策略配置解析器。
 *
 * <p>Halo 2.22 运行时不保证提供 Jackson 3 的 tools.jackson 包，插件启动路径
 * 不能直接依赖该类。这里仅解析策略表单生成的扁平 JSON 字符串，避免因宿主缺少
 * 可选 JSON 实现导致插件无法启动。</p>
 */
final class OpenListPropertiesResolver {

    private OpenListPropertiesResolver() {
    }

    static OpenListProperties fromConfigMapOrDefault(ConfigMap configMap) {
        return Optional.ofNullable(configMap)
            .map(ConfigMap::getData)
            .map(data -> data.get("default"))
            .map(OpenListPropertiesResolver::fromJsonOrDefault)
            .orElseGet(OpenListProperties::new);
    }

    static OpenListProperties fromJsonOrDefault(String json) {
        try {
            return fromJson(json);
        } catch (RuntimeException e) {
            return new OpenListProperties();
        }
    }

    static OpenListProperties fromJson(String json) {
        var values = new Parser(json).parseObject();
        var props = new OpenListProperties();
        setIfPresent(values, "siteUrl", props::setSiteUrl);
        setIfPresent(values, "username", props::setUsername);
        setIfPresent(values, "password", props::setPassword);
        setIfPresent(values, "uploadPath", props::setUploadPath);
        setIfPresent(values, "tokenEndpoint", props::setTokenEndpoint);
        return props;
    }

    private static void setIfPresent(Map<String, String> values,
                                     String key,
                                     java.util.function.Consumer<String> setter) {
        if (values.containsKey(key) && values.get(key) != null) {
            setter.accept(values.get(key));
        }
    }

    private static final class Parser {

        private final String json;
        private int index;

        Parser(String json) {
            if (json == null) {
                throw new IllegalArgumentException("JSON must not be null");
            }
            this.json = json;
        }

        Map<String, String> parseObject() {
            var values = new LinkedHashMap<String, String>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (consume('}')) {
                ensureEnd();
                return values;
            }
            while (true) {
                skipWhitespace();
                var key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, parseValueAsStringOrNull());
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                if (consume('}')) {
                    ensureEnd();
                    return values;
                }
                throw error("Expected ',' or '}'");
            }
        }

        private String parseValueAsStringOrNull() {
            if (peek('"')) {
                return parseString();
            }
            skipValue();
            return null;
        }

        private void skipValue() {
            skipWhitespace();
            if (peek('"')) {
                parseString();
                return;
            }
            if (peek('{')) {
                skipObjectValue();
                return;
            }
            if (peek('[')) {
                skipArrayValue();
                return;
            }
            if (startsWith("true")) {
                index += 4;
                return;
            }
            if (startsWith("false")) {
                index += 5;
                return;
            }
            if (startsWith("null")) {
                index += 4;
                return;
            }
            if (peek('-') || peekDigit()) {
                skipNumberValue();
                return;
            }
            throw error("Expected JSON value");
        }

        private void skipObjectValue() {
            expect('{');
            skipWhitespace();
            if (consume('}')) {
                return;
            }
            while (true) {
                skipWhitespace();
                parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                skipValue();
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                if (consume('}')) {
                    return;
                }
                throw error("Expected ',' or '}'");
            }
        }

        private void skipArrayValue() {
            expect('[');
            skipWhitespace();
            if (consume(']')) {
                return;
            }
            while (true) {
                skipWhitespace();
                skipValue();
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                if (consume(']')) {
                    return;
                }
                throw error("Expected ',' or ']'");
            }
        }

        private void skipNumberValue() {
            if (consume('-')) {
                // optional leading minus
            }
            consumeDigits();
            if (consume('.')) {
                consumeDigits();
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }
                consumeDigits();
            }
        }

        private void consumeDigits() {
            if (!peekDigit()) {
                throw error("Expected digit");
            }
            while (peekDigit()) {
                index++;
            }
        }

        private String parseString() {
            expect('"');
            var value = new StringBuilder();
            while (index < json.length()) {
                var ch = json.charAt(index++);
                if (ch == '"') {
                    return value.toString();
                }
                if (ch == '\\') {
                    value.append(parseEscapedCharacter());
                    continue;
                }
                if (ch < 0x20) {
                    throw error("Control character is not allowed in string");
                }
                value.append(ch);
            }
            throw error("Unterminated string");
        }

        private char parseEscapedCharacter() {
            if (index >= json.length()) {
                throw error("Unterminated escape sequence");
            }
            var escaped = json.charAt(index++);
            return switch (escaped) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> parseUnicodeEscape();
                default -> throw error("Unsupported escape sequence");
            };
        }

        private char parseUnicodeEscape() {
            if (index + 4 > json.length()) {
                throw error("Incomplete unicode escape");
            }
            var hex = json.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw error("Invalid unicode escape");
            }
        }

        private void skipWhitespace() {
            while (index < json.length()
                && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (peek(expected)) {
                index++;
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            return index < json.length() && json.charAt(index) == expected;
        }

        private boolean startsWith(String expected) {
            return json.startsWith(expected, index);
        }

        private boolean peekDigit() {
            return index < json.length()
                && json.charAt(index) >= '0'
                && json.charAt(index) <= '9';
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private void ensureEnd() {
            skipWhitespace();
            if (index != json.length()) {
                throw error("Unexpected trailing content");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                message + " at character " + index
            );
        }
    }
}
