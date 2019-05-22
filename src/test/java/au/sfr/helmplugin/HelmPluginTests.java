package au.sfr.helmplugin;


import org.gradle.internal.impldep.org.junit.Assert;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.gradle.internal.impldep.org.hamcrest.CoreMatchers.equalTo;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;

public class HelmPluginTests {
    @TempDir
    Path testProjectDir;
    private static final String DOWNLOAD_HELM_TASK = "downloadHelm";

    @Test
    public void testHelmDownload() throws Exception {
        setUpTestProject();

        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withDebug(true)
                .withArguments(DOWNLOAD_HELM_TASK, "--stacktrace")
                .build();

        Assert.assertThat(
                Objects.requireNonNull(result.task(":" + DOWNLOAD_HELM_TASK)).getOutcome(),
                equalTo(UP_TO_DATE));
    }

    private void setUpTestProject() throws IOException {
        Path buildFile = Files.createFile(testProjectDir.resolve("build.gradle"));
        Files.write(buildFile, "plugins { id 'au.sfr.helm' }".getBytes());
    }
}
