package au.sfr.helm;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;

public class Helm extends DefaultTask {
    @Input
    private String helmVersion;
    @Input
    private String chartLocation;
    @Input
    private String uploadUrl;
    @Input
    private String user;
    @Input
    private String password;

    public Helm() {
    }

    public String getHelmVersion() {
        return helmVersion;
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
