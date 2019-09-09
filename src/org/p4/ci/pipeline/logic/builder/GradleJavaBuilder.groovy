package org.p4.ci.pipeline.logic.builder

import org.p4.ci.pipeline.util.PipelineConfigurer

import static org.p4.ci.pipeline.logic.wrapper.JUnitReportWrapper.withJUnitForGradleReportPublished

class GradleJavaBuilder extends AbstractJavaBuilder {

    GradleJavaBuilder(Script pipelineScript, PipelineConfigurer pipelineConfigurer) {
        super(pipelineScript, pipelineConfigurer)
    }

    @Override
    void prepare() {
        executeInScriptContext {
            writeJenkinsGradleInitScriptWithArtifactoryAuthenticationInJenkinsHomeDirectory()
        }
    }

    @Override
    void buildCode(boolean failOnVersionMisconfiguration) {
        executeInScriptContext {
            //TODO: Here or in "prepare"? However, prepare can be called multiple times (on different nodes)
            setupVersioning()

            sh "./gradlew build -PversionCiModifier='${env.VERSION_CI_MODIFIER}'"
        }
    }

    private void setupVersioning() {
        executeInScriptContext {
            Properties props = readProperties file: 'gradle.properties'
            echo "Read Gradle project properties: ${props}"

            String versionBase = props['versionBase']
            String versionCiModifier = props['versionCiModifier']
            String versionSuffix = props['versionSuffix']
            echo "Read from repo file: versionBase: ${versionBase}, versionCiModifier: ${versionCiModifier}, versionSuffix: ${versionSuffix}"

            if (!versionBase) {
                String errorMessage = "Empty Gradle 'versionBase' property detected. It should be defined in gradle.properties. " +
                        "See: https://jira.playmobile.pl/confluence/display/ITPBC/Wersjonowanie+aplikacji "
                println "ERROR. $errorMessage"
                assert versionBase, errorMessage
            }
            if (versionCiModifier) {
                println "Warning. Ignoring non-empty 'versionCiModifier' property defined in gradle.properties: '${versionCiModifier}'"
            }
            versionCiModifier = "-${env.BUILD_TIME}-${utils.sanitizeBranchName(env.APP_CODE_REPO_BRANCH)}"

            env.PIPELINE_VERSION = "${versionBase}${versionCiModifier}${versionSuffix}"
            env.APP_VERSION = "${versionBase}" //TODO: Why do we need it in fact?
            env.VERSION_CI_MODIFIER = versionCiModifier
            echo "PIPELINE_VERSION: ${env.PIPELINE_VERSION}, VERSION_CI_MODIFIER: ${env.VERSION_CI_MODIFIER}, APP_VERSION: ${env.APP_VERSION}"
        }
    }

    @Override
    void testCode() {
        executeInScriptContext {
            withJUnitForGradleReportPublished(pipelineScript).wrap() {
                echo "INFO: Gathering test results. Test execution already performed in build step."
            }
        }
    }

