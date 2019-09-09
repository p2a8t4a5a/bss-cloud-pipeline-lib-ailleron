def call(body) {

    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    openshift.withCluster() {
        env.NAMESPACE = openshift.project()
        def sysName = pipelineParams.sysName
        env.SYS_NAME = sysName
        env.SYS_CONFIG_REPO = "git@pbatman1.p4.int:root/${sysName}-system-config.git"
    }

    pipeline {
        agent any
        options {
            ansiColor('xterm')
            timestamps()
        }
        parameters {
            string(name: 'BRANCH', defaultValue: 'dev', description: 'Wybierz branch dla środowiska')
        }

        stages {
            stage('Checkout') {
                steps {
                    deleteDir()
                    git url: "${env.SYS_CONFIG_REPO}", credentialsId: "${env.NAMESPACE}-gitlab-ssh-secret", branch: "preview"
                }
            }
            stage('Start Jobs') {
                steps {
                    script {
                        def files = findFiles glob: 'config/applications/**/cicd.params'
                        files.each {
                            print "Processing file: ${it.path}"
                            def props = readProperties file: it.path
                            def appName = props['APP_NAME'].subSequence(1, props['APP_NAME'].length() - 1)
                            try {
                                build job: "${env.NAMESPACE}-${appName}-pipeline-preview", parameters: [string(name: 'branch', value: "${params.BRANCH}")], wait: false
                                //print "Pipeline started: ${appName}-pipeline-preview-test"
                            } catch(e) {

                            }
                        }
                    }
                }
            }
        }
    }

}