def call(body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    openshift.withCluster() {
      env.NAMESPACE = openshift.project()
      env.SYS_NAME = pipelineParams.sysName
    }

    pipeline {
      agent any

      stages {

        stage('Fetch config') {
            steps {
                deleteDir() /* clean up our workspace */
                script {
                    utils.downloadConfigs('config-data', "git@pbatman1.p4.int:root/${SYS_NAME}-system-config.git")
                }
            }
        }

        stage('Check project'){
          steps {

            script {
              openshift.withCluster() {
                openshift.withProject("${env.PROJ}") {
                  def result = openshift.selector("secret").exists()
                  if (!result) {
                    error "Project ${env.PROJ} doesn't exist or is misconfigured"
                  }
                  echo "Result=${result}"
                }
              }
            }
          }
        }

        stage('Apply config'){
          steps {
            script {
                // currentBuild.displayName = "configure ${env.PROJ}"
                currentBuild.description = "${env.PROJ}"
            }
            echo "Configuring ${env.PROJ}"
            dir('config-data') {
                applyConfig env.PROJ
            }
          }
        }

      }
    }
}
