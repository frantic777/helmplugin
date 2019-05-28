package au.sfr.helmplugin;


import org.gradle.internal.impldep.org.junit.Assert;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;

class HelmPluginTests {
    @TempDir
    Path testProjectDir;
    private static final String DOWNLOAD_HELM_TASK = "downloadHelm";
    private static final String PACKAGE_HELM_TASK = "packHelm";

    @Test
    void testHelmDownload() throws Exception {
        setUpTestProject();

        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withDebug(true)
                .withArguments(DOWNLOAD_HELM_TASK, "--stacktrace")
                .forwardStdOutput(new BufferedWriter(new OutputStreamWriter(System.out)))
                .build();

        ProcessBuilder pb = new ProcessBuilder(testProjectDir + "/build/helm/helm");
        pb.inheritIO();
        Process process = pb.start();
        int code = process.waitFor();
        Assert.assertEquals(0, code);
    }

    @Test
    void testPackHelmChart() throws IOException, InterruptedException {
        setUpTestProject();
        copyTestChart();

        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withDebug(true)
                .withArguments(PACKAGE_HELM_TASK, "--stacktrace")
                .forwardStdOutput(new BufferedWriter(new OutputStreamWriter(System.out)))
                .build();
    }

    private void copyTestChart() throws IOException {
        Path buildFile = Files.createDirectories(testProjectDir.resolve("src").resolve("helm").resolve(testProjectDir.getFileName()));
        System.out.println(buildFile);
    }

    private void setUpTestProject() throws IOException {
        Path buildFile = Files.createFile(testProjectDir.resolve("build.gradle"));
        Files.write(buildFile, "plugins { id 'au.sfr.helm' }".getBytes());
    }
}
