package au.sfr.helm;

public class Helm {
    private String helmVersion = "";
    private String uploadUrl = "";
    private String user = "";
    private String password = "";
    private boolean sslDisabled = false;

    public Helm() {
    }

    public String getHelmVersion() {
        return helmVersion;
    }

    public void setHelmVersion(String helmVersion) {
        this.helmVersion = helmVersion;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSslDisabled() {
        return sslDisabled;
    }

    public void setSslDisabled(boolean sslDisabled) {
        this.sslDisabled = sslDisabled;
    }
}
