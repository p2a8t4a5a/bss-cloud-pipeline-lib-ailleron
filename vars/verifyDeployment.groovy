def call(String destStage) {

    script {
        timeout(time: 5, unit: 'MINUTES') {
            openshift.withCluster() {
                openshift.withProject(destStage) {
                    //TODO: It hangs in openshift-client 1.0.23 due to regression - ITPBC-386 & https://github.com/openshift/jenkins-client-plugin/issues/209

                    def dcObj = openshift.selector('dc', env.APP_NAME).object()
                    /*
                    def changed = false
                    if (env.owners != null && dcObj.metadata.annotations['owners'] != env.owners) {
                        dcObj.metadata.annotations.put("owners", env.owners)
                        changed = true
                    }
                    if (env.description != null && dcObj.metadata.annotations['description'] != env.description) {
                        dcObj.metadata.annotations.put("description", env.description)
                        changed = true
                    }
                    if (env.metricsUrl != null && dcObj.metadata.annotations['metricsUrl'] != env.metricsUrl) {
                        dcObj.metadata.annotations.put("metricsUrl", env.metricsUrl)
                        changed = true
                    }
                    if (env.logsUrl != null && dcObj.metadata.annotations['logsUrl'] != env.logsUrl) {
                        dcObj.metadata.annotations.put("logsUrl", env.logsUrl)
                        changed = true
                    }
                    if (env.productionUse != null && dcObj.metadata.annotations['productionUse'] != env.productionUse) {
                        dcObj.metadata.annotations.put("productionUse", env.productionUse)
                        changed = true
                    }
                    if (changed) {
                        print "Modifying deployment with annotations"
                        openshift.apply(dcObj)
                    }
                    */

                    // adding git repo volume
                    print "EnvGitRepo: ${env.GIT_REPO_VOLUME}"
                    if (env.GIT_REPO_VOLUME != null && !env.GIT_REPO_VOLUME.isEmpty() && dcObj.spec.template.spec.initContainers == null) {
                        echo "Adding git repo volume"
                        dcObj.spec.template.spec.initContainers = []
                        dcObj.spec.template.spec.initContainers.add([
                                image: 'alpine/git',
                                imagePullPolicy: 'Always',
                                name: 'git-clone',
                                args: ['clone','--single-branch','--',"http://openshift-git-repo:openshift_123@10.10.99.86/${env.GIT_REPO_VOLUME}",'/repo'],
                                volumeMounts: [[
                                        mountPath:'/repo',
                                        name: 'git-repo']
                                ],
                                securityContext: [
                                        allowPrivilegeEscalation: false,
                                        readOnlyRootFilesystem: true,
                                        runAsUser: 1001830001
                                ]
                        ])
                        dcObj.spec.template.spec.containers[0].volumeMounts.add([name: 'git-repo', mountPath: '/repo'])
                        dcObj.spec.template.spec.volumes.add([emptyDir: [:], name: 'git-repo'])
                        openshift.apply(dcObj)
                    }

                    def podSelector = openshift.selector('pod', [deployment: "${APP_NAME}-${dcObj.status.latestVersion}"])

                    //Required as "podSelector.untilEach {}" ends immediately with success if selector is empty
                    //What's more in 1.0.22 'podSelector.watch {}' can hang indefinitely without entering the loop... Old plain while + sleep instead...
                    while (!podSelector.exists() || isPodInScheduledPhase(podSelector)) {
                        echo "podSelector.exists(): ${podSelector.exists()}, podSelector: ${podSelector}"
                        echo "No pod for deployment found so far. Waiting for one."
                        sleep(time: 1, unit: 'SECONDS')
                    }
                    echo "Found (${podSelector.count()}) pod(s): ${podSelector.objects()*.status}"

                    //TODO: Verify issue with "ready: false" after leaving untilEach which check it in 1.0.23 - while loop should not be needed
                    //TODO: Verify behavior with multiple pods returned - once available
                    int iterationNumber = 0
                    while(!checkContainerReadiness(podSelector)) {
                        echo "In CCR()"
                        echo "Waiting for deployment to be finished (${podSelector.names()}) (iteration ${++iterationNumber})"
                        podSelector.untilEach { //TODO: 'untilEach(2) {}' could be used - how to get "2"? Read requirements from OpenShift?
                            echo "Current status for containers (${it.names()}): ${it.objects()*.status}"
                            return checkContainerReadiness(it)
                        }
                        echo "Status of containers readiness after iteration ${iterationNumber}: ${podSelector.objects()*.status.containerStatuses*.ready}"
                        echo "End of CCR()"
                    }

                    echo "Final status of containers: ${podSelector.objects()*.status}"
                    assert checkContainerReadiness(podSelector), "All containers should be ready"
                }
            }
        }
    }
}

//https://jira.playmobile.pl/jira/browse/ITPBC-397 - "type:PodScheduled" matches .exists(), however, there are not containers and further assertion fail
private boolean isPodInScheduledPhase(def podSelector) {
    def podStatuses = podSelector.objects()*.status
    def isPodInScheduledPhaseVar = podStatuses.every { it.phase == "Pending" } && !podStatuses.every { it.conditions.isEmpty() && it.conditions[0].type == "PodScheduled" }
    println "isPodInScheduledPhaseVar: ${isPodInScheduledPhaseVar}; podSelector iPISP: ${podSelector}; ${podSelector.objects()*.status}"
    return isPodInScheduledPhaseVar
}

private boolean checkContainerReadiness(def podSelector) {
    println "readiness: ${podSelector.objects()*.status.containerStatuses*.ready}"    //concurrency issues between checks
    boolean areReady = podSelector.objects().every { pod ->
        pod.status.containerStatuses.every {
            it.ready == true
        }
    }
    println "areReady: ${areReady}, readiness: ${podSelector.objects()*.status.containerStatuses*.ready}, podSelector CCR: ${podSelector}"
//    println "podSelector CCR objects: ${podSelector.objects()}"

    //TODO: Extra (re-evaluated) debug statement to diagnose occasional timeouts - ITPBC-394 and ITPBC-426
    boolean areReadyToBeReturned = podSelector.objects().every { pod ->
        pod.status.containerStatuses.every {
            it.ready == true
        }
    }
    println "Returning areReady: ${areReadyToBeReturned}"
    return areReadyToBeReturned
}
