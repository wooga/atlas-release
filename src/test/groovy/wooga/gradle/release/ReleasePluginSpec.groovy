/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package wooga.gradle.unity

import cz.malohlava.VisTaskExecGraphPlugin
import nebula.plugin.release.NetflixOssStrategies
import nebula.plugin.release.ReleasePlugin
import nebula.test.PluginProjectSpec
import nebula.test.ProjectSpec
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.specs.Spec
import org.gradle.cache.internal.VersionStrategy
import spock.lang.Unroll
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.paket.PaketPlugin
import wooga.gradle.paket.pack.tasks.PaketPack
import wooga.gradle.paket.unity.PaketUnityPlugin
import wooga.gradle.release.WoogaStrategies


class ReleasePluginActivationSpec extends PluginProjectSpec {
    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: 'v0.0.1')
    }

    @Override
    String getPluginName() { return 'net.wooga.release' }
}

class ReleasePluginSpec extends ProjectSpec {
    public static final String PLUGIN_NAME = 'net.wooga.release'

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: 'v0.0.1')
    }

    @Unroll("applies plugin #pluginName")
    def 'Applies other plugins'(String pluginName, Class pluginType) {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(pluginType)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(pluginType)

        where:
        pluginName          | pluginType
        "releasePlugin"     | ReleasePlugin
        "baseReleasePlugin" | BaseReleasePlugin
        "vistec"            | VisTaskExecGraphPlugin
        "githubPublish"     | GithubPublishPlugin
        "paket"             | PaketPlugin
        "paket-unity"       | PaketUnityPlugin
    }

    def findStrategyByName(List<VersionStrategy> strategies, name) {
        strategies.find { it.name == name }
    }

    @Unroll('verify version strategy #strategyName is configured correctly')
    def "veryfy wooga version strategies"() {
        given:
        project.plugins.apply(PLUGIN_NAME)
        def extension = project.extensions.findByType(ReleasePluginExtension)
        def strategies = extension.getVersionStrategies()

        expect:
        findStrategyByName(strategies, strategyName) == strategy

        where:
        strategyName  | strategy
        "snapshot"    | WoogaStrategies.SNAPSHOT
        "pre-release" | WoogaStrategies.PRE_RELEASE
    }

    def "veryfy default version strategies"() {
        given:
        project.plugins.apply(PLUGIN_NAME)
        def extension = project.extensions.findByType(ReleasePluginExtension)

        expect:
        extension.defaultVersionStrategy == NetflixOssStrategies.DEVELOPMENT
    }

    def "add visteg plugin"() {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(VisTaskExecGraphPlugin)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(VisTaskExecGraphPlugin)
    }

    def "creates archives configuration"() {
        given:
        project.plugins.apply(PLUGIN_NAME)

        expect:
        project.configurations.getByName(configuationName)

        where:
        configuationName = wooga.gradle.release.ReleasePlugin.ARCHIVES_CONFIGURATION
    }

    def "works with java plugin"() {
        given:
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(PLUGIN_NAME)

        expect:
        project.configurations.getByName(configuationName)

        where:
        configuationName = wooga.gradle.release.ReleasePlugin.ARCHIVES_CONFIGURATION
    }

    @Unroll('verify task creation of task #taskName')
    def "creates helper tasks"() {
        given:
        assert !project.tasks.hasProperty(taskName)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.tasks.getByName(taskName)

        where:
        taskName << [
                wooga.gradle.release.ReleasePlugin.UNTIY_PACK_TASK,
                wooga.gradle.release.ReleasePlugin.SETUP_TASK,
                wooga.gradle.release.ReleasePlugin.RC_TASK,
                wooga.gradle.release.ReleasePlugin.TEST_TASK
        ]
    }

    @Unroll('verify version strategy for #tagVersion, #scope and #stage')
    def "uses custom wooga semver strategies"() {

        given: "a project with specified release stage and scope"

        project.ext.set('release.stage', stage)
        project.ext.set('release.scope', scope)

        and: "a history"

        commitsBefore.times {
            git.commit(message: 'feature commit')
        }

        git.tag.add(name: "v$tagVersion")

        commitsAfter.times {
            git.commit(message: 'fix commit')
        }

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.version.toString() == expectedVersion

        where:

        tagVersion      | commitsBefore | commitsAfter | stage      | scope   | expectedVersion
        '1.0.0'         | 1             | 3            | "SNAPSHOT" | "minor" | "1.1.0-master00003"
        '1.0.0'         | 1             | 3            | "rc"       | "minor" | "1.1.0-rc00001"
        '1.0.0'         | 1             | 3            | "final"    | "minor" | "1.1.0"
        '1.0.0'         | 1             | 3            | "final"    | "major" | "2.0.0"
        '1.0.0-rc00001' | 10            | 5            | "rc"       | "major" | "1.0.0-rc00002"
        '1.0.0-rc00022' | 0             | 1            | "rc"       | "major" | "1.0.0-rc00023"
        '0.1.0-rc00002' | 22            | 5            | "final"    | "minor" | "0.1.0"
        '0.1.0'         | 3             | 5            | "SNAPSHOT" | "patch" | "0.1.1-master00005"
    }

    @Unroll('verify version branch rename for branch #branchName')
    def "applies branch rename for nuget repos"() {

        given: "a project with specified SNAPSHOT release stage"

        project.ext.set('release.stage', "SNAPSHOT")

        and: "a history"

        if (branchName != "master") {
            git.checkout(branch: "$branchName", startPoint: 'master', createBranch: true)
        }

        git.tag.add(name: "v1.0.0")
        git.commit(message: 'feature commit')

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.version.toString() == expectedVersion

        where:
        branchName          | expectedVersion
        "master"            | "1.1.0-master00001"
        "with/slash"        | "1.1.0-branchWithSlash00001"
        "numbers0123456789" | "1.1.0-branchNumbersZeroOneTwoThreeFourFiveSixSevenEightNine00001"
        "with/slash"        | "1.1.0-branchWithSlash00001"
        "with_underscore"   | "1.1.0-branchWithUnderscore00001"
        "with-dash"         | "1.1.0-branchWithDash00001"
    }

    def createFile(String fileName, File directory) {
        directory.mkdirs()
        return new File(directory, fileName)
    }

    File createMockPaketTemplate(String id, File directory) {
        def f = createFile("paket.template", directory)
        f << """
        type file
        id $id
        author wooga
        """.stripIndent()
        return f
    }

    def "configures paketPack tasks dependsOn if available"() {
        given: "multiple paket.template file"
        createMockPaketTemplate("Wooga.Test1", new File(projectDir, "sub1"))
        createMockPaketTemplate("Wooga.Test2", new File(projectDir, "sub2"))
        createMockPaketTemplate("Wooga.Test3", new File(projectDir, "sub3"))

        and: "no paket pack tasks"
        assert !project.tasks.withType(PaketPack)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        def paketPackTasks = project.tasks.withType(PaketPack)
        paketPackTasks.size() == 3
        paketPackTasks.every {
            it.dependsOn.contains(project.tasks.getByName(wooga.gradle.release.ReleasePlugin.SETUP_TASK))
        }
    }

    def "creates cleanupMetaFiles in subprojects with unity plugin applied"() {
        given: "sub project with unity plugin applied"
        def subProject = addSubproject("unity.test")
        subProject.plugins.apply("net.wooga.unity")

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        subProject.tasks.getByName("cleanMetaFiles")
    }

    def "configures paketPack artifacts as local dependencies"() {
        given: "multiple paket.template file"
        createMockPaketTemplate("Wooga.Test1", new File(projectDir, "sub1"))
        createMockPaketTemplate("Wooga.Test2", new File(projectDir, "sub2"))
        createMockPaketTemplate("Wooga.Test3", new File(projectDir, "sub3"))

        when:
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        then:
        Configuration archive = project.configurations.getByName(wooga.gradle.release.ReleasePlugin.ARCHIVES_CONFIGURATION)
        def artifacts = archive.allArtifacts
        artifacts.size() == 3
        artifacts.every { it.name.contains("Wooga.Test") }
        artifacts.every { it.extension == "nupkg" }
    }

    @Unroll
    def "verify githubPublish onlyIf spec when project.status:#testState and github repository #repository"() {
        given: "gradle project with plugin applied and evaluated"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        and: "configured repository branch"
        if (repository) {
            project.github.repository = repository
        }

        when:
        project.status = testState

        then:
        def githubPublishTask = project.tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)
        Spec<? super Task> testSpec = githubPublishTask.getOnlyIf()
        testSpec.isSatisfiedBy(githubPublishTask) == expectedResult

        where:
        testState      | repository       | expectedResult
        'release'      | "wooga/testRepo" | true
        'release'      | null             | false
        'candidate'    | "wooga/testRepo" | true
        'candidate'    | null             | false
        'snapshot'     | "wooga/testRepo" | false
        'snapshot'     | null             | false
        'random value' | "wooga/testRepo" | false
        'random value' | null             | false
    }
}
