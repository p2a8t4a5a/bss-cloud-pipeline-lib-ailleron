import org.p4.ci.pipeline.util.CacheWrapper
import org.p4.ci.pipeline.util.logging.Logger
import org.p4.ci.pipeline.util.logging.LogLevel

import static org.p4.ci.pipeline.PipelineConstants.ARTIFACTORY_BASE_PATH_WITH_TRAILING_SLASH
import static org.p4.ci.pipeline.util.PipelineUtils.isGradleApplicationType
import static org.p4.ci.pipeline.util.PipelineUtils.isJavaApplicationType

String obtain_npm_app_version(Logger log){
    // TODO - zróbcie to ładniej
    def npm_package = readJSON file: 'package.json'
    log.debug "Version: ${npm_package.version}"
    def gitCommit = sh (script: "git log -n 1 --pretty=format:'%H'", returnStdout: true)
    log.debug "Git commit version: ${gitCommit}"
    def shortGitCommit = gitCommit[0..6]
    return "${npm_package.version}-${env.BUILD_TIME}-${shortGitCommit}"
}

void copy_java_resources(String from){
    sh """
            ls ${from}/*
            rm -rf oc-build && mkdir -p oc-build/deployments
            for t in \$(echo "jar;war;ear" | tr ";" "\\n"); do
            cp -rfv ${from}/*.\$t oc-build/deployments/ 2> /dev/null || echo "No \$t files"
            done
        """
}


void buildApp(String appType, CacheWrapper cacheWrapper, boolean failOnVersionMisconfiguration = false) {

    cacheWrapper.runWithBuildCacheContext {
        Logger.init(this, [ logLevel: LogLevel.INFO ])
        Logger log = new Logger(this)
        
        if (isJavaApplicationType(appType)) {
            error("Unexpected error. Java application build should be handled by new mechanism...")
        } else  if (appType == 'nodejs') {
            def npm_options = ""
            if (log.level == LogLevel.DEBUG){
                npm_options += "--loglevel verbose"
            }
            executeWithSshAccessToGitProvided {
                if (log.level == LogLevel.DEBUG){
                    sh """
                    set +x
                    echo "Building package.json:"
                    cat package.json
                    set -x
                    """
                }
                sh """
                    export http_proxy=http://172.16.19.196:8080
                    export https_proxy=http://172.16.19.196:8080
                    export no_proxy=172.16.42.157,10.10.99.86,pbatman1.p4.int
                    npm config set proxy \$http_proxy
                    npm config set https-proxy \$https_proxy
                    npm config set noproxy \$no_proxy
                    npm install ${npm_options}
                    npm run build ${npm_options}
                """
            }

        } 
        
        if (appType == 'nodejs' || appType == 'nodejsServer') {
            env.PIPELINE_VERSION = obtain_npm_app_version(log)
        }

        log.info "PIPELINE_VERSION: ${env.PIPELINE_VERSION}"
    }
}

def buildImage(String appType) {
    Logger.init(this, [ logLevel: LogLevel.INFO ])
    Logger log = new Logger(this)
    
    //TODO: Temporarily, before extracing deployment to separate class (like building already)
    if (isJavaApplicationType(appType) && isGradleApplicationType(appType)) {
        copy_java_resources('./build/libs')
    } else if (isJavaApplicationType(appType)) {
        copy_java_resources("./target")
    } else  if (appType == 'nodejs') {
        if (log.level == LogLevel.DEBUG){
            sh """
            set +x
            echo "Project building in `pwd`"
            echo "Files in project dir:"
            ls ./
            set -x
            """
        }
        sh """

            rm -rf oc-build && mkdir -p oc-build/
            cp -rfv ./build/* oc-build/

            # itpbc-238
            [ -d s2i ] && cp -rfv ./s2i/* oc-build/

            # create a file for config hierarchy loading
            cd oc-build/
            mkdir ./nginx-default-cfg/
            echo 'include /opt/app-root/etc/nginx.default.d/sys/*.conf;' > ./nginx-default-cfg/p4-sys.conf
            echo 'include /opt/app-root/etc/nginx.default.d/proj/*.conf;' > ./nginx-default-cfg/p4-proj.conf
            echo 'include /opt/app-root/etc/nginx.default.d/app/*.conf;' > ./nginx-default-cfg/p4-app.conf
        """
    } else if (appType == 'nodejsServer') {
        sh """

           rm -rf oc-build && mkdir -p oc-build/
           shopt -s extglob
           cp -rfv !(oc-build) oc-build/

           cd oc-build/
        """
    }


    openshift.withCluster() {
        openshift.withProject("${STAGE0}") {
            def start_build = { openshift.selector("bc", "${APP_NAME}").startBuild("--from-dir=oc-build") }
            def build = null
            //TODO: Pass PIPELINE_VERSION to container image build
            try {
                build = start_build()
            } catch (err){
                log.warn "Problem with starting build: ${err}"
                log.info "Retrying ..."
                sleep 10
                build = start_build()
            }
            def build_obj = build.object()
            log.debug "Build started with command: ${build.actions.cmd}"
            def build_logs = build.logs("-f")
            log.info "Build status: ${build_obj.status.phase}"

            /*def builds = openshift.selector("bc", "${APP_NAME}").related("builds")
            timeout(2) {
                builds.untilEach(1) {
                    return (it.object().status.phase == "Complete")
                }
            }*/

            def retries = 5
            while ((retries > 0) && (build_obj.status.phase != 'Complete') && (build_obj.status.phase != 'Failed')){
                log.debug "Waiting for build to be finished .... [${retries} from 5]"
                retries = retries - 1
                log.debug "Build state: ${build_obj.status.phase}"
                sleep 2
                build_obj = build.object()
            }
            if (build_obj.status.phase != 'Complete') {
                log.fatal "Build incompleted (timeout=30s), state: ${build_obj.status.phase}"
                log.fatal "Build error: ${build_obj.status.message}"
                //error build_obj.status.logSnippet
                log.info "Workaround - for more info refer to https://jira.playmobile.pl/jira/projects/ITPBC/issues/ITPBC-727"
            }
            log.info "Image builded Successfuly"
            String imageDigest = build_obj.status.output.to.imageDigest
            log.info "Tagging ${NAMESPACE}/${APP_NAME}@${imageDigest} as ${NAMESPACE}/${APP_NAME}:${PIPELINE_VERSION}"
            openshift.tag("${NAMESPACE}/${APP_NAME}@${imageDigest}", "${NAMESPACE}/${APP_NAME}:${PIPELINE_VERSION}")
        }
    }
}

