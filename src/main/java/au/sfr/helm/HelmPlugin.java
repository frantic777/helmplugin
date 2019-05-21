package au.sfr.helm;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.Arrays;

public class HelmPlugin implements Plugin<Project> {

    private final String HELM_GROUP = "helm";

    @Override
    public void apply(Project project) {
        project.getTasks().create("downloadHelm", Helm.class, task -> {
            task.setGroup(HELM_GROUP);
            System.out.println(Arrays.toString(project.getProperties().keySet().toArray()));
        });
    }
}
