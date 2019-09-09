def call(String PROJ, String APP_NAME) {

  def template_params = ["--param-file=config/_default/project.params","--param=PROJECT=${PROJ}"]
  def template_file = "templates/${APP_NAME}-template.yaml"

  script {
    def proj_param_file = "config/${PROJ}/project.params"
    def file_exists = sh script: "test -f ${proj_param_file}", returnStatus: true
    if (file_exists == 0) {
      template_params.add("--param-file=${proj_param_file}")
      echo "Extra params added: ${proj_param_file}"
      } else {
        echo "No extra params found for project ${PROJ}"
      }

      openshift.withCluster() {
        openshift.withProject("${PROJ}") {
          print "Applying template ${template_file}"
          def objects = openshift.process(readFile(file:template_file), template_params)
          for (int i = 0; i < objects.size(); i++) {
            print "> " + objects[i].kind + ":" + objects[i].metadata['name']
            openshift.apply(objects[i])
          }
        }
      }
    }
  }
