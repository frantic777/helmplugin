package au.sfr.helm;

import hapi.chart.ChartOuterClass;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.fabric8.kubernetes.client.utils.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.kamranzafar.jtar.TarInputStream;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.DirectoryChartLoader;
import org.microbean.helm.chart.TapeArchiveChartLoader;
import org.microbean.helm.chart.TapeArchiveChartWriter;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.Iterator;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_KUBECONFIG_FILE;

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

    private static String getHomeDir() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("win")) {
            String homeDrive = System.getenv("HOMEDRIVE");
            String homePath = System.getenv("HOMEPATH");
            if (homeDrive != null && !homeDrive.isEmpty() && homePath != null && !homePath.isEmpty()) {
                String homeDir = homeDrive + homePath;
                File f = new File(homeDir);
                if (f.exists() && f.isDirectory()) {
                    return homeDir;
                }
            }
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null && !userProfile.isEmpty()) {
                File f = new File(userProfile);
                if (f.exists() && f.isDirectory()) {
                    return userProfile;
                }
            }
        }
        String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            File f = new File(home);
            if (f.exists() && f.isDirectory()) {
                return home;
            }
        }

        //Fall back to user.home should never really get here
        return System.getProperty("user.home", ".");
    }

    @Override
    public void apply(Project project) {
        Helm helm = project.getExtensions().create("helm", Helm.class, project);
        project.afterEvaluate(prj -> {
            helm.getRepositories().forEach((repository) -> {
                prj.getTasks().create(PUSH_CHART_TASK + repository.getName(), DefaultTask.class, task -> pushChartTask(task, helm, repository));
            });

            try {
                File kubeConfigFile = new File(
                        Utils.getSystemPropertyOrEnvVar(KUBERNETES_KUBECONFIG_FILE, new File(getHomeDir(), ".kube" + File.separator + "config").toString()));
                io.fabric8.kubernetes.api.model.Config k8sConfig = KubeConfigUtils.parseConfig(kubeConfigFile);

                k8sConfig.getContexts().forEach(ctx -> prj.getTasks().create(INSTALL_TASK + ctx.getName(), DefaultTask.class, task -> installOrUpgradeChartTask(task, helm, ctx.getName())));

                prj.getTasks().create(INSTALL_TASK + "Default", DefaultTask.class, task -> installOrUpgradeChartTask(task, helm, null));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

        });
        project.getTasks().create(PACK_TASK, DefaultTask.class, this::packHelmTask);
    }

    private void installOrUpgradeChartTask(DefaultTask task, Helm helm, String context) {
        task.setGroup(HELM_GROUP);
        task.setDependsOn(Collections.singleton(PACK_TASK));
        task.doLast(t -> {
            try {
                TapeArchiveChartLoader chartLoader = new TapeArchiveChartLoader();
                ChartOuterClass.Chart.Builder chart = chartLoader.load(new TarInputStream(new GZIPInputStream(new FileInputStream(chartFile.get()))));

                Config config = Config.autoConfigure(context);
                config.setNamespace(helm.getNamespace());

                try (DefaultKubernetesClient client = new DefaultKubernetesClient(config);
                     Tiller tiller = new Tiller(client);
                     ReleaseManager releaseManager = new ReleaseManager(tiller)) {
                    Iterator<hapi.services.tiller.Tiller.ListReleasesResponse> releases = releaseManager.list(hapi.services.tiller.Tiller.ListReleasesRequest.newBuilder().build());
                    String releaseName = helm.getReleaseName().isEmpty() ? task.getProject().getName() : helm.getReleaseName();
                    AtomicBoolean alreadyInstalled = new AtomicBoolean(false);
                    releases.forEachRemaining(release -> {
                        release.getReleasesList().forEach(r -> {
                            if (r.getName().equals(releaseName)) {
                                alreadyInstalled.set(true);
                            }
                        });
                    });

                    if (alreadyInstalled.get()) {
                        installChart(chart, releaseManager, releaseName);
                    } else {
                        updateChart(chart, releaseManager, releaseName);
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

    }

    private void installChart(ChartOuterClass.Chart.Builder chart, ReleaseManager releaseManager, String releaseName) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
        requestBuilder.setTimeout(300L);
        requestBuilder.setName(releaseName);
        requestBuilder.setWait(true);
        Future<InstallReleaseResponse> releaseFuture = releaseManager.install(requestBuilder, chart);
        releaseFuture.get();
    }

    private void updateChart(ChartOuterClass.Chart.Builder chart, ReleaseManager releaseManager, String releaseName) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        UpdateReleaseRequest.Builder requestBuilder = UpdateReleaseRequest.newBuilder();
        requestBuilder.setTimeout(300L);
        requestBuilder.setName(releaseName);
        requestBuilder.setWait(true);
        Future<hapi.services.tiller.Tiller.UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
        releaseFuture.get();
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
