package au.sfr.helmplugin;


import au.sfr.helm.HelmPlugin;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.gradle.internal.impldep.org.junit.Assert;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class HelmPluginTests {
    @SuppressWarnings("WeakerAccess")
    @TempDir
    Path testProjectDir;

    @Test
    @Order(1)
    void testHelmDownload() throws Exception {
        setUpTestProject();

        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withDebug(true)
                .withArguments(HelmPlugin.DOWNLOAD_TASK, "--stacktrace")
                .forwardStdOutput(new BufferedWriter(new OutputStreamWriter(System.out)))
                .build();

        ProcessBuilder pb = new ProcessBuilder(HelmPlugin.HELM_EXEC_LOCATION + "helm");
        pb.inheritIO();
        Process process = pb.start();
        int code = process.waitFor();
        Assert.assertEquals(0, code);
    }

    @Test
    @Order(2)
    void testPackHelmChart() throws IOException {
        setUpTestProject();
        copyTestChart();

        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withDebug(true)
                .withArguments(HelmPlugin.PACK_TASK, "--stacktrace")
                .forwardStdOutput(new BufferedWriter(new OutputStreamWriter(System.out)))
                .build();
    }

    private void copyTestChart() throws IOException {
        Path chartDestination = Files.createDirectories(testProjectDir.resolve("src").resolve("helm").resolve("hello"));
        Path chartSource = Files.createDirectories(Paths.get("src").resolve("test").resolve("resources").resolve("test-chart").resolve("hello"));
        FileUtils.copyDirectory(chartSource.toFile(), chartDestination.toFile());
    }

    private void setUpTestProject() throws IOException {
        Path buildFile = Files.createFile(testProjectDir.resolve("build.gradle"));
        Files.write(buildFile, "plugins { id 'au.sfr.helm' }".getBytes());
        Path propertiesFile = Files.createFile(testProjectDir.resolve("gradle.properties"));
        Files.write(propertiesFile, "version=1.0.1-SNAPSHOT\ngroup=au.sfr\nname=hello".getBytes());
        Path settingsFile = Files.createFile(testProjectDir.resolve("settings.gradle"));
        Files.write(settingsFile, "rootProject.name='hello'".getBytes());
    }
}
