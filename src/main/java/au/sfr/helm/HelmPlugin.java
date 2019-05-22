package au.sfr.helm;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

public class HelmPlugin implements Plugin<Project> {
    private static final Logger log = LoggerFactory.getLogger(HelmPlugin.class);

    private static final String HELM_GROUP = "helm";

    private static final String LATEST = "latest";
    private static final String PROPERTY_ARCH = "os.arch";
    private static final String PROPERTY_OS = "os.name";
    private static final String PLACEHOLDER_TAG = "$TAG";
    private static final String PLACEHOLDER_ARCH = "$ARCH";
    private static final String PLACEHOLDER_OS = "$OS";
    private static final String HELM_DIST_TEMPLATE = "helm-" + PLACEHOLDER_TAG + "-" + PLACEHOLDER_OS + "-" + PLACEHOLDER_ARCH + ".tar.gz";
    private static final String URL_LATEST_VERSION = "https://github.com/helm/helm/releases/latest";
    private static final String URL_DOWNLOAD = "https://kubernetes-helm.storage.googleapis.com/";
    private final String HELM = "helm";

    @Override
    public void apply(Project project) {
        project.getTasks().create("downloadHelm", Helm.class, task -> {
            try {
                task.setGroup(HELM_GROUP);
                String arch = System.getProperty(PROPERTY_ARCH).toLowerCase();
                String os = System.getProperty(PROPERTY_OS).toLowerCase();
                String tag = Objects.requireNonNullElse(task.getVersion(), getLatestVersion());

                String fileName = HELM_DIST_TEMPLATE.replace(PLACEHOLDER_TAG, tag).replace(PLACEHOLDER_OS, os).replace(PLACEHOLDER_ARCH, arch);

                String downloadURL = URL_DOWNLOAD + fileName;

                TarArchiveInputStream files = new TarArchiveInputStream(new GZIPInputStream(new URL(downloadURL).openStream()));

                ArchiveEntry entry = files.getNextEntry();
                boolean helmFound = false;
                while (entry != null) {
                    if (entry.getName().equalsIgnoreCase(HELM)){
                        helmFound = true;
                        File helmFile = new File("build/helm/helm");
                        helmFile.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(helmFile);
                        IOUtils.copy(files, fos);
                    }
                    entry = files.getNextEntry();
                }
                if (!helmFound) {
                    throw new RuntimeException("Helm executable not found.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public String getLatestVersion() throws IOException {
        URLConnection urlConnection = new URL(URL_LATEST_VERSION).openConnection();
        InputStream is = urlConnection.getInputStream();
        String latestUrl = urlConnection.getURL().getPath();
        return latestUrl.substring(latestUrl.lastIndexOf('/') + 1);
    }
}
