import org.p4.ci.pipeline.PipelineConstants

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
        triggers {
            gitlab(triggerOnPush: true, triggerOnMergeRequest: false, branchFilterType: 'NameBasedFilter', includeBranchesSpec: 'preview')
        }
        stages {
            stage('Checkout') {
                steps {
                    deleteDir()
                    git url: "${env.SYS_CONFIG_REPO}", credentialsId: "${env.NAMESPACE}-gitlab-ssh-secret", branch: "preview"
                }
            }
            stage('Apply Templates') {
                steps {
                    script {
                        def executions = [:]
                        def allowedProjects = buildUtils.getPreviewProjects(env.SYS_NAME)
                        print "Allowed projects: $allowedProjects"
                        dir("config/system/${env.SYS_NAME}") {
                            def files = findFiles glob: '*/**/*.yaml'
                            for (int i = 0; i < files.size(); ++i) {
                                def project = "${files[i].path}".tokenize('/')[0]
                                if (allowedProjects.contains(project)) {
                                    utils.addListElementToMap(executions, project, "${files[i].path}")
                                }
                            }

                            def defaultFiles = findFiles glob: "${env.SYS_NAME}-test/**/*.yaml"
                            for (int i = 0; i < allowedProjects.size(); i++) {
                                files = findFiles glob: "${allowedProjects[i]}/**/*.yaml"
                                if (files.size() == 0) {
                                    for (int j = 0; j < defaultFiles.size(); j++) {
                                        addFileToProjectsMap(executions, allowedProjects[i], "${defaultFiles[j].path}")
                                    }
                                }
                            }

                            files = findFiles glob: '*.yaml'
                            for (int i = 0; i < files.size(); ++i) {
                                def projects = executions.keySet()
                                projects.each {
                                    utils.addListElementToMap(executions, it, "${files[i].path}")
                                }
                            }

                            buildUtils.createResources(executions)
                        }

                        dir('config/applications') {
                            def files = findFiles glob: '*/*.yaml'
                            def globalExecutions = [:]
                            for (int i = 0; i < files.size(); ++i) {
                                def projects = executions.keySet()
                                projects.each {
                                    def filePath = "${it}/${files[i].path}"
                                    if (!executions[it].contains(filePath)) {
                                        utils.addListElementToMap(globalExecutions, it, files[i].path)
                                    }
                                }
                            }
                            buildUtils.createResources(globalExecutions)
                        }
                    }
                }
            }
        }
    }
}
