package org.p4.ci.pipeline.logic.builder

import org.p4.ci.pipeline.util.PipelineConfigurer

import static org.p4.ci.pipeline.logic.wrapper.JUnitReportWrapper.withJUnitForMavenReportPublished

class MavenJavaBuilder extends AbstractJavaBuilder {

    MavenJavaBuilder(Script pipelineScript, PipelineConfigurer pipelineConfigurer) {
        super(pipelineScript, pipelineConfigurer)
    }

    @Override
    void prepare() {
        //TODO: e.g. Maven cache initialization
    }

    void buildCode(boolean failOnVersionMisconfiguration) {
        executeInScriptContext {
            buildUtils.writeJenkinsSettingsXmlInCurrentDirectory()
            setupVersioning()
            displayDebugJavaVersionInformation()

            sh "mvn clean verify -U -DskipTests=true -f ${env.POM_FILE} -s settings-jenkins.xml -P jenkins -Dsha1=${env.VERSION_CI_MODIFIER}"
        }
    }

    private void setupVersioning() {
        executeInScriptContext {
            boolean failOnVersionMisconfiguration
            def pomModel = readMavenPom file: "${env.MAIN_APP_DIR}${env.POM_FILE}"
            echo "Version: ${pomModel.version}"
            String revision = pomModel.properties['revision']
            String sha1 = pomModel.properties['sha1']
            String changelist = pomModel.properties['changelist']
            echo "revision: ${revision}"
            echo "sha1: ${pomModel.properties['sha1']}"
            echo "changelist: ${pomModel.properties['changelist']}"

            if (!revision) {

                if (failOnVersionMisconfiguration) {
                    String errorMessage = "Empty Maven 'revision' property detected. It should be defined in pom.xml. " +
                            "See: https://jira.playmobile.pl/confluence/display/ITPBC/Wersjonowanie+aplikacji "
                    println "ERROR. $errorMessage"
                    assert revision, errorMessage
                }

                println """
                    DEPRECATION WARNING. Empty Maven 'revision' property detected. It should be defined in pom.xml.
                    It will be forbidden in the foreseeable future.
                    More details: https://jira.playmobile.pl/confluence/display/ITPBC/Wersjonowanie+aplikacji
                    Base revision set to '0.0.1', changelist to '-SNAPSHOT'""".stripIndent()
                revision = '0.0.1'
                changelist = '-SNAPSHOT'
            }
            if (sha1) {
                println "Warning. Ignoring non-empty 'sha1' property defined in pom.xml: '${sha1}'"
            }
            //TODO: Tune behavior for master (release) branch
            sha1 = "-${env.BUILD_TIME}-${utils.sanitizeBranchName(env.APP_CODE_REPO_BRANCH)}"
            //TODO: Think about sense of keeping SNAPSHOT suffix while building from branch
            env.VERSION_CI_MODIFIER = sha1
            env.PIPELINE_VERSION = "${revision}${sha1}${changelist}"
            env.APP_VERSION = "${revision}"

            echo "VERSION_CI_MODIFIER: ${env.VERSION_CI_MODIFIER}"
        }
    }

    private void displayDebugJavaVersionInformation() {
        executeInScriptContext {
            sh """
                    ls -l /usr/lib/jvm/
                    java -version
                    echo \$JAVA_HOME
                    echo \$PATH
                """
        }
    }

    void testCode() {
        executeInScriptContext {
            withJUnitForMavenReportPublished(pipelineScript).wrap() {
                sh "mvn test -f ${env.POM_FILE} -s settings-jenkins.xml -P jenkins -Dsha1=${env.VERSION_CI_MODIFIER}"
            }
        }
    }

    void uploadStubs(def artifactoryServer) {
        executeInScriptContext {
            def pomModel = readMavenPom file: env.POM_FILE
            String artifactId = pomModel.artifactId
            String groupId = pomModel.groupId
            echo "Read project groupId: ${groupId}, artifactId: ${artifactId}"

            sh """
        cat target/maven-archiver/pom.properties
        mkdir target/generated-pom
        cp .flattened-pom.xml target/generated-pom/${artifactId}-${env.PIPELINE_VERSION}.pom
    """.stripIndent()

            String uploadRepo = getUploadRepoForVersion(env.PIPELINE_VERSION)
            String repoFolder = groupId.replace('.', '/').trim()
            String targetPath = "${uploadRepo}/${repoFolder}/${artifactId}/${env.PIPELINE_VERSION}/"
            println "TargetPath: ${targetPath}"
            String uploadSpec = """
        {
          "files": [
            {
              "pattern": "target/${artifactId}-${env.PIPELINE_VERSION}-stubs.jar",
              "target": "${targetPath}"
            },
            {
              "pattern": "target/generated-pom/${artifactId}-${env.PIPELINE_VERSION}.pom",
              "target": "${targetPath}"
            }
         ]
        }""".stripIndent()
            def buildInfo = artifactoryServer.upload(uploadSpec)
            println "BuildInfo: ${buildInfo.toString()}"
        }
    }

    void executeSonar() {
        executeInScriptContext {
            scriptUtils.mayFail {
                withSonarQubeEnv('SonarQube') {
                    sh "mvn sonar:sonar -f ${env.POM_FILE} -s settings-jenkins.xml -P jenkins -Dsha1=${env.VERSION_CI_MODIFIER} " +
                            "-Dsonar.projectName=${env.SYS_NAME}-${env.APP_NAME}"
//                    //Disabled as user openshift doesn't have required permissions to set tags - project name is overridden instead
//                    sonarUtils.tagAplicationWithProjectTagInSonar("${env.SYS_NAME}-${env.APP_NAME}", "${env.SYS_NAME}")
                }
            }
        }
    }
}
