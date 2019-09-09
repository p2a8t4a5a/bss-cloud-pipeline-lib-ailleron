def call(String sourceStage, String destStage, String version = "latest") {

    script {
       openshift.withCluster() {
          echo "Setting tag: ${sourceStage}/${env.APP_NAME}:${version} ->  ${destStage}/${env.APP_NAME}:${version}"
          openshift.tag("${sourceStage}/${env.APP_NAME}:${version}", "${destStage}/${env.APP_NAME}:${version}", "--reference-policy=local")

          echo "Setting tag: ${sourceStage}/${env.APP_NAME}:${version} ->  ${destStage}/${env.APP_NAME}:latest"
          openshift.tag("${sourceStage}/${env.APP_NAME}:${version}", "${destStage}/${env.APP_NAME}:latest", "--reference-policy=local")
       }
    }
}
