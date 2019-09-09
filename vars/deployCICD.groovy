def call(String APP) {

  script {
    def realPwd = sh returnStdout: true, script: 'pwd'
    utils.deployScripts(realPwd.trim(), "deploy-cicd.sh")

    sh "library-scripts/deploy-cicd.sh ${APP}"
  }
}
