package tests.job

import com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration
import com.lesfurets.jenkins.unit.global.lib.LocalSource

//TODO: OpenShiftDSL is not visible in Idea; Java classes from the same package works fine; Gradle sees no problem in that
import com.openshift.jenkins.plugins.OpenShiftDSL
import org.apache.maven.model.Model
import org.jfrog.hudson.pipeline.dsl.ArtifactoryPipelineGlobal
import org.jfrog.hudson.pipeline.types.ArtifactoryServer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import testSupport.PipelineSpockTestBase

import static groovy.util.GroovyCollections.combinations

class P4PipelineSpec extends PipelineSpockTestBase {

    @Rule
    TemporaryFolder tmpFolder = new TemporaryFolder()

    def setup() {
        scriptRoots = scriptRoots + ['src']
        //and
        loadAndSetBindingForCustomGlobalVars()
        //and
        defineEnvironmentVariables()
        //and
        registerAllowedPipelineMethods()
        //and
        //TODO: Consider workaround with: https://github.com/jenkinsci/JenkinsPipelineUnit/issues/15#issuecomment-290466913
        def openShiftMock = Stub(OpenShiftDSL)
        openShiftMock.project() >> "projectX"
        binding.setVariable("openshift", openShiftMock)
        //and
        ArtifactoryPipelineGlobal artifactoryStub = Stub() {
            server("artifactory") >> Stub(ArtifactoryServer)
        }
        binding.setVariable("Artifactory", artifactoryStub)
    }

    def "not fail on sanity check for #pipelineName with #appType for simple µservice"() {
        given:
            String scriptBody = provideP4PipelineTemplate(pipelineName, appType)
        and:
            //It's problematic to load script directly from String
            File scriptFile = tmpFolder.newFile("${pipelineName}.groovy") << scriptBody
            Script script = loadScript(scriptFile.absolutePath)
        when:
            runScript(script)
        then:
            printCallStack()
            assertJobStatusSuccess()
        where:
            [pipelineName, appType] << combinations(
                    ["p4Pipeline", "p4PreviewPipeline"],
                    ['springboot', 'springbootJava11', 'nodejs', 'nodejsServer'])
    }

    private void loadAndSetBindingForCustomGlobalVars() {
        def p4pipelineLibrary = LibraryConfiguration.library()
                .name("p4-pipeline-library")
                .defaultVersion("master")
                .retriever(LocalSource.localSource("build/libs"))
                .targetPath("build/libs")
                .implicit(true)
                .build()
        helper.registerSharedLibrary(p4pipelineLibrary)
        //TODO: Add power-stage with GitSource or (as currently) use symlink with submodules to have also code completion
    }

    private defineEnvironmentVariables() {
//        //Not needed, kept as reference
//        addEnvVar("APP_CODE_REPO_BRANCH", "master")
    }

    private void registerAllowedPipelineMethods() {
        //TODO: Remove unneed stubbing from declarative pipeline
        helper.registerAllowedMethod("ansiColor", [String], null)
        helper.registerAllowedMethod('git', [Map], null)
        helper.registerAllowedMethod('unstash', [String], null)
        helper.registerAllowedMethod('sh', [Map], { "some/path" })    //TODO: More fine grained stubbing
        helper.registerAllowedMethod('timeout', [Map], null)
        helper.registerAllowedMethod('input', [Closure], { Stub(Map) })
        helper.registerAllowedMethod('mail', [Map], null)
        helper.registerAllowedMethod('fileExists', [String], { it != "/home/jenkins/.m2/repository" })
        helper.registerAllowedMethod('jdk', [String], null)
        helper.registerAllowedMethod('readMavenPom', [Map], { createValidMavenPomModel() })
        //preview
        helper.registerAllowedMethod('gitlab', [Map], null)
        //nodejs
        helper.registerAllowedMethod('sshagent', [Map, Closure], null)
        helper.registerAllowedMethod('readJSON', [Map], { it.file == "package.json" ? [version: "0.0.11"] : "" })

        registerAllowedPipelineMethodsScripted()
    }

    private void registerAllowedPipelineMethodsScripted() {
        helper.registerAllowedMethod("timestamps", [Closure], null)
        helper.registerAllowedMethod("ansiColor", [String, Closure], null)
        helper.registerAllowedMethod("input", [Map], null)
        //power-stage
        helper.registerAllowedMethod("milestone", [Integer], null)
        helper.registerAllowedMethod("retry", [Integer, Closure], null)
        helper.registerAllowedMethod("lock", [Map, Closure], null)
    }

    private Model createValidMavenPomModel() {
        return new Model().with {
            version = "0.0.1"
            groupId = "p4.test"
            artifactId = "mock-project"
            addProperty("revision", "0.0.2")
            addProperty("sha1", "master")
            addProperty("changelist", "-SNAPSHOT")
            return it
        }
    }

    private String provideP4PipelineTemplate(String pipelineName, String appType = "springboot") {
        """
                import com.lesfurets.jenkins.unit.global.lib.Library
                
                @Library('p4-pipeline-library@master') _
                
                ${pipelineName} {
                    appRepo = 'git@pbatman1.p4.int:root/sync-service.git'
                    appRepoBranch = 'master'
                    appType = '${appType}'
                    sysName = 'bsp'
                    stubsUpload=true; extraProperty='extra1'
                }
            """
    }
}
