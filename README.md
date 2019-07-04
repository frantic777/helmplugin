# Helm Gradle plugin
This plugin is developed to close gaps in other plugins I have encountered in my use-cases:
1) Multiple Helm repositories support. If you have different repositories for snapshots, candidates and releases and you want your charts to be promoted when they reach certain stage, then this plugin allows you defining them and it will create a push task for each of them
2) Multiple Kubernetes repositories support. This plugin will create install, delete and purge task for each context allowing you to work with multiple environments defined in your K8S cluster
3) Pure java implementation. This plugin doesnt require external helm client and uses just java libraries to talk to Tiller
## downloadHelm task
**downloadHelm** fetches latest (until otherwise specified in the configuration) stable version of Helm tool and unpacks it into build directory.
