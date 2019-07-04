package au.sfr.helm;

import groovy.lang.Closure;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.List;

public class Helm {
    /**
     * Repo name -> details
     */
    private Project project;
    private List<Repository> repositories = new ArrayList<>();
    private String namespace = "default";
    private String releaseName = "";
    private long timeout = 300;
    private boolean sslChecksDisabled = false;
    private boolean ignorePushError = false;

    public Helm(Project project) {
        this.project = project;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }

    public void repository(Closure closure) {
        Repository repository = new Repository();
        project.configure(repository, closure);
        repositories.add(repository);
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    public boolean isSslChecksDisabled() {
        return sslChecksDisabled;
    }

    public void setSslChecksDisabled(boolean sslChecksDisabled) {
        this.sslChecksDisabled = sslChecksDisabled;
    }

    public boolean isIgnorePushError() {
        return ignorePushError;
    }

    public void setIgnorePushError(boolean ignorePushError) {
        this.ignorePushError = ignorePushError;
    }

    public static class Repository {
        private String name;
        private String url;
        private String user;
        private String password;

        public Repository() {
        }

        public Repository(String name, String url, String user, String password) {
            this.name = name;
            this.url = url;
            this.user = user;
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
