boolean isTestResourcesConfigured(String appName) {
    return fileExists(resolveResourcePathForApplicationName(appName))
}

private String resolveResourcePathForApplicationName(String appName) {
    return "config-data/config/applications/${appName}/test/resources/"
}

void prepareTestResources(String appName, String namespace, String resourceBaseName) {
    executeRawOcCommandForProject(namespace, "apply -f ${resolveResourcePathForApplicationName(appName)}")
}

void cleanupTestResources(String appName, String namespace, String resourceBaseName) {
    executeRawOcCommandForProject(namespace, "delete -f ${resolveResourcePathForApplicationName(appName)}")
}

private void executeRawOcCommandForProject(String namespace, String command) {
    openshift.withCluster() {
        openshift.withProject(namespace) {
            def result = openshift.raw(command)
            if (!result) {
                error "Error creating test resources: ${result}"
            }
            echo "Result: ${result}"
        }
    }
}
