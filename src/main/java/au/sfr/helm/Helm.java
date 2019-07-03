package au.sfr.helm;

import java.util.HashMap;
import java.util.Map;

public class Helm {
    /**
     * Repo name -> details
     */
    private Map<String, Repository> repositories = new HashMap<>();
    private boolean sslChecksDisabled = false;
    private boolean ignorePushError = false;

    public Helm() {
    }

    public Map<String, Repository> getRepositories() {
        return repositories;
    }

    public void setRepositories(Map<String, Repository> repositories) {
        this.repositories = repositories;
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
        private String url;
        private String user;
        private String password;

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
