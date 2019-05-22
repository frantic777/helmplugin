package au.sfr.helm;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public class Helm extends DefaultTask {
    @Input
    private String version;
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
