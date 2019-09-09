import info.solidsoft.jenkins.powerstage.PowerStageOrchestrator
import org.p4.ci.pipeline.DeployMode
import org.p4.ci.pipeline.logic.model.BuildTool
import org.p4.ci.pipeline.logic.builder.CodeBuilder
import org.p4.ci.pipeline.logic.builder.GradleJavaBuilder
import org.p4.ci.pipeline.logic.builder.MavenJavaBuilder
import org.p4.ci.pipeline.logic.builder.UnsupportedCodeBuilder
import org.p4.ci.pipeline.logic.model.SonarMode
import org.p4.ci.pipeline.notification.NotificationWrapper
import org.p4.ci.pipeline.util.PipelineConfigurer
import org.p4.ci.pipeline.util.logging.Logger
import org.p4.ci.pipeline.util.logging.LogLevel

import static info.solidsoft.jenkins.powerstage.PowerStageOrchestrator.createGlobalConfig
import static info.solidsoft.jenkins.powerstage.PowerStageOrchestrator.stageOrchestrator
import static org.p4.ci.pipeline.logic.wrapper.ResourceCleanWrapper.executeWithResourceCleaning
import static org.p4.ci.pipeline.notification.NotificationWrapper.emailNotificationWrapper
import static org.p4.ci.pipeline.util.PipelineUtils.isJavaApplicationType

def void configureBranches(boolean preview, Map pipelineParams, Logger log){
    // this function configure branches for code, default configuration and system configuration repositories
    def sys_repo_branch = 'master'
    if (preview){
        log.debug "Configuring branches for preview mode, founded variables: gitlab-> ${env.gitlabBranch}, env-> ${env.branch}"
        def branch = env.gitlabBranch ? env.gitlabBranch : env.branch
        if (branch == null) {
            log.info "Empty branch value, configuring to `dev`"
            branch = "dev"
        }
        env.APP_CODE_REPO_BRANCH = branch
        sys_repo_branch = 'preview'
        
    }else{
        env.APP_CODE_REPO_BRANCH = pipelineParams.appRepoBranch ? pipelineParams.appRepoBranch : 'master'    
    }
    log.info "Application branch configured to: ${env.APP_CODE_REPO_BRANCH}"
    env.DEFAULT_SYS_CONFIG_REPO_BRANCH = pipelineParams.defaultConfigBranch ? pipelineParams.defaultConfigBranch : 'master'
    log.info "Default System configuration branch configured to: ${env.DEFAULT_SYS_CONFIG_REPO_BRANCH}"
    if (pipelineParams.configRepoBranch){
        // todo: checkout if someone is using configRepoBranch in their configuration // possibly not as supported just manually
        // send mail to me about this fact ;)
        sys_repo_branch = pipelineParams.configRepoBranch
        log.warn "parameter configRepoBranch used here!"
        utils.send_email("marcin.karkocha@mindboxgroup.com", 
                            "Usage of configRepoBranch parameter", 
                            "Parameter configRepoBranch used in project: ${env.JOB_NAME} with value: ${pipelineParams.confiRepoBranch}")
    }
    env.SYS_CONFIG_REPO_BRANCH = pipelineParams.sysConfigBranch ? pipelineParams.sysConfigBranch : sys_repo_branch
    log.info "System configuration branch configured to: ${env.SYS_CONFIG_REPO_BRANCH}"
    log.info "Branch configuration finished!"
}

