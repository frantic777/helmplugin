package au.sfr.helm;

public class Helm {
    private String helmVersion = "";
    private String uploadUrl = "";
    private String user = "";
    private String password = "";
    private boolean sslChecksDisabled = false;
    private boolean ignorePushError = false;

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

    public boolean isSslChecksDisabled() {
        return sslChecksDisabled;
    }

    public void setSslChecksDisabled(boolean sslChecksDisabled) {
        this.sslChecksDisabled = sslChecksDisabled;
    }

    public boolean isIgnorePushError() {
        return ignorePushError;
    }

    public void setIgnorePushError(boolean ignorePushError) {
        this.ignorePushError = ignorePushError;
    }
}
