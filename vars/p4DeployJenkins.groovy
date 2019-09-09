def call(body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    openshift.withCluster() {
        env.NAMESPACE = openshift.project()
        env.SYS_CONFIG_REPO = "git@pbatman1.p4.int:root/${env.SYS_NAME}-system-config.git"
        env.PROJ_SUFFIX = env.PROJ_SUFFIX ? env.PROJ_SUFFIX : ""
        env.CICD_PROJ_NAME= env.PROJ_SUFFIX ? "${env.SYS_NAME}-${env.PROJ_SUFFIX}-cicd" : "${env.SYS_NAME}-cicd"
    }

    pipeline {
        agent any

        stages {

            stage('Fetch config') {
                steps {
                    deleteDir() /* clean up our workspace */
                    script {
                        utils.downloadConfigs('config-data', env.SYS_CONFIG_REPO)
                    }
                }
            }
            stage('Deploy Jenkins') {
                steps {
                    echo "Deploying Jenkins for system ${env.SYS_NAME}"
                    dir('config-data') {
                        deployJenkins env.SYS_NAME, env.PROJ_SUFFIX
                    }
                    script {
                        print "Loading configuration:"
                        currentBuild.description = "${env.SYS_NAME} (${env.PROJ_SUFFIX})"
                        openshift.withCluster() {
                            openshift.withProject("${env.CICD_PROJ_NAME}") {
                                def files = findFiles glob: 'config-data/config/profiles/cicd/*.yaml'
                                print "${files.size()} configuration files found!"
                                files.each {
                                    print "Applying config file: ${it.path}"
                                    def response = openshift.apply(readFile(it.path))
                                    print "Project ${env.CICD_PROJ_NAME}: created objects ${response.names()}"
                                }
                            }
                        }
                    }
                }
            }
            stage('Init config pipeline') {
                steps {
                    script {
                        openshift.withCluster() {
                            openshift.withProject("${env.CICD_PROJ_NAME}") {
                                openshift.startBuild("${env.SYS_NAME}-pipeline-config-preview")
                            }
                        }
                    }
                }
            }
            stage('Webhook') {
                steps {
                    script {
                        def systemProjectName = "root/${env.SYS_NAME}-system-config"
                        def gitProjectName = java.net.URLEncoder.encode(systemProjectName, "UTF-8")
                        def url = "http://10.10.99.86/api/v4/projects/${gitProjectName}/hooks?private_token=oQvZnuoMRkzyNWkcs4dy"
                        def response = httpRequest url: url
                        def r = utils.parseJson(response.content)
                        def hook = "https://jenkins-${env.SYS_NAME}-cicd.apps.bsscloud.p4.int/project/${env.SYS_NAME}-cicd/${env.SYS_NAME}-cicd-${env.SYS_NAME}-pipeline-config-preview"
                        def x = r.find { it.url == hook }
                        r = null
                        if (x != null) {
                            print "Hook already exists: ${x.url}"
                        } else {
                            def reqBody = '{"url":"' + hook + '", "enable_ssl_verification": false}'
                            httpRequest url: url, contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: reqBody.toString()
                        }
                    }
                }
            }
        }
    }
}
