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
import spock.lang.Ignore
import wooga.gradle.release.ReleasePlugin
import nebula.test.PluginProjectSpec
import nebula.test.ProjectSpec
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import wooga.gradle.release.AtlasReleasePluginExtension
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
import wooga.gradle.release.utils.WoogaStrategies


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
    public static final String CLEAN_META_FILES_TASK = "cleanMetaFiles"

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: 'v0.0.1')
    }

    @Unroll("creates the task #extensionName extension")
    def 'Creates the extensions'() {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.extensions.findByName(extensionName)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        def extension = project.extensions.findByName(extensionName)
        extensionType.isInstance(extension)

        where:
        extensionName                | extensionType
        ReleasePlugin.EXTENSION_NAME | AtlasReleasePluginExtension.class
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
        "releasePlugin"     | nebula.plugin.release.ReleasePlugin
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
    def "verify wooga version strategies"() {
        given:
        project.plugins.apply(PLUGIN_NAME)
        def extension = project.extensions.findByType(org.ajoberstar.gradle.git.release.base.ReleasePluginExtension)
        def strategies = extension.getVersionStrategies()

        expect:
        findStrategyByName(strategies, strategyName) == strategy

        where:
        strategyName  | strategy
        "snapshot"    | WoogaStrategies.SNAPSHOT
        "pre-release" | WoogaStrategies.PRE_RELEASE
    }

    def "verify default version strategies"() {
        given:
        project.plugins.apply(PLUGIN_NAME)
        def extension = project.extensions.findByType(org.ajoberstar.gradle.git.release.base.ReleasePluginExtension)

        expect:
        extension.defaultVersionStrategy == WoogaStrategies.DEVELOPMENT
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
        configuationName = ReleasePlugin.ARCHIVES_CONFIGURATION
    }

    def "works with java plugin"() {
        given:
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(PLUGIN_NAME)

        expect:
        project.configurations.getByName(configuationName)

        where:
        configuationName = ReleasePlugin.ARCHIVES_CONFIGURATION
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
                ReleasePlugin.UNTIY_PACK_TASK,
                ReleasePlugin.SETUP_TASK,
                ReleasePlugin.TEST_TASK
        ]
    }

    @Unroll('verify version strategy for #tagVersion, #scope and #stage')
    def "uses custom wooga semver strategies"() {

        given: "a project with specified release stage and scope"

        project.ext.set('release.stage', stage)
        if (scope) {
            project.ext.set('release.scope', scope)
        }

        and: "a history"

        if (branchName != "master") {
            git.checkout(branch: "$branchName", startPoint: 'master', createBranch: true)
        }

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

        tagVersion      | commitsBefore | commitsAfter | stage      | scope   | branchName      | expectedVersion
        '1.0.0'         | 1             | 3            | "SNAPSHOT" | "minor" | "master"        | "1.1.0-master00003"
        '1.0.0'         | 1             | 3            | "rc"       | "minor" | "master"        | "1.1.0-rc00001"
        '1.0.0'         | 1             | 3            | "final"    | "minor" | "master"        | "1.1.0"
        '1.0.0'         | 1             | 3            | "final"    | "major" | "master"        | "2.0.0"
        '1.0.0-rc00001' | 10            | 5            | "rc"       | "major" | "master"        | "1.0.0-rc00002"
        '1.0.0-rc00022' | 0             | 1            | "rc"       | "major" | "master"        | "1.0.0-rc00023"
        '0.1.0-rc00002' | 22            | 5            | "final"    | "minor" | "master"        | "0.1.0"
        '0.1.0'         | 3             | 5            | "SNAPSHOT" | "patch" | "master"        | "0.1.1-master00005"
        '1.1.0'         | 3             | 5            | "SNAPSHOT" | null    | "release/2.x"   | "2.0.0-branchReleaseTwoDotx00005"
        '2.0.1'         | 3             | 5            | "SNAPSHOT" | null    | "release/2.1.x" | "2.1.0-branchReleaseTwoDotOneDotx00005"
        '1.1.1'         | 3             | 5            | "rc"       | null    | "release/2.x"   | "2.0.0-rc00001"
        '2.0.1'         | 3             | 5            | "final"    | null    | "release/2.1.x" | "2.1.0"
        '1.1.0'         | 3             | 5            | "SNAPSHOT" | "minor" | "release/2.x"   | "1.2.0-branchReleaseTwoDotx00005"
        '2.0.1'         | 3             | 5            | "SNAPSHOT" | "patch" | "release/2.1.x" | "2.0.2-branchReleaseTwoDotOneDotx00005"
        '1.1.0'         | 3             | 5            | "SNAPSHOT" | null    | "2.x"           | "2.0.0-branchTwoDotx00005"
        '2.0.1'         | 3             | 5            | "SNAPSHOT" | null    | "2.1.x"         | "2.1.0-branchTwoDotOneDotx00005"
        '1.1.1'         | 3             | 5            | "rc"       | null    | "2.x"           | "2.0.0-rc00001"
        '2.0.1'         | 3             | 5            | "final"    | null    | "2.1.x"         | "2.1.0"
        '1.1.0'         | 3             | 5            | "SNAPSHOT" | "minor" | "2.x"           | "1.2.0-branchTwoDotx00005"
        '2.0.1'         | 3             | 5            | "SNAPSHOT" | "patch" | "2.1.x"         | "2.0.2-branchTwoDotOneDotx00005"
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
        "master"            | "1.0.1-master00001"
        "with/slash"        | "1.0.1-branchWithSlash00001"
        "numbers0123456789" | "1.0.1-branchNumbersZeroOneTwoThreeFourFiveSixSevenEightNine00001"
        "with/slash"        | "1.0.1-branchWithSlash00001"
        "with_underscore"   | "1.0.1-branchWithUnderscore00001"
        "with-dash"         | "1.0.1-branchWithDash00001"
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
            it.dependsOn.contains(project.tasks.getByName(ReleasePlugin.SETUP_TASK))
        }
    }
    @Ignore("can't access properties. should be an integration test")
    def "creates cleanupMetaFiles in subprojects with unity plugin applied"() {
        given: "sub project with unity plugin applied"
        def subProject = addSubproject("unity.test")
        subProject.plugins.apply("net.wooga.unity")

        when:
        project.plugins.apply(PLUGIN_NAME)
        subProject.evaluate()
        project.evaluate()

        then:
        subProject.tasks.getByName("cleanMetaFiles")
    }

    @Ignore("can't access properties. should be an integration test")
    def "configures paketPack tasks dependsOn cleanupMetaFiles when unity plugin is applied"() {
        given: "multiple paket.template file"
        createMockPaketTemplate("Wooga.Test1", new File(projectDir, "sub1"))
        createMockPaketTemplate("Wooga.Test2", new File(projectDir, "sub2"))
        createMockPaketTemplate("Wooga.Test3", new File(projectDir, "sub3"))

        and: "sub project with unity plugin applied"
        def subProject = addSubproject("unity.test")
        subProject.plugins.apply("net.wooga.unity")

        and: "sub project 2 with unity plugin applied"
        def subProject2 = addSubproject("unity.test2")
        subProject2.plugins.apply("net.wooga.unity")

        and: "sub project 3 without unity plugin applied"
        def subProject3 = addSubproject("sub3.test")

        and: "a custom cleanMetaFiles in this non unity project"
        subProject3.tasks.create("cleanMetaFiles")

        when:
        project.plugins.apply(PLUGIN_NAME)
        subProject.evaluate()
        subProject2.evaluate()
        subProject3.evaluate()
        project.evaluate()

        then:
        def paketPackTasks = project.tasks.withType(PaketPack)
        paketPackTasks.size() == 3
        paketPackTasks.every {
            it.dependsOn.contains(subProject.tasks.getByName(CLEAN_META_FILES_TASK))
        }

        paketPackTasks.every {
            it.dependsOn.contains(subProject2.tasks.getByName(CLEAN_META_FILES_TASK))
        }

        paketPackTasks.every {
            !it.dependsOn.contains(subProject3.tasks.getByName(CLEAN_META_FILES_TASK))
        }
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
        Configuration archive = project.configurations.getByName(ReleasePlugin.ARCHIVES_CONFIGURATION)
        def artifacts = archive.allArtifacts
        artifacts.size() == 3
        artifacts.every { it.name.contains("Wooga.Test") }
        artifacts.every { it.extension == "nupkg" }
    }

    def "sets version on paketPack tasks"() {
        given: "multiple paket.template files with version set"
        createMockPaketTemplate("Wooga.Test1", new File(projectDir, "sub1")) << "version 2.0.0"
        createMockPaketTemplate("Wooga.Test2", new File(projectDir, "sub2")) << "version 2.0.0"
        createMockPaketTemplate("Wooga.Test3", new File(projectDir, "sub3")) << "version 2.0.0"

        when:
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        then:
        def tasks = project.tasks.withType(PaketPack)
        tasks.every { it.version != "2.0.0" }
    }

    @Unroll
    def "verify githubPublish onlyIf spec when project.status:#testState and github repository #repositoryName"() {
        given: "gradle project with plugin applied and evaluated"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        and: "configured repository branch"
        if (repositoryName) {
            project.github.repositoryName = repositoryName
        }

        when:
        project.status = testState

        then:
        def githubPublishTask = project.tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)
        Spec<? super Task> testSpec = githubPublishTask.getOnlyIf()
        testSpec.isSatisfiedBy(githubPublishTask) == expectedResult

        where:
        testState      | repositoryName   | expectedResult
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
