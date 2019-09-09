def call(String PROJ) {

  script {
    echo "> Applying configuration"

    def realPwd = sh returnStdout: true, script: 'pwd'
    utils.deployScripts(realPwd.trim(), "configure-project.sh")
    sh "sh library-scripts/configure-project.sh ${PROJ}"
  }
}
