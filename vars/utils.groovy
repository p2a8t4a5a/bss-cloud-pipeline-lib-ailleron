import groovy.json.JsonSlurper
import org.p4.ci.pipeline.util.PipelineScriptUtils
import org.p4.ci.pipeline.util.logging.Logger
import org.p4.ci.pipeline.util.logging.LogLevel

// def GIT_SERVER='pbatman1.p4.int'

def deployScripts(dir, String scripts="") {
    def SCRIPTS = ("common.sh " + scripts).split();
    if (!scripts){
        SCRIPTS = 'common.sh configure-project.sh create-preview.sh deploy-app.sh deploy-cicd.sh deploy-jenkins.sh'.split()
    }

    def destDir = dir + '/library-scripts/'
    sh "[ -d ${destDir} ] || mkdir ${destDir}"

    println "Deploying deployment scripts to " + destDir
    for (int i = 0; i < SCRIPTS.size(); i++) {
        writeFile file: destDir + SCRIPTS[i], text: libraryResource('scripts/' + SCRIPTS[i])
    }
    sh "chmod +x ${destDir}/*.sh"
}

// fetch configuration data from git repos (default + system specific) and merge it into single directory
def downloadConfigs(destDir, sysRepo, sysRepoBranch = 'master', defaultRepoBranch = 'master') {
    def repo1 = "git@pbatman1.p4.int:root/default-system-config.git"
    def repo2 = sysRepo
    dir(destDir) {
        deleteDir() // clean up dir first
    }
    dir(destDir + '/config-repo-src') {
        println "Downloading config from default-system-config repo"
        // global/default
        checkout([$class: 'GitSCM', branches: [[name: "*/${defaultRepoBranch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '1']], submoduleCfg: [], userRemoteConfigs: [[url: "${repo1}", credentialsId: "${env.NAMESPACE}-gitlab-ssh-secret"]]])

        println "Downloading config from system repo"
        // system config
        checkout([$class: 'GitSCM', branches: [[name: "*/${sysRepoBranch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '2']], submoduleCfg: [], userRemoteConfigs: [[url: "${repo2}", credentialsId: "${env.NAMESPACE}-gitlab-ssh-secret"]]])
        // merge configs
        sh """
            set +x
            echo "\ncoping files from level 1"
            cp -a 1/* ../

            # merge existing files in lower level repo by appending it to upper level
            echo "\n"
            for f in \$(find ../ -type f -not -path '*/config-repo-src/*');do
                filename=\${f#../}
                echo "\$filename found"
                if [ -f 2/\$filename ];then
                    echo "2/\$filename exists on level 2 - appending"
                    cat 2/\$filename >> ../\$filename
                    rm 2/\$filename
                fi
            done
            
            echo "\nCoping rest of files from level 2"
            cp -a 2/* ../
            echo "\n Clear temporary files\n"
            rm -rf  1/
            rm -rf 2/
            set -x
            echo "\nConfiguration files after merge:"
            ls -R ../

        """
    }


}

def prepConfig() {
    dir('config-data') {
        deleteDir()
    }
    unstash 'config-data'
}

String getBuildTimeAsString() {
    def dateFormat = new java.text.SimpleDateFormat("yyyyMMdd'-'HHmmss")
    Date date = new Date()
    return dateFormat.format(date)
}

String sanitizeBranchName(String branchName) {
    return branchName.replace("/", "-").toLowerCase()
}

//TODO: Make it more general with different configurable ways of notification (email, slack, ...)
void notifyAboutDeploymentViaEmail(String app, String version, String proj, String url, String emails) {

    if (env.EMAIL_NOFICATIONS_DISABLED == "true") {
        return
    }

    def body = """
        Application <b>${app}</b>, version: <b>${version}</b> was deployed to: <b>${proj}</b>
        </br>
        </br>
        
        For more details go here: <a href="${url}">${url}</a>
        """.stripIndent()

    send_email(emails, "[Jenkins] Deployment report (${app})", body)
}

def void send_email(String emails, String subject, String body){
    Logger.init(this, [ logLevel: LogLevel.INFO ])
    Logger log = new Logger(this)

    if (!emails) {
        log.warn "No emails provided. Skipping notification."
        return
    }
    log.info "Email address to send: ${emails}"

    try {
        mail body: body,
                subject: subject,
                from: 'ci@playmobile.pl',
                mimeType: 'text/html',
                to: emails

    } catch (Exception e) {
        currentBuild.result = "UNSTABLE"
        println "Error while sending email: ${e.toString()}"
        println "Exception: ${PipelineScriptUtils.fetchStackTraceAsString(e)}"
    }
}

@NonCPS
def parseJson(content) {
    return new JsonSlurper().parseText(content)
}

def addListElementToMap(def map, def key, def element) {
    if (map[key] == null)
        map[key] = []
    map[key].add(element)
}
