package au.sfr.helm;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

public class HelmPlugin implements Plugin<Project> {
    public static final String DOWNLOAD_TASK = "helmDownload";
    public static final String PACK_TASK = "helmPack";
    public static final String INIT_CLIENT_TASK = "helmInitClient";
    public static final String PUSH_CHART_TASK = "helmPushChart";
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
    private static final AtomicReference<File> chartFile = new AtomicReference<>();

    @Override
    public void apply(Project project) {
        project.getTasks().create(DOWNLOAD_TASK, Helm.class, this::downloadHelmTask);
        project.getTasks().create(PACK_TASK, Helm.class, this::packHelmTask);
        project.getTasks().create(INIT_CLIENT_TASK, Helm.class, this::initHelmClientTask);
        project.getTasks().create(PUSH_CHART_TASK, Helm.class, this::pushChartTask);
    }

    private void pushChartTask(Helm task) {
        task.setGroup(HELM_GROUP);
        task.doLast(t -> {
            if (chartFile.get() != null) {
                HttpClient.newBuilder().authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(task.getUser(), task.getPassword().toCharArray());
                    }
                });
            } else {
                throw new RuntimeException("Build chart first");
            }
        });
    }

    private void initHelmClientTask(Helm task) {
        task.setGroup(HELM_GROUP);
        task.doLast(t -> {
            ProcessBuilder pb = new ProcessBuilder(HELM_EXEC_LOCATION + "helm", "init", "--client-only");
            runProcess(pb);
        });
    }

    private String runProcess(ProcessBuilder pb) {
        try {
            Process process = pb.start();
            InputStream is = process.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            new Thread(() -> {
                try {
                    for (int read = is.read(); read != -1; read = process.getInputStream().read()) {
                        System.out.write(read);
                        os.write(read);
                    }
                    os.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Exit code: " + exitCode);
            }
            return os.toString();
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void packHelmTask(Helm task) {
        task.setGroup(HELM_GROUP);
        task.doLast(t -> {
            Project project = task.getProject();
            Object projectVersion = project.getVersion();
            ProcessBuilder pb = new ProcessBuilder(HELM_EXEC_LOCATION + "helm", "package", "--version", projectVersion.toString(), project.getProjectDir().toPath().resolve("src").resolve("helm").resolve(project.getName()).toString());
            File workingDir = project.getProjectDir().toPath().resolve("build").resolve("helm").toFile();
            if (workingDir.exists() || workingDir.mkdirs()) {
                pb.directory(workingDir);
            }
            String result = runProcess(pb);
            String chartLocation = result.split(": ")[1].trim();
            chartFile.set(new File(chartLocation));
        });
    }


    private void downloadHelmTask(Helm task) {
        task.setGroup(HELM_GROUP);
        task.doLast(t -> {
            try {
                boolean exists = Files.walk(Path.of(HELM_EXEC_LOCATION)).anyMatch(path -> path.getFileName().startsWith("helm"));
                if (!exists) {
                    String arch = System.getProperty(PROPERTY_ARCH).toLowerCase();
                    String os = System.getProperty(PROPERTY_OS).toLowerCase();
                    String tag = task.getHelmVersion().length() > 0 ? task.getHelmVersion() : getLatestVersion();

                    String fileName = HELM_DIST_TEMPLATE.replace(PLACEHOLDER_TAG, tag).replace(PLACEHOLDER_OS, os).replace(PLACEHOLDER_ARCH, arch);

                    String downloadURL = URL_DOWNLOAD + fileName;

                    TarArchiveInputStream files = new TarArchiveInputStream(new GZIPInputStream(new URL(downloadURL).openStream()));
                    boolean helmFound = extractHelm(files);
                    if (!helmFound) {
                        throw new RuntimeException("Helm executable not found.");
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException("Failed downloading Helm executable", ex);
            }
        });
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
