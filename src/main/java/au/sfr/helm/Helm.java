package au.sfr.helm;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;

public class Helm extends DefaultTask {
    @Input
    private final String version;
    @Input
    private final String chartLocation;
    @Input
    private final String uploadUrl;
    @Input
    private final String user;
    @Input
    private final String password;

    public Helm(String version, String chartLocation, String uploadUrl, String user, String password) {
        this.version = version;
        this.chartLocation = chartLocation;
        this.uploadUrl = uploadUrl;
        this.user = user;
        this.password = password;
    }

    public String getVersion() {
        return version;
    }

    public String getChartLocation() {
        return chartLocation;
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
}
