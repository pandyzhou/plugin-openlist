package run.halo.openlist;

/**
 * OpenList 存储策略配置，从 ConfigMap 反序列化。
 */
public class OpenListProperties {

    private String siteUrl;

    private String username;

    private String password;

    private String uploadPath = "";

    private String tokenEndpoint = "/api/auth/login";

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUploadPath() {
        return uploadPath;
    }

    public void setUploadPath(String uploadPath) {
        this.uploadPath = uploadPath;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    /**
     * 获取去除尾部斜杠的站点地址。
     */
    public String getNormalizedSiteUrl() {
        if (siteUrl == null) {
            return "";
        }
        return siteUrl.endsWith("/")
            ? siteUrl.substring(0, siteUrl.length() - 1)
            : siteUrl;
    }

    /**
     * 获取规范化的上传路径（确保以 / 开头，不以 / 结尾）。
     * 如果为空则返回空字符串，表示直接在年月目录下存储。
     */
    public String getNormalizedUploadPath() {
        if (uploadPath == null || uploadPath.isBlank()) {
            return "";
        }
        var path = uploadPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
