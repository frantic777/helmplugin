package au.sfr.helm;

public class Helm {
    private String helmVersion = "";
    private String uploadUrl = "";
    private String user = "";
    private String password = "";

    public Helm() {
    }

    public String getHelmVersion() {
        return helmVersion;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public void setHelmVersion(String helmVersion) {
        this.helmVersion = helmVersion;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
