package au.sfr.helm;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
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

    private static void disableSSLCertificateChecking() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }
        }};

        try {
            SSLContext sc = SSLContext.getInstance("SSL");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void apply(Project project) {
        Helm helm = project.getExtensions().create("helm", Helm.class);
        project.getTasks().create(DOWNLOAD_TASK, DefaultTask.class, task -> downloadHelmTask(task, helm));
        project.getTasks().create(PACK_TASK, DefaultTask.class, this::packHelmTask);
        project.getTasks().create(INIT_CLIENT_TASK, DefaultTask.class, this::initHelmClientTask);
        project.getTasks().create(PUSH_CHART_TASK, DefaultTask.class, task -> pushChartTask(task, helm));
    }

    private void pushChartTask(DefaultTask task, Helm helm) {
        task.setGroup(HELM_GROUP);
        task.setDependsOn(Collections.singleton(PACK_TASK));
        if (helm.isSslDisabled()) {
            System.out.println("WARNING: Disabling SSL Checks");
            disableSSLCertificateChecking();
        }
        task.doLast(t -> {
            File chartFile = HelmPlugin.chartFile.get();
            if (chartFile == null) {
                throw new RuntimeException("Build chart first");
            } else if (helm.getUploadUrl() == null || helm.getUploadUrl().length() == 0) {
                throw new RuntimeException("URL is not set");
            }
            HttpClient httpClient = HttpClient.newBuilder().authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(helm.getUser(), helm.getPassword().toCharArray());
                }
            }).build();
            String publishUrl = helm.getUploadUrl() + "/" + getLastFile(chartFile.toString());
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(publishUrl)).PUT(HttpRequest.BodyPublishers.ofFile(chartFile.toPath())).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Exit code " + response.statusCode() + "\n" + response.body());
                } else {
                    System.out.println(response.body());
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

        });
    }

    private void initHelmClientTask(DefaultTask task) {
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

    private void packHelmTask(DefaultTask task) {
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

    private void downloadHelmTask(DefaultTask task, Helm helm) {
        task.setGroup(HELM_GROUP);
        task.doLast(t -> {
            try {
                if (helm.isSslDisabled()) {
                    System.out.println("WARNING: Disabling SSL Checks");
                    disableSSLCertificateChecking();
                }
                boolean exists = Path.of(HELM_EXEC_LOCATION).toFile().exists() && Files.walk(Path.of(HELM_EXEC_LOCATION)).anyMatch(path -> path.getFileName().startsWith("helm"));
                if (!exists) {
                    String arch = System.getProperty(PROPERTY_ARCH).toLowerCase();
                    String os = System.getProperty(PROPERTY_OS).toLowerCase();
                    String tag = helm.getHelmVersion().length() > 0 ? helm.getHelmVersion() : getLatestVersion();

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
        Files.createDirectories(Path.of(HELM_EXEC_LOCATION));
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