    @Override
    void uploadStubs(Object artifactoryServer) {
        executeInScriptContext {
            sh "./gradlew generatePomFileForStubsPublication -PversionCiModifier='${env.VERSION_CI_MODIFIER}'"   //generate pom.xml in build/publications/stubs/pom-default.xml
            //TODO: It can be read from generate pom :)
            //TODO: Even better, Gradle should upload stubs wuth "publishStubsPublicationToArtifactory"
            sh "./gradlew properties -PversionCiModifier='${env.VERSION_CI_MODIFIER}' | grep -e '^version:' -e '^group:' -e '^archivesBaseName:' | sed 's/: /=/g' > build/buildProperties"
            Properties props = readProperties file: 'build/buildProperties'
            String groupId = props.get("group")
            String artifactId = props.get("archivesBaseName")
            String projectVersion = props.get("version")
            echo "Read project properties: $groupId, $artifactId, $projectVersion"
            assert groupId
            assert artifactId
            assert projectVersion
            assert projectVersion == env.PIPELINE_VERSION

            String stubsArtifactId = "${artifactId}-stubs"
            sh "cp build/publications/stubs/pom-default.xml build/publications/stubs/${stubsArtifactId}-${env.PIPELINE_VERSION}.pom"

            String stubJarPath = "build/libs/"
            String stubPomPath = "build/publications/stubs/"

            String uploadRepo = getUploadRepoForVersion(env.PIPELINE_VERSION)
            String repoFolder = groupId.replace('.', '/').trim()
            String targetPath = "${uploadRepo}/${repoFolder}/${stubsArtifactId}/${env.PIPELINE_VERSION}/"
            echo "TargetPath: ${targetPath}"
            String uploadSpec = """
        {
          "files": [
            {
              "pattern": "${stubJarPath}${artifactId}-${env.PIPELINE_VERSION}-stubs.jar",
              "target": "${targetPath}"
            },
            {
              "pattern": "${stubPomPath}${artifactId}-${env.PIPELINE_VERSION}.pom",
              "target": "${targetPath}"
            }
         ]
        }""".stripIndent()
            def buildInfo = artifactoryServer.upload(uploadSpec)
            echo "BuildInfo: ${buildInfo.toString()}"

        }
    }

    @Override
    void executeSonar() {
        executeInScriptContext {
            scriptUtils.mayFail {
                //TODO: Solve issue with "Exception during cache preparation. Marking pipeline as unstable" on multiple cache initialization
//                pipelineConfigurer.cacheWrapper.runWithBuildCacheContext {
                    withSonarQubeEnv('SonarQube') {
                        sh "./gradlew sonarqube -PversionCiModifier='${env.VERSION_CI_MODIFIER}' -Dsonar.projectName=${env.SYS_NAME}-${env.APP_NAME} -Psonar"
                    }
//                }
            }
        }
    }

    private void writeJenkinsGradleInitScriptWithArtifactoryAuthenticationInJenkinsHomeDirectory() {
        executeInScriptContext {
            dir("/home/jenkins/.gradle/init.d/") {  //TODO: Constant
                writeFile(file: "authenticate-artifactory.gradle", text: generateGradleInitScriptWithArtifactoryAuthentication())
            }
        }
    }

    private String generateGradleInitScriptWithArtifactoryAuthentication() {
        //TODO: Extract Repo host to constant
        return """
            //Based on: https://github.com/spring-projects/gradle-init-scripts/blob/master/init.gradle
            logger.info('Applying init.gradle to add Artifactory credentials')
            
            def mavenUsername = System.getenv("MAVEN_USERNAME")
            def mavenPassword = System.getenv("MAVEN_PASSWORD")
            
            assert System.env.MAVEN_USERNAME && System.env.MAVEN_PASSWORD, "MAVEN_USERNAME and/or MAVEN_PASSWORD are not set"
            
            gradle.projectsLoaded {
                rootProject.allprojects {
                    buildscript {
                        repositories.withType(org.gradle.api.artifacts.repositories.MavenArtifactRepository).matching {
                            it.url.host == 'pbatman1.p4.int'
                        }.all {
                            credentials {
                                username = mavenUsername
                                password = mavenPassword
                            }
                        }
                    }
                    repositories.withType(org.gradle.api.artifacts.repositories.MavenArtifactRepository).matching {
                        it.url.host == 'pbatman1.p4.int'
                    }.all {
                        credentials {
                            username = mavenUsername
                            password = mavenPassword
                        }
                    }
                }
                settings.pluginManagement {
                        repositories.withType(org.gradle.api.artifacts.repositories.MavenArtifactRepository).matching {
                            it.url.host == 'pbatman1.p4.int'
                        }.all {
                            credentials {
                                username = mavenUsername
                                password = mavenPassword
                            }
                        }
                }
            }
        """.stripIndent()
    }
}
