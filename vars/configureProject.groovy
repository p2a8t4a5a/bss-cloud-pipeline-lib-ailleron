def call(String destStage) {

    script {
        openshift.withCluster() {
            openshift.withProject(destStage) {
                def dcObj = openshift.selector('dc', env.APP_NAME).object()
                def podSelector = openshift.selector('pod', [deployment: "${APP_NAME}-${dcObj.status.latestVersion}"])
                timeout(time: 3, unit: 'MINUTES') {
                    podSelector.untilEach {
                        echo "pod: ${it.name()}"
                        return it.object().status.containerStatuses[0].ready
                    }
                }
            }
        }
    }
}
