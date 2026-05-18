package run.halo.openlist;

public class OpenListPropertiesResolverTest {

    public static void main(String[] args) {
        parsesPolicyConfigJsonWithoutJackson();
        ignoresUnknownNonStringValuesWithoutDroppingKnownConfig();
        preservesDefaultTokenEndpointWhenMissing();
        returnsDefaultsForInvalidJson();
    }

    private static void parsesPolicyConfigJsonWithoutJackson() {
        var props = OpenListPropertiesResolver.fromJsonOrDefault("""
            {
              \"siteUrl\": \"https://openlist.example.com/\",
              \"username\": \"admin\",
              \"password\": \"p\\\\a\\\"s\",
              \"uploadPath\": \"/Halo/中文\",
              \"tokenEndpoint\": \"/api/auth/login/hash\"
            }
            """);

        assertEquals("https://openlist.example.com/", props.getSiteUrl());
        assertEquals("admin", props.getUsername());
        assertEquals("p\\a\"s", props.getPassword());
        assertEquals("/Halo/中文", props.getUploadPath());
        assertEquals("/api/auth/login/hash", props.getTokenEndpoint());
    }

    private static void ignoresUnknownNonStringValuesWithoutDroppingKnownConfig() {
        var props = OpenListPropertiesResolver.fromJsonOrDefault("""
            {
              \"siteUrl\": \"https://openlist.example.com\",
              \"enabled\": true,
              \"limits\": [1, 2, 3],
              \"extra\": {\"nested\": \"value\"},
              \"username\": \"admin\"
            }
            """);

        assertEquals("https://openlist.example.com", props.getSiteUrl());
        assertEquals("admin", props.getUsername());
    }

    private static void preservesDefaultTokenEndpointWhenMissing() {
        var props = OpenListPropertiesResolver.fromJsonOrDefault("""
            {
              \"siteUrl\": \"https://openlist.example.com\",
              \"username\": \"admin\",
              \"password\": \"secret\",
              \"uploadPath\": \"/Halo\"
            }
            """);

        assertEquals("/api/auth/login", props.getTokenEndpoint());
    }

    private static void returnsDefaultsForInvalidJson() {
        var props = OpenListPropertiesResolver.fromJsonOrDefault("not-json");

        assertEquals(null, props.getSiteUrl());
        assertEquals(null, props.getUsername());
        assertEquals(null, props.getPassword());
        assertEquals("", props.getUploadPath());
        assertEquals("/api/auth/login", props.getTokenEndpoint());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                "Expected " + expected + " but got " + actual
            );
        }
    }
}