def createResources(map) {
    def keys = map.keySet()
    openshift.withCluster() {
        keys.each {
            try {
                openshift.withProject(it) {
                    def files = map[it]
                    for (int j = 0; j < files.size(); ++j) {
                        print "Project ${it}: creating ${files[j]}"
                        def response = openshift.apply(readFile(files[j]))
                        print "Project ${it}: created objects ${response.names()}"
                    }
                }
            } catch (e) {
                print "Error for project $it: ${e.getMessage()}"
            }
        }
    }
}

def getPreviewProjects(sysName) {
    def listOfProjects = []
    openshift.withCluster() {
        def projects = openshift.selector('project').names()
        projects.each {
            def projectName = it - 'project/'
            if (!(projectName ==~ /${sysName}-test|${sysName}-stage|${sysName}-prod|${sysName}-cicd/) && projectName ==~ /${sysName}-.*/) {
                listOfProjects.add(projectName)
            }
        }
    }
    return listOfProjects
}

void writeJenkinsSettingsXmlInCurrentDirectory() {
    writeFile(file: "settings-jenkins.xml", text: generateGenericSettingsXmlContent())
}

private String generateGenericSettingsXmlContent() {
    final String allReposArtifactoryUrl = "${ARTIFACTORY_BASE_PATH_WITH_TRAILING_SLASH}remote-repos"
    return """
        <settings>
             <mirrors>
                 <mirror>
                     <id>artifactory</id>
                     <url>${allReposArtifactoryUrl}</url>
                     <mirrorOf>*,!artifactory-snapshots</mirrorOf>
                 </mirror>
             </mirrors>
            <profiles>
                <profile>
                    <id>jenkins</id>
                     <repositories>
                         <!-- https://jira.playmobile.pl/jira/browse/ITPBC-325 - "mirror" makes repository release-only -->
                         <repository>      
                              <id>artifactory-snapshots</id>
                              <url>${allReposArtifactoryUrl}</url>
                             <releases>
                                 <enabled>false</enabled>
                             </releases>
                             <snapshots>
                                 <enabled>true</enabled>
                             </snapshots>
                         </repository>
                     </repositories>
                </profile>
            </profiles>
            <servers>
                <server>
                    <id>artifactory</id>
                    <username>\${env.MAVEN_USERNAME}</username>
                    <password>\${env.MAVEN_PASSWORD}</password> 
                </server>
                <server>
                    <id>artifactory-snapshots</id>
                    <username>\${env.MAVEN_USERNAME}</username>
                    <password>\${env.MAVEN_PASSWORD}</password> 
                </server>
            </servers>
        </settings>
        """.stripIndent()
}

def executeWithSshAccessToGitProvided(Closure stageBlock, boolean testLogin = true) {
    sshagent(credentials: ["${env.NAMESPACE}-gitlab-ssh-secret"]) {
        if (testLogin) {
            sh """
                echo 'Verifying SSH access to Git repository'
                ssh -T \${APP_REPO%%:*}
            """
        }
        return stageBlock()
    }
}
