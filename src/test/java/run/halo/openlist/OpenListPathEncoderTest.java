package run.halo.openlist;

public class OpenListPathEncoderTest {

    public static void main(String[] args) {
        assertEncoded(
            "/Halo/post-contents/test_upload.txt",
            "/Halo/post-contents/test_upload.txt"
        );
        assertEncoded(
            "/Halo/2026/05/%E4%B8%AD%E6%96%87%20file%2525%3F.txt",
            "/Halo/2026/05/中文 file%25?.txt"
        );
        assertEncoded(
            "/Halo/2026/05/",
            "/Halo/2026/05/"
        );
    }

    private static void assertEncoded(String expected, String input) {
        var actual = OpenListPathEncoder.encodeFilePathHeader(input);
        if (!expected.equals(actual)) {
            throw new AssertionError(
                "Expected " + expected + " but got " + actual
            );
        }
    }
}
