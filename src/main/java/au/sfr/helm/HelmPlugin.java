package au.sfr.helm;

import hapi.chart.ChartOuterClass;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.microbean.helm.chart.DirectoryChartLoader;
import org.microbean.helm.chart.TapeArchiveChartWriter;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

public class HelmPlugin implements Plugin<Project> {
    public static final String PACK_TASK = "helmPack";
    public static final String INSTALL_TASK = "helmInstall";
    public static final String PURGE_TASK = "helmPurge";
    public static final String DELETE_TASK = "helmDelete";
    public static final String UPGRADE_TASK = "helmUpgrade";
    public static final String PUSH_CHART_TASK = "helmPushChart";
    private static final String HELM_GROUP = "helm";
    private static final AtomicReference<File> chartFile = new AtomicReference<>();
    private static SSLContext sslContext;

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
            sslContext = SSLContext.getInstance("SSL");

            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void apply(Project project) {
        Helm helm = project.getExtensions().create("helm", Helm.class, project);
        project.afterEvaluate(prj -> {
            helm.getRepositories().forEach((repository) -> {
                prj.getTasks().create(PUSH_CHART_TASK + repository.getName(), DefaultTask.class, task -> pushChartTask(task, helm, repository));
            });
        });
        project.getTasks().create(PACK_TASK, DefaultTask.class, this::packHelmTask);
    }

    private void pushChartTask(DefaultTask task, Helm helm, Helm.Repository repository) {
        task.setGroup(HELM_GROUP);
        task.setDependsOn(Collections.singleton(PACK_TASK));
        task.doLast(t -> {
            File chartFile = HelmPlugin.chartFile.get();
            System.out.println("Publishing " + HelmPlugin.chartFile.get());
            if (chartFile == null) {
                throw new RuntimeException("Build chart first");
            } else if (repository.getUrl() == null || repository.getUrl().isEmpty()) {
                throw new RuntimeException("URL is not set");
            }
            HttpClient.Builder builder = HttpClient.newBuilder();
            if (helm.isSslChecksDisabled()) {
                System.out.println("WARNING: Disabling SSL Checks");
                disableSSLCertificateChecking();
                builder = builder.sslContext(sslContext);
            }
            HttpClient httpClient = builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(repository.getUser(), repository.getPassword().toCharArray());
                }
            }).build();
            String publishUrl = repository.getUrl() + "/" + getLastFile(chartFile.toString());
            System.out.println("To " + publishUrl);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(publishUrl)).PUT(HttpRequest.BodyPublishers.ofFile(chartFile.toPath())).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    String result = "WARNING: PUT response code " + response.statusCode() + "\n" + response.body();
                    if (helm.isIgnorePushError()) {
                        System.out.println(result);
                    } else {
                        throw new RuntimeException(result);
                    }
                } else {
                    System.out.println(response.body());
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void packHelmTask(DefaultTask task) {
        task.setGroup(HELM_GROUP);
        task.doLast(t -> {
            try {
                Project project = task.getProject();
                File chartLocation = project.getProjectDir().toPath().resolve("build").resolve("helm").resolve(project.getName() + '-' + project.getVersion() + ".tgz").toFile();
                if (chartLocation.getParentFile().mkdirs()) {
                    System.out.println("Created " + chartLocation.getParentFile().toString() + " directory");
                }
                if (chartLocation.createNewFile()) {
                    System.out.println("Created " + chartLocation.toString() + " file");
                }
                TapeArchiveChartWriter chartWriter = new TapeArchiveChartWriter(new GZIPOutputStream(new FileOutputStream(chartLocation)));
                ChartOuterClass.Chart.Builder chartBuilder = new DirectoryChartLoader().load(project.getProjectDir().toPath().resolve("src").resolve("helm").resolve(project.getName()));
                chartBuilder.getMetadataBuilder().setVersion(project.getVersion().toString());
                chartWriter.write(chartBuilder);
                chartWriter.close();
                chartFile.set(chartLocation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String getLastFile(String latestUrl) {
        return latestUrl.substring(latestUrl.lastIndexOf('/') + 1);
    }
}