// main function
def call(boolean previewMode = false, Closure body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    Logger.init(this, [ logLevel: LogLevel.INFO ])
    Logger log = new Logger(this)

    log.info "Initializing new build ...."
    log.info "Resolved configuration: ${pipelineParams}"
    if (previewMode){
        log.info "Build in Preview Mode"
    }

    timestamps {    //TODO: commonWrappers {}
    ansiColor('xterm') {

    String deploymentNotificationEmails = pipelineParams.deploymentNotificationEmails
    NotificationWrapper notificationWrapper = emailNotificationWrapper(this, deploymentNotificationEmails)
    notificationWrapper.withNotification {

        List gitlabTriggerPropertiesList = []

            //TODO: Move to preparation stage
        openshift.withCluster() {
            env.NAMESPACE = openshift.project()
        }

        env.MAIN_APP_DIR = pipelineParams.mainAppDir ? pipelineParams.mainAppDir : ""
        env.POM_FILE = env.BUILD_CONTEXT_DIR ? "${env.BUILD_CONTEXT_DIR}/pom.xml" : "pom.xml"
        env.APP_NAME = pipelineParams.appName ? pipelineParams.appName : "${env.JOB_NAME}".replaceAll(/-?pipeline-?/, '').replaceAll(/-?${env.NAMESPACE}-?/, '').replaceAll("/", '')
                .replaceAll('test(\\d?)-', '').replaceAll('-test(\\d?)', '')    //TODO: Temporary hack to reuse the same application in test pipeline
        def sysName = pipelineParams.sysName
        env.SYS_NAME = sysName
        env.APP_REPO = pipelineParams.appRepo
        // by convention config repo is sys_name-system-config
        env.SYS_CONFIG_REPO = "git@pbatman1.p4.int:root/${sysName}-system-config.git"
        env.BUILD_TIME=utils.getBuildTimeAsString()
        env.PIPELINE_TEST_RESOURCES_BASE_NAME = (env.JOB_NAME - "${env.NAMESPACE}-").split("/")[-1] + "-${env.BUILD_NUMBER}"
        env.GIT_REPO_VOLUME = pipelineParams.gitRepoVolume ?: ''
        env.ENV_VARS = pipelineParams.envVars ?: ''
        echo "PIPELINE_TEST_RESOURCES_BASE_NAME: $PIPELINE_TEST_RESOURCES_BASE_NAME"

        env.EMAIL_NOFICATIONS_DISABLED = env.SYS_CONFIG_REPO_BRANCH?.startsWith("devel") ? "true" : "false" //TODO: broken - shared lib branch is not exposed that way :)
        configureBranches(previewMode, pipelineParams as Map, log)

        if (previewMode) {
            println "Pipeline started by ${env.gitlabUserName} ${env.gitlabUserEmail}: repo->${env.gitlabTargetRepoHttpUrl}, branch->${env.gitlabBranch}"
            deploymentNotificationEmails += ",${env.gitlabUserEmail}"
            
            env.FILTER_BRANCH_NAME = pipelineParams.appRepoBranch ? pipelineParams.appRepoBranch : 'master'
            if (env.FILTER_BRANCH_NAME != 'master')
                env.FILTER_BRANCH_NAME += ',master'
            env.FILTER_BRANCH_NAME += ",test,stage,prod"

            def branchPrefix = pipelineParams.appRepoBranchPrefix ? pipelineParams.appRepoBranchPrefix : ''
            if (!branchPrefix.isEmpty())
                branchPrefix = "${branchPrefix}-"

            env.STAGE0 = "${env.NAMESPACE}"
            env.STAGE1 = "${sysName}-${env.APP_CODE_REPO_BRANCH.minus(branchPrefix)}"
            env.STAGE2 = "invalid"
            env.STAGE3 = "invalid"

            gitlabTriggerPropertiesList = [
                    /*gitLabConnection('your-gitlab-connection-name'),*/
                    pipelineTriggers([
                            [
                                    $class                        : 'GitLabPushTrigger',
                                    triggerOnPush                 : true,
                                    triggerOnMergeRequest         : false,
                                    triggerOpenMergeRequestOnPush : "never",
                                    triggerOnNoteRequest          : true,
                                    noteRegex                     : "Jenkins please retry a build",
                                    skipWorkInProgressMergeRequest: false,
//                                    secretToken                   : project_token,
                                    ciSkip                        : true,
                                    setBuildDescription           : true,
                                    addNoteOnMergeRequest         : true,
                                    addCiMessage                  : true,
                                    addVoteOnMergeRequest         : true,
                                    acceptMergeRequestOnSuccess   : false,
                                    branchFilterType              : "NameBasedFilter",
                                    includeBranchesSpec           : "",
                                    excludeBranchesSpec           : env.FILTER_BRANCH_NAME,
                            ]
                    ])
            ]

        } else {

            def projPrefix = "${env.NAMESPACE}".replaceAll("-cicd","")
            env.STAGE0 = "${env.NAMESPACE}"
            env.STAGE1 = "${projPrefix}-test"
            env.STAGE2 = "${projPrefix}-stage"
            env.STAGE3 = "${projPrefix}-prod"

            env.JIRA_API_URL = "http://10.10.101.148:8080/jira/rest/releaserestresource/1.0/message"
            env.JIRA_PROJECT_KEY = pipelineParams.jiraProjectKey ? pipelineParams.jiraProjectKey : "ITA${env.SYS_NAME.toUpperCase()}"

            // ITPBC-535
            env.INFLUX_NOTIFICATION_DB = pipelineParams.influxNotificationDb ? pipelineParams.influxNotificationDb : env.SYS_NAME

            def runningMode = pipelineParams.runningMode ?: 'AUTO'
            if (runningMode == 'AUTO') {
                gitlabTriggerPropertiesList = [
                        /*gitLabConnection('your-gitlab-connection-name'),*/
                        pipelineTriggers([
                                [
                                        $class                        : 'GitLabPushTrigger',
                                        triggerOnPush                 : true,
                                        triggerOnMergeRequest         : false,
                                        triggerOpenMergeRequestOnPush : "never",
                                        triggerOnNoteRequest          : true,
                                        noteRegex                     : "Jenkins please retry a build",
                                        skipWorkInProgressMergeRequest: false,
//                                    secretToken                   : project_token,
                                        ciSkip                        : true,
                                        setBuildDescription           : true,
                                        addNoteOnMergeRequest         : true,
                                        addCiMessage                  : true,
                                        addVoteOnMergeRequest         : true,
                                        acceptMergeRequestOnSuccess   : false,
                                        branchFilterType              : "NameBasedFilter",
                                        includeBranchesSpec           : env.APP_CODE_REPO_BRANCH,
                                        excludeBranchesSpec           : ""
                                ]
                        ])
                ]
            }

        }

        println "Pipeline order: ${env.STAGE0}(build) => ${env.STAGE1} => ${env.STAGE2} => ${env.STAGE3}"

        // manage different type of apps
        env.APP_TYPE = pipelineParams.appType ?: 'springboot'
        //TODO: Replace with more structuralized form
        //TODO: BuildTools should be defined in build template
        def appsParamsMap = [
                springboot: [
                        buildSlave: 'maven35-java11',
                        buildTool: BuildTool.JDK8
                ],
                springbootJava11: [
                        buildSlave: 'maven35-java11',
                        buildTool: BuildTool.OPENJDK11
                ],
                springbootGradle: [
                        buildSlave: 'maven35-java11',
                        buildTool: BuildTool.JDK8
                ],
                springbootJava11Gradle: [
                        buildSlave: 'maven35-java11',
                        buildTool: BuildTool.OPENJDK11
                ],
                java11JEE: [
                    buildSlave: 'maven35-java11',
                    buildTool: BuildTool.OPENJDK11
                ],
                nodejs: [
                        buildSlave: 'nodejs8'
                ],
                nodejsServer: [
                        buildSlave: 'nodejs8'
                ]

        ]
        String buildSlaveLabel = appsParamsMap[env.APP_TYPE]['buildSlave']
        //TODO: Extend to List<BuildTool> to support multiple tools in nested way
        BuildTool buildTool = appsParamsMap[env.APP_TYPE]['buildTool'] ?: BuildTool.DUMMY   //TODO: Add it to PipelineConfigurer?

        def artifactoryServer = Artifactory.server 'artifactory'

        boolean stubsUpload = pipelineParams.stubsUpload == true
        boolean failOnVersionMisconfiguration = true    //Remove "feature toggle" once stable: https://jira.playmobile.pl/jira/browse/ITPBC-326
        SonarMode sonarMode = pipelineParams.sonarMode ?:
                (pipelineParams.sonarEnabled == true ? SonarMode.PERMISSIVE :   //TODO: Remove support for deprecated syntax
                SonarMode.DISABLED)    //TODO: Switch to PERMISSIVE by default - https://jira.playmobile.pl/jira/browse/ITPBC-508

        println "sonarMode : ${sonarMode.toString()} (pipelineParams.sonarMode: ${pipelineParams.sonarMode?.toString()}, " +
                "pipelineParams.sonarEnabled: ${pipelineParams.sonarEnabled})"

        //TODO: Move here also artifactoryServer?
        PipelineConfigurer pipelineConfigurer = new PipelineConfigurer(this).initialize(pipelineParams)
        //Please note. toString() on objects with custom toString() method is required. Otherwise logged statement is empty...
        echo "Configuration provided by PipelineConfigurer: ${pipelineConfigurer.toString()}"

        properties(gitlabTriggerPropertiesList)

        //TODO: Provide output of all resolved configuration parameters (not just passed pipelineParams

        PowerStageOrchestrator stageOrchestrator = stageOrchestrator(this, createGlobalConfig {
            maximumNumberOfAttempts = 2
        })

        //TODO: Delegate somewhere
        CodeBuilder codeBuilder = isJavaApplicationType(env.APP_TYPE) ?
                (env.APP_TYPE?.toLowerCase()?.contains("gradle") ?
                        new GradleJavaBuilder(this, pipelineConfigurer) :
                        new MavenJavaBuilder(this, pipelineConfigurer))
                : new UnsupportedCodeBuilder()

        //TODO: Consider a dedicated type of stage in stageOrchestrator with no lock/timeout/... Somehow problematic in implementation
        stage('Preparation') {  //artificial informational stage

            //TODO: Somehow problematic - lock should not be with pipeline keeping a pod
            node(buildSlaveLabel) {

                stageOrchestrator.simpleStage('Validate') {
                    openshift.withCluster() {
                        if (previewMode) {
                            def projectName = "${env.STAGE1}"
                            def projects = openshift.selector('project').names()
                            print "Projects: ${projects}"
                            def found = projects.find { it == "project/${projectName}" }
                            if (found == null) {
                                env.EMAIL_NOFICATIONS_DISABLED = true
                                currentBuild.result = 'ABORTED'
                                currentBuild.displayName = "#${env.BUILD_NUMBER} skipped"
                                currentBuild.description = "Branch ${env.gitlabBranch} without OpenShift project"
                            }
                            assert found, "No project ${projectName} found on OpenShift. Skipping"
                        } else {
                            print "Owners: ${pipelineParams.owners}"
                            env.owners = pipelineParams.owners
                            print "Description: ${pipelineParams.description}"
                            env.description = pipelineParams.description
                            print "Metrics: ${pipelineParams.metricsUrl}"
                            env.metricsUrl = pipelineParams.metricsUrl
                            print "Logs: ${pipelineParams.logsUrl}"
                            env.logsUrl = pipelineParams.logsUrl
                            print "IsProd?: ${pipelineParams.productionUse}"
                            env.productionUse = pipelineParams.productionUse
                        }
                    }
                }

                stageOrchestrator.simpleStage('Git Checkout') {
                    deleteDir() /* clean up our workspace */
                    git url: "${env.APP_REPO}", credentialsId: "${env.NAMESPACE}-gitlab-ssh-secret", branch: env.APP_CODE_REPO_BRANCH
                    script {
                        utils.downloadConfigs('config-data', env.SYS_CONFIG_REPO, env.SYS_CONFIG_REPO_BRANCH, env.DEFAULT_SYS_CONFIG_REPO_BRANCH)
                        gitCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        env.GIT_COMMIT_ID = gitCommit
                    }
                    stash includes: 'config-data/**/*', name: 'config-data'
                }

                codeBuilder.inBuildContext(buildTool) {
                    executeWithResourceCleaning(this) {
                        when(testResourcesUtils.isTestResourcesConfigured(env.APP_NAME)) {
                            stageOrchestrator.simpleStage('Prepare test resources') {
                                codeBuilder.prepareTestResources()
                            }
                        }

                        stageOrchestrator.simpleStage('Build App') {

                            codeBuilder.prepare()

                            if (isJavaApplicationType(env.APP_TYPE)) {
                                codeBuilder.buildCode(failOnVersionMisconfiguration)
                            } else {
                                //Legacy mode for non Java apps - just for now
                                buildUtils.buildApp(env.APP_TYPE, pipelineConfigurer.cacheWrapper, failOnVersionMisconfiguration)
                            }
                        }

                        if (isJavaApplicationType(env.APP_TYPE)) {  //onlyIf(isJavaApplicationType(env.APP_TYPE)) {} ?
                            // Run unit & integration tests
                            stageOrchestrator.simpleStage('Unit & integration testing') {
                                try {
                                    codeBuilder.testCode()
                                } catch (e) {
                                    print "No tests - continue"
                                }
                            }
                        }

                        if (!previewMode) {
                            if (isJavaApplicationType(env.APP_TYPE) && stubsUpload) {
                                stageOrchestrator.simpleStage('Deploy stubs') {
                                    codeBuilder.uploadStubs(artifactoryServer)
                                }
                            }
                        }

                        //TODO: PoC - rework configuration to support different mode with and without permissive quality check and preview mode in preview pipeline
                        if (isJavaApplicationType(env.APP_TYPE) && sonarMode.enabled && !previewMode) {
                            //TODO: Stage could be always created, but skipped if disabled - https://comquent.de/en/skipped-stages-in-jenkins-scripted-pipeline/
                            stageOrchestrator.simpleStage("Sonar analysis") {
                                codeBuilder.executeSonar()
                            }
                            //TODO: Add (optional) quality check after deploy to test
                        }
                    }.finalizedBy {
                        //Problematic to put into stage due to milestone numbering issues and visualisation glitches (flickering)
                        if (testResourcesUtils.isTestResourcesConfigured(env.APP_NAME)) {
                            codeBuilder.cleanupTestResources()
                        }
                    }

                }

                if (!previewMode) {
                    // Get RM id from Jira API
                    stageOrchestrator.simpleStage('Find Jira Release') {
                        jiraRelease()
                    }
                }

                // Build Container Image using the artifacts produced in previous stages
                stageOrchestrator.simpleStage('Build Container Image') {
                    buildUtils.buildImage(env.APP_TYPE)
                }
            }   //dedicated executor node

            deployUtils.promoteAndDeploy(stageOrchestrator, env.STAGE0, env.STAGE1, env.PIPELINE_VERSION, deploymentNotificationEmails)

            if (!previewMode) {
                if (isJavaApplicationType(env.APP_TYPE) && sonarMode.enabled) {
                    stageOrchestrator.customStage("Wait for Quality Gate", stageOrchestrator.createConfig {
                        maximumNumberOfAttempts = 1
//                        timeoutInMinutes(1) //TODO: Implement that in PowerStage
                    }) {
                        when(sonarMode.qualityGateEnabled) {
                            codeBuilder.waitForQualityGateResult(sonarMode)
                        }
                    }
                }

                deployUtils.promoteAndDeployWithJiraNotification(stageOrchestrator, env.STAGE1, env.STAGE2, env.PIPELINE_VERSION, deploymentNotificationEmails)
                deployUtils.promoteAndDeployWithJiraNotification(stageOrchestrator, env.STAGE2, env.STAGE3, env.PIPELINE_VERSION, deploymentNotificationEmails, DeployMode.MANUAL, true)
            } else {
                currentBuild.description = "${env.PIPELINE_VERSION} : ${env.GIT_COMMIT_ID}"
            }
        }

    }
    }
    }
}
