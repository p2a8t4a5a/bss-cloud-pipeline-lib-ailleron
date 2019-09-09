def call(body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    openshift.withCluster() {
        env.NAMESPACE = openshift.project()
        env.CICD_PROJ = openshift.project()
        env.SYS_CONFIG_REPO = "git@pbatman1.p4.int:root/${pipelineParams.sysName}-system-config.git"
        env.SYS_NAME = pipelineParams.sysName
        env.SYS_CONFIG_REPO_BRANCH = pipelineParams.configRepoBranch ? pipelineParams.configRepoBranch : 'master'
    }

    pipeline {
        agent any

        stages {

            stage('Fetch config') {
                steps {
                    deleteDir() /* clean up our workspace */
                    script {
                        utils.downloadConfigs('config-data', env.SYS_CONFIG_REPO, env.SYS_CONFIG_REPO_BRANCH)
                    }
                }
            }
            stage('Apply CICD config') {
                steps {
                    script {
                        currentBuild.description = "configure ${env.APP_NAME}"
                    }
                    echo "Applying CICD configuration for app ${env.APP_NAME}"
                    dir('config-data') {
                        deployCICD env.APP_NAME
                    }
                }
            }
            stage('Init preview') {
                steps {
                    script {
                        openshift.withCluster() {
                            openshift.withProject() {
                                String jobName = "${env.APP_NAME}-pipeline-preview"
                                def buildSelector = openshift.selector("build", [buildconfig: jobName])
                                int buildCount = buildSelector.count()
                                if (buildCount > 0) {
                                    print "Previous builds of '${jobName}' detected (${buildCount}). Skipping job triggering. \n" +
                                          "IMPORTANT. If triggers configuration has been changed in the meantime, re-run that job manually to apply changes."
                                } else {
                                    print "No previous builds detected. Triggering 'jobName'"
                                    openshift.startBuild(jobName)
                                }
                            }
                        }
                    }
                }
            }
            stage('WebHook') {
                steps {
                    script {
                        try {
                            // TODO - can we do it smarter? :)
                            dir("config-data/config/applications/${env.APP_NAME}") {
                                def props = readProperties file: 'cicd.params'
                                def repoName = props['APP_CODE_REPO_NAME']
                                env.GIT_PROJECT = repoName.subSequence(1, repoName.length() - 1).minus(".git")
                                print "Repo: ${env.GIT_PROJECT}"
                            }
                            def gitProjectName = java.net.URLEncoder.encode(env.GIT_PROJECT, "UTF-8")
                            def url = "http://10.10.99.86/api/v4/projects/${gitProjectName}/hooks?private_token=oQvZnuoMRkzyNWkcs4dy"
                            def response = httpRequest url: url
                            def r = utils.parseJson(response.content)
                            def hook = "${env.JENKINS_URL}project/${env.SYS_NAME}-cicd/${env.SYS_NAME}-cicd-${env.APP_NAME}-pipeline-preview"
                            def x = r.find { it.url == hook }
                            r = null
                            if (x != null) {
                                print "Hook already exists: ${x.url}"
                            } else {
                                def reqBody = '{"url":"' + hook + '", "enable_ssl_verification": false}'
                                print "Body: ${reqBody}"
                                httpRequest url: url, contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: reqBody
                            }

                            url = "http://10.10.99.86/api/v4/projects/${gitProjectName}/members?private_token=oQvZnuoMRkzyNWkcs4dy"
                            response = httpRequest url: url
                            r = utils.parseJson(response.content)
                            x = r.find { it.username == "openshift" }
                            r == null
                            if (x == null) {
                                def reqBody = '{"user_id": 253, "access_level":30}'
                                httpRequest url: url, contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: reqBody
                                print "User openshift(id=253) added to project ${gitProjectName}"
                            }
                        } catch (e) {
                            print e
                        }
                    }
                }
            }
        }
    }
}
