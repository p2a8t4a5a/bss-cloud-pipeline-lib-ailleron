def call(String SYS, String PROJ_SUFFIX) {

  script {
    def realPwd = sh returnStdout: true, script: 'pwd'
    utils.deployScripts(realPwd.trim(), "deploy-jenkins.sh configure-project.sh")

    sh "library-scripts/deploy-jenkins.sh ${SYS} ${PROJ_SUFFIX}"
  }
}
