package au.sfr.helm;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

public class HelmPlugin implements Plugin<Project> {
    public static final String DOWNLOAD_TASK = "helmDownload";
    public static final String PACK_TASK = "helmPack";
    private static final String USER_HOME = System.getProperty("user.home");
    public static final String HELM_EXEC_LOCATION = USER_HOME + "/.helm/executable/";
    private static final String HELM_GROUP = "helm";
    private static final String PROPERTY_ARCH = "os.arch";
    private static final String PROPERTY_OS = "os.name";
    private static final String PLACEHOLDER_TAG = "$TAG";
    private static final String PLACEHOLDER_ARCH = "$ARCH";
    private static final String PLACEHOLDER_OS = "$OS";
    private static final String HELM_DIST_TEMPLATE = "helm-" + PLACEHOLDER_TAG + "-" + PLACEHOLDER_OS + "-" + PLACEHOLDER_ARCH + ".tar.gz";
    private static final String URL_LATEST_VERSION = "https://github.com/helm/helm/releases/latest";
    private static final String URL_DOWNLOAD = "https://kubernetes-helm.storage.googleapis.com/";
    private static final String HELM = "helm";

    @Override
    public void apply(Project project) {
        project.getTasks().create(DOWNLOAD_TASK, Helm.class, this::downloadHelmTask);
        project.getTasks().create(PACK_TASK, Helm.class, this::packHelmTask);
    }

    private void packHelmTask(Helm task) {
        Project project = task.getProject();
        Object projectVersion = project.getVersion();
        ProcessBuilder pb = new ProcessBuilder(HELM_EXEC_LOCATION + "helm", "package", "--version", projectVersion.toString(), project.getRootDir().toPath().resolve("src").resolve("helm").resolve(project.getName()).toString());
        File workingDir = project.getRootDir().toPath().resolve("build").resolve("helm").toFile();
        if (workingDir.exists() || workingDir.mkdirs()) {
            pb.directory(workingDir);
        }
        try {
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }


    private void downloadHelmTask(Helm task) {
        try {
            task.setGroup(HELM_GROUP);
            String arch = System.getProperty(PROPERTY_ARCH).toLowerCase();
            String os = System.getProperty(PROPERTY_OS).toLowerCase();
            String tag = Objects.requireNonNullElse(task.getHelmVersion(), getLatestVersion());

            String fileName = HELM_DIST_TEMPLATE.replace(PLACEHOLDER_TAG, tag).replace(PLACEHOLDER_OS, os).replace(PLACEHOLDER_ARCH, arch);

            String downloadURL = URL_DOWNLOAD + fileName;

            TarArchiveInputStream files = new TarArchiveInputStream(new GZIPInputStream(new URL(downloadURL).openStream()));
            boolean helmFound = extractHelm(files);
            if (!helmFound) {
                throw new RuntimeException("Helm executable not found.");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed downloading Helm executable", ex);
        }
    }

    private boolean extractHelm(TarArchiveInputStream files) throws IOException {
        ArchiveEntry entry = files.getNextEntry();
        boolean helmFound = false;
        while (entry != null) {
            String entryName = entry.getName();
            String fileName = getLastFile(entryName);
            if (fileName.toLowerCase().startsWith(HELM)) {
                helmFound = true;
                String helmFilePath = HELM_EXEC_LOCATION + fileName;
                File helmFile = new File(helmFilePath);
                File parentDirectory = helmFile.getParentFile();
                if (parentDirectory.exists() || parentDirectory.mkdirs()) {
                    FileOutputStream fos = new FileOutputStream(helmFile);
                    IOUtils.copy(files, fos);
                    fos.close();
                } else {
                    throw new RuntimeException("Unable to create directory: " + parentDirectory);
                }
                if (!helmFile.setExecutable(true)) {
                    throw new RuntimeException("Unable to set executable flag");
                }
            }
            entry = files.getNextEntry();
        }
        files.close();
        return helmFound;
    }

    private String getLatestVersion() throws IOException {
        URLConnection urlConnection = new URL(URL_LATEST_VERSION).openConnection();
        urlConnection.getInputStream();
        String latestUrl = urlConnection.getURL().getPath();
        return getLastFile(latestUrl);
    }

    private String getLastFile(String latestUrl) {
        return latestUrl.substring(latestUrl.lastIndexOf('/') + 1);
    }
}
