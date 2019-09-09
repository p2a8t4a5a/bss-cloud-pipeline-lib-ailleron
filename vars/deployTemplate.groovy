def call(String PROJ, String APP, String version = "latest") {

  script {
    def realPwd = sh returnStdout: true, script: 'pwd'
    utils.deployScripts(realPwd.trim(), "deploy-app.sh configure-project.sh")

    sh "library-scripts/deploy-app.sh ${PROJ} ${APP} ${version}"

    openshift.withCluster() {
      openshift.withProject("${PROJ}") {
        if (env.ENV_VARS != '') {
          print "Applying envs: ${env.ENV_VARS}"
          def envVarsTable = env.ENV_VARS.split(";")

          def dcObj = openshift.selector('dc', APP).object()
          print "Current envs: ${dcObj.spec.template.spec.containers[0].env}"
          envVarsTable.each {
            def tmp = it.split("=")
            if (dcObj.spec.template.spec.containers[0].env.find {it.name == tmp[0]} == null) {
                print "Adding: ENV ${tmp[0]}"
                openshift.set("env", "dc/${APP}", it)
            } else {
                print "Already exists: ENV ${tmp[0]}"
            }
          }
        }
      }
    }
  }
}
