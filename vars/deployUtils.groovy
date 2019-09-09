import info.solidsoft.jenkins.powerstage.PowerStageOrchestrator
import info.solidsoft.jenkins.powerstage.stage.StageExecutionMode

import org.p4.ci.pipeline.DeployMode

def promoteAndDeployWithJiraNotification(PowerStageOrchestrator stageOrchestrator, String stageFrom, String stageTo, String pipelineVersion, String deploymentNotificationEmails, DeployMode deployMode = DeployMode.AUTO, boolean influxNotificationEnabled = false) {
    promoteAndDeploy(stageOrchestrator, stageFrom, stageTo, pipelineVersion, deploymentNotificationEmails, deployMode)

    stageOrchestrator.simpleStage("Jira ${stageTo} Notification") {
        jiraNotification("Deployed on ${stageTo}")  //TODO: extractShortStageName(stageTo)
    }

    if (influxNotificationEnabled) {
        stageOrchestrator.simpleStage("Influx Notification") {
            influxNotification(stageTo)
        }
    }
}

def promoteAndDeploy(PowerStageOrchestrator stageOrchestrator, String stageFrom, String stageTo, String pipelineVersion, String deploymentNotificationEmails, DeployMode deployMode = DeployMode.AUTO) {

    String stageToShortName = "${extractShortStageName(stageTo).capitalize()}"

    //TODO: deployStage()?
    stageOrchestrator.customStage("Deploy to ${stageToShortName}", stageOrchestrator.createConfig {
        stageExecutionMode = (deployMode == DeployMode.MANUAL ? StageExecutionMode.MANUAL : StageExecutionMode.AUTO)
    }) {
        node("") {  //TODO: It is worth to put node inside for those steps?
            //promotion
            utils.prepConfig()
            dir('config-data') {
                deployTemplate(stageTo, env.APP_NAME, pipelineVersion)
            }
            promoteVersion(stageFrom, stageTo, pipelineVersion)

            //deployment verification
            verifyDeployment(stageTo)
            utils.notifyAboutDeploymentViaEmail(env.APP_NAME, pipelineVersion, stageTo, env.BUILD_URL, deploymentNotificationEmails)
        }
    }
}

private String extractShortStageName(String fullStageName) {
    List<String> tokenizedStageName = fullStageName.tokenize('-')
    return tokenizedStageName.size() == 2 ? tokenizedStageName[1] : fullStageName
}
