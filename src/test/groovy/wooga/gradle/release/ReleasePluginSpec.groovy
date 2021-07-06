/*
 * Copyright 2017-2021 the original author or authors.
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

package wooga.gradle.release


import nebula.test.ProjectSpec
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.specs.Spec
import org.gradle.cache.internal.VersionStrategy
import spock.lang.Ignore
import spock.lang.Unroll
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.paket.PaketPlugin
import wooga.gradle.paket.pack.tasks.PaketPack
import wooga.gradle.paket.unity.PaketUnityPlugin


class ReleasePluginSpec extends ProjectSpec {
    public static final String PLUGIN_NAME = 'net.wooga.release'
    public static final String CLEAN_META_FILES_TASK = "cleanMetaFiles"

    Grgit git

    def setup() {
        new File(projectDir, '.gitignore') << """
        userHome/
        """.stripIndent()

        git = Grgit.init(dir: projectDir)
        git.init()
        git.add(patterns: ['.gitignore'])
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
        pluginName      | pluginType
        "githubPublish" | GithubPublishPlugin
        "paket"         | PaketPlugin
        "paket-unity"   | PaketUnityPlugin
    }

    def findStrategyByName(List<VersionStrategy> strategies, name) {
        strategies.find { it.name == name }
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
                ReleasePlugin.SETUP_TASK,
                ReleasePlugin.TEST_TASK_NAME
        ]
    }

    @Unroll('verify wooga packages version strategy for #tagVersion, #scope and #stage')
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

    @Unroll('verify inferred custom semver 1.x version from nearestNormal: #nearestNormal, nearestAny: #nearestAnyTitle, scope: #scopeTitle, stage: #stage and branch: #branchName to be #expectedVersion')
    def "uses custom wooga application strategies v1"() {

        given: "a project with specified release stage and scope"

        project.ext.set('release.stage', stage)
        if (scope != _) {
            project.ext.set('release.scope', scope)
        }

        and: "a history"

        if (branchName != "master") {
            git.checkout(branch: "$branchName", startPoint: 'master', createBranch: true)
        }

        5.times {
            git.commit(message: 'feature commit')
        }

        git.tag.add(name: "v$nearestNormal")

        distance.times {
            git.commit(message: 'fix commit')
        }

        if (nearestAny != _) {
            git.tag.add(name: "v$nearestAny")
            distance.times {
                git.commit(message: 'fix commit')
            }
        }

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.version.toString() == expectedVersion

        where:
        nearestAny      | distance | stage      | scope   | branchName      | expectedVersion
        _               | 1        | "snapshot" | _       | "master"        | "1.0.1-master00001"
        _               | 2        | "snapshot" | "major" | "master"        | "2.0.0-master00002"
        _               | 3        | "snapshot" | "minor" | "master"        | "1.1.0-master00003"
        _               | 4        | "snapshot" | "patch" | "master"        | "1.0.1-master00004"

        _               | 1        | "snapshot" | _       | "develop"       | "1.0.1-branchDevelop00001"
        _               | 2        | "snapshot" | "major" | "develop"       | "2.0.0-branchDevelop00002"
        _               | 3        | "snapshot" | "minor" | "develop"       | "1.1.0-branchDevelop00003"
        _               | 4        | "snapshot" | "patch" | "develop"       | "1.0.1-branchDevelop00004"

        _               | 1        | "snapshot" | _       | "feature/check" | "1.0.1-branchFeatureCheck00001"
        _               | 2        | "snapshot" | _       | "hotfix/check"  | "1.0.1-branchHotfixCheck00002"
        _               | 3        | "snapshot" | _       | "fix/check"     | "1.0.1-branchFixCheck00003"
        _               | 4        | "snapshot" | _       | "feature-check" | "1.0.1-branchFeatureCheck00004"
        _               | 5        | "snapshot" | _       | "hotfix-check"  | "1.0.1-branchHotfixCheck00005"
        _               | 6        | "snapshot" | _       | "fix-check"     | "1.0.1-branchFixCheck00006"
        _               | 7        | "snapshot" | _       | "PR-22"         | "1.0.1-branchPRTwoTwo00007"
        // TODO: Not supported by the atlas-version plugin currently
//        _               | 8        | "snapshot" | _       | "fix/a_-bug"    | "1.0.1-branchFixABug00008"
//        _               | 9        | "snapshot" | _       | "fix/-_.bug"    | "1.0.1-branchFixDotbug00009"
//        _               | 10       | "snapshot" | _       | "fix/-.-.-bug"  | "1.0.1-branchFixDotDotBug00010"

        _               | 1        | "snapshot" | _       | "release/1.x"   | "1.0.1-branchReleaseOneDotx00001"
        _               | 2        | "snapshot" | _       | "release-1.x"   | "1.0.1-branchReleaseOneDotx00002"
        _               | 3        | "snapshot" | _       | "release/1.0.x" | "1.0.1-branchReleaseOneDotZeroDotx00003"
        _               | 4        | "snapshot" | _       | "release-1.0.x" | "1.0.1-branchReleaseOneDotZeroDotx00004"

        _               | 2        | "snapshot" | _       | "1.x"           | "1.0.1-branchOneDotx00002"
        _               | 4        | "snapshot" | _       | "1.0.x"         | "1.0.1-branchOneDotZeroDotx00004"

        _               | 1        | "rc"       | _       | "master"        | "1.0.1-rc00001"
        _               | 2        | "rc"       | "major" | "master"        | "2.0.0-rc00001"
        _               | 3        | "rc"       | "minor" | "master"        | "1.1.0-rc00001"
        _               | 4        | "rc"       | "patch" | "master"        | "1.0.1-rc00001"

        '1.1.0-rc00001' | 1        | "rc"       | _       | "master"        | "1.1.0-rc00002"
        '1.1.0-rc00002' | 1        | "rc"       | _       | "master"        | "1.1.0-rc00003"

        _               | 1        | "rc"       | _       | "release/1.x"   | "1.0.1-rc00001"
        _               | 2        | "rc"       | _       | "release-1.x"   | "1.0.1-rc00001"
        _               | 3        | "rc"       | _       | "release/1.0.x" | "1.0.1-rc00001"
        _               | 4        | "rc"       | _       | "release-1.0.x" | "1.0.1-rc00001"

        _               | 1        | "rc"       | _       | "1.x"           | "1.0.1-rc00001"
        _               | 3        | "rc"       | _       | "1.0.x"         | "1.0.1-rc00001"

        "1.1.0-rc00001" | 1        | "rc"       | _       | "release/1.x"   | "1.1.0-rc00002"
        "1.1.0-rc00001" | 2        | "rc"       | _       | "release-1.x"   | "1.1.0-rc00002"
        "1.0.1-rc00001" | 3        | "rc"       | _       | "release/1.0.x" | "1.0.1-rc00002"
        "1.0.1-rc00001" | 4        | "rc"       | _       | "release-1.0.x" | "1.0.1-rc00002"

        "1.1.0-rc00001" | 1        | "rc"       | _       | "1.x"           | "1.1.0-rc00002"
        "1.0.1-rc00001" | 3        | "rc"       | _       | "1.0.x"         | "1.0.1-rc00002"

        _               | 1        | "final"    | _       | "master"        | "1.0.1"
        _               | 2        | "final"    | "major" | "master"        | "2.0.0"
        _               | 3        | "final"    | "minor" | "master"        | "1.1.0"
        _               | 4        | "final"    | "patch" | "master"        | "1.0.1"

        _               | 1        | "final"    | _       | "release/1.x"   | "1.0.1"
        _               | 1        | "final"    | _       | "release/1.x"   | "1.0.1"
        _               | 2        | "final"    | _       | "release-1.x"   | "1.0.1"
        _               | 3        | "final"    | _       | "release/1.0.x" | "1.0.1"
        _               | 4        | "final"    | _       | "release-1.0.x" | "1.0.1"

        _               | 1        | "final"    | _       | "1.x"           | "1.0.1"
        _               | 3        | "final"    | _       | "1.0.x"         | "1.0.1"

        nearestNormal = '1.0.0'
        nearestAnyTitle = (nearestAny == _) ? "unset" : nearestAny
        scopeTitle = (scope == _) ? "unset" : scope
    }

    @Unroll('verify inferred semver 2.x version from nearestNormal: #nearestNormal, nearestAny: #nearestAnyTitle, scope: #scopeTitle, stage: #stage and branch: #branchName to be #expectedVersion')
    def "uses custom wooga application strategies v2"() {

        given: "a project with specified release stage and scope"

        project.ext.set('release.stage', stage)
        project.ext.set('version.scheme', 'semver2')
        if (scope != _) {
            project.ext.set('release.scope', scope)
        }

        and: "a history"

        if (branchName != "master") {
            git.checkout(branch: "$branchName", startPoint: 'master', createBranch: true)
        }

        5.times {
            git.commit(message: 'feature commit')
        }

        git.tag.add(name: "v$nearestNormal")

        distance.times {
            git.commit(message: 'fix commit')
        }

        if (nearestAny != _) {
            git.tag.add(name: "v$nearestAny")
            distance.times {
                git.commit(message: 'fix commit')
            }
        }

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.version.toString() == expectedVersion

        where:
        nearestAny        | distance | stage        | scope   | branchName                 | expectedVersion
        _                 | 1        | "ci"         | _       | "master"                   | "1.1.0-master.1"
        _                 | 2        | "ci"         | "major" | "master"                   | "2.0.0-master.2"
        _                 | 3        | "ci"         | "minor" | "master"                   | "1.1.0-master.3"
        _                 | 4        | "ci"         | "patch" | "master"                   | "1.0.1-master.4"

        _                 | 1        | "ci"         | _       | "develop"                  | "1.1.0-develop.1"
        _                 | 2        | "ci"         | "major" | "develop"                  | "2.0.0-develop.2"
        _                 | 3        | "ci"         | "minor" | "develop"                  | "1.1.0-develop.3"
        _                 | 4        | "ci"         | "patch" | "develop"                  | "1.0.1-develop.4"

        _                 | 1        | "ci"         | _       | "feature/check"            | "1.1.0-branch.feature.check.1"
        _                 | 2        | "ci"         | _       | "hotfix/check"             | "1.0.1-branch.hotfix.check.2"
        _                 | 3        | "ci"         | _       | "fix/check"                | "1.1.0-branch.fix.check.3"
        _                 | 4        | "ci"         | _       | "feature-check"            | "1.1.0-branch.feature.check.4"
        _                 | 5        | "ci"         | _       | "hotfix-check"             | "1.0.1-branch.hotfix.check.5"
        _                 | 6        | "ci"         | _       | "fix-check"                | "1.1.0-branch.fix.check.6"
        _                 | 7        | "ci"         | _       | "PR-22"                    | "1.1.0-branch.pr.22.7"
        _                 | 8        | "ci"         | _       | "fix/a_-bug"               | "1.1.0-branch.fix.a.bug.8"
        _                 | 9        | "ci"         | _       | "fix/-_.bug"               | "1.1.0-branch.fix.bug.9"
        _                 | 10       | "ci"         | _       | "fix/-.-.-bug"             | "1.1.0-branch.fix.bug.10"

        _                 | 1        | "ci"         | _       | "release/1.x"              | "1.1.0-branch.release.1.x.1"
        _                 | 2        | "ci"         | _       | "release-1.x"              | "1.1.0-branch.release.1.x.2"
        _                 | 3        | "ci"         | _       | "release/1.0.x"            | "1.0.1-branch.release.1.0.x.3"
        _                 | 4        | "ci"         | _       | "release-1.0.x"            | "1.0.1-branch.release.1.0.x.4"

        _                 | 2        | "ci"         | _       | "1.x"                      | "1.1.0-branch.1.x.2"
        _                 | 4        | "ci"         | _       | "1.0.x"                    | "1.0.1-branch.1.0.x.4"
        '2.0.0-rc.2'      | 0        | "ci"         | _       | "release/2.x"              | "2.0.0-branch.release.2.x.0"

        _                 | 1        | "snapshot"   | _       | "master"                   | "1.1.0-master.1"
        _                 | 2        | "snapshot"   | "major" | "master"                   | "2.0.0-master.2"
        _                 | 3        | "snapshot"   | "minor" | "master"                   | "1.1.0-master.3"
        _                 | 4        | "snapshot"   | "patch" | "master"                   | "1.0.1-master.4"

        _                 | 1        | "snapshot"   | _       | "develop"                  | "1.1.0-develop.1"
        _                 | 2        | "snapshot"   | "major" | "develop"                  | "2.0.0-develop.2"
        _                 | 3        | "snapshot"   | "minor" | "develop"                  | "1.1.0-develop.3"
        _                 | 4        | "snapshot"   | "patch" | "develop"                  | "1.0.1-develop.4"

        _                 | 1        | "snapshot"   | _       | "feature/check"            | "1.1.0-branch.feature.check.1"
        _                 | 2        | "snapshot"   | _       | "hotfix/check"             | "1.0.1-branch.hotfix.check.2"
        _                 | 3        | "snapshot"   | _       | "fix/check"                | "1.1.0-branch.fix.check.3"
        _                 | 4        | "snapshot"   | _       | "feature-check"            | "1.1.0-branch.feature.check.4"
        _                 | 5        | "snapshot"   | _       | "hotfix-check"             | "1.0.1-branch.hotfix.check.5"
        _                 | 6        | "snapshot"   | _       | "fix-check"                | "1.1.0-branch.fix.check.6"
        _                 | 7        | "snapshot"   | _       | "PR-22"                    | "1.1.0-branch.pr.22.7"
        _                 | 8        | "snapshot"   | _       | "fix/a_-bug"               | "1.1.0-branch.fix.a.bug.8"
        _                 | 9        | "snapshot"   | _       | "fix/-_.bug"               | "1.1.0-branch.fix.bug.9"
        _                 | 10       | "snapshot"   | _       | "fix/-.-.-bug"             | "1.1.0-branch.fix.bug.10"

        _                 | 1        | "snapshot"   | _       | "release/1.x"              | "1.1.0-branch.release.1.x.1"
        _                 | 2        | "snapshot"   | _       | "release-1.x"              | "1.1.0-branch.release.1.x.2"
        _                 | 3        | "snapshot"   | _       | "release/1.0.x"            | "1.0.1-branch.release.1.0.x.3"
        _                 | 4        | "snapshot"   | _       | "release-1.0.x"            | "1.0.1-branch.release.1.0.x.4"

        _                 | 2        | "snapshot"   | _       | "1.x"                      | "1.1.0-branch.1.x.2"
        _                 | 4        | "snapshot"   | _       | "1.0.x"                    | "1.0.1-branch.1.0.x.4"

        _                 | 1        | "staging"    | _       | "master"                   | "1.1.0-staging.1"
        _                 | 2        | "staging"    | "major" | "master"                   | "2.0.0-staging.1"
        _                 | 3        | "staging"    | "minor" | "master"                   | "1.1.0-staging.1"
        _                 | 4        | "staging"    | "patch" | "master"                   | "1.0.1-staging.1"

        '1.1.0-staging.1' | 1        | "staging"    | _       | "master"                   | "1.1.0-staging.2"
        '1.1.0-staging.2' | 1        | "staging"    | _       | "master"                   | "1.1.0-staging.3"

        _                 | 1        | "staging"    | _       | "release/1.x"              | "1.1.0-staging.1"
        _                 | 2        | "staging"    | _       | "release-1.x"              | "1.1.0-staging.1"
        _                 | 3        | "staging"    | _       | "release/1.0.x"            | "1.0.1-staging.1"
        _                 | 4        | "staging"    | _       | "release-1.0.x"            | "1.0.1-staging.1"

        _                 | 1        | "staging"    | _       | "1.x"                      | "1.1.0-staging.1"
        _                 | 3        | "staging"    | _       | "1.0.x"                    | "1.0.1-staging.1"

        "1.1.0-staging.1" | 1        | "staging"    | _       | "release/1.x"              | "1.1.0-staging.2"
        "1.1.0-staging.1" | 2        | "staging"    | _       | "release-1.x"              | "1.1.0-staging.2"
        "1.0.1-staging.1" | 3        | "staging"    | _       | "release/1.0.x"            | "1.0.1-staging.2"
        "1.0.1-staging.1" | 4        | "staging"    | _       | "release-1.0.x"            | "1.0.1-staging.2"

        "1.1.0-staging.1" | 1        | "staging"    | _       | "1.x"                      | "1.1.0-staging.2"
        "1.0.1-staging.1" | 3        | "staging"    | _       | "1.0.x"                    | "1.0.1-staging.2"

        _                 | 1        | "rc"         | _       | "master"                   | "1.1.0-rc.1"
        _                 | 2        | "rc"         | "major" | "master"                   | "2.0.0-rc.1"
        _                 | 3        | "rc"         | "minor" | "master"                   | "1.1.0-rc.1"
        _                 | 4        | "rc"         | "patch" | "master"                   | "1.0.1-rc.1"

        '1.1.0-rc.1'      | 1        | "rc"         | _       | "master"                   | "1.1.0-rc.2"
        '1.1.0-rc.2'      | 1        | "rc"         | _       | "master"                   | "1.1.0-rc.3"

        _                 | 1        | "rc"         | _       | "release/1.x"              | "1.1.0-rc.1"
        _                 | 2        | "rc"         | _       | "release-1.x"              | "1.1.0-rc.1"
        _                 | 3        | "rc"         | _       | "release/1.0.x"            | "1.0.1-rc.1"
        _                 | 4        | "rc"         | _       | "release-1.0.x"            | "1.0.1-rc.1"

        _                 | 1        | "rc"         | _       | "1.x"                      | "1.1.0-rc.1"
        _                 | 3        | "rc"         | _       | "1.0.x"                    | "1.0.1-rc.1"

        "1.1.0-rc.1"      | 1        | "rc"         | _       | "release/1.x"              | "1.1.0-rc.2"
        "1.1.0-rc.1"      | 2        | "rc"         | _       | "release-1.x"              | "1.1.0-rc.2"
        "1.0.1-rc.1"      | 3        | "rc"         | _       | "release/1.0.x"            | "1.0.1-rc.2"
        "1.0.1-rc.1"      | 4        | "rc"         | _       | "release-1.0.x"            | "1.0.1-rc.2"

        "1.1.0-rc.1"      | 1        | "rc"         | _       | "1.x"                      | "1.1.0-rc.2"
        "1.0.1-rc.1"      | 3        | "rc"         | _       | "1.0.x"                    | "1.0.1-rc.2"

        "1.1.0-rc.1"      | 1        | "staging"    | _       | "1.x"                      | "1.1.0-staging.1"
        "1.0.1-rc.1"      | 3        | "staging"    | _       | "1.0.x"                    | "1.0.1-staging.1"

        _                 | 1        | "production" | _       | "master"                   | "1.1.0"
        _                 | 2        | "production" | "major" | "master"                   | "2.0.0"
        _                 | 3        | "production" | "minor" | "master"                   | "1.1.0"
        _                 | 4        | "production" | "patch" | "master"                   | "1.0.1"

        _                 | 1        | "production" | _       | "release/1.x"              | "1.1.0"
        _                 | 1        | "production" | _       | "release/1.x"              | "1.1.0"
        _                 | 2        | "production" | _       | "release-1.x"              | "1.1.0"
        _                 | 3        | "production" | _       | "release/1.0.x"            | "1.0.1"
        _                 | 4        | "production" | _       | "release-1.0.x"            | "1.0.1"

        _                 | 1        | "production" | _       | "1.x"                      | "1.1.0"
        _                 | 3        | "production" | _       | "1.0.x"                    | "1.0.1"

        _                 | 1        | "final"      | _       | "master"                   | "1.1.0"
        _                 | 2        | "final"      | "major" | "master"                   | "2.0.0"
        _                 | 3        | "final"      | "minor" | "master"                   | "1.1.0"
        _                 | 4        | "final"      | "patch" | "master"                   | "1.0.1"

        _                 | 1        | "final"      | _       | "release/1.x"              | "1.1.0"
        _                 | 1        | "final"      | _       | "release/1.x"              | "1.1.0"
        _                 | 2        | "final"      | _       | "release-1.x"              | "1.1.0"
        _                 | 3        | "final"      | _       | "release/1.0.x"            | "1.0.1"
        _                 | 4        | "final"      | _       | "release-1.0.x"            | "1.0.1"

        _                 | 1        | "final"      | _       | "1.x"                      | "1.1.0"
        _                 | 3        | "final"      | _       | "1.0.x"                    | "1.0.1"
        _                 | 10       | "ci"         | _       | "test/build01-"            | "1.1.0-branch.test.build.1.10"
        _                 | 22       | "ci"         | _       | "test/build01+"            | "1.1.0-branch.test.build.1.22"
        _                 | 45       | "ci"         | _       | "test/build01_"            | "1.1.0-branch.test.build.1.45"
        _                 | 204      | "ci"         | _       | "test/build01"             | "1.1.0-branch.test.build.1.204"
        _                 | 100      | "ci"         | _       | "test/build.01"            | "1.1.0-branch.test.build.1.100"
        _                 | 55       | "ci"         | _       | "test/build002"            | "1.1.0-branch.test.build.2.55"
        _                 | 66       | "ci"         | _       | "test/build.002"           | "1.1.0-branch.test.build.2.66"
        _                 | 789      | "ci"         | _       | "test/build000000000003"   | "1.1.0-branch.test.build.3.789"
        _                 | 777      | "ci"         | _       | "test/build.000000000003"  | "1.1.0-branch.test.build.3.777"
        _                 | 789      | "ci"         | _       | "test/build000000.000003"  | "1.1.0-branch.test.build.0.3.789"
        _                 | 3        | "ci"         | _       | "test/build.000000.000003" | "1.1.0-branch.test.build.0.3.3"
        _                 | 3        | "ci"         | _       | "release/1.00.x"           | "1.0.1-branch.release.1.0.x.3"

        nearestNormal = '1.0.0'
        nearestAnyTitle = (nearestAny == _) ? "unset" : nearestAny
        scopeTitle = (scope == _) ? "unset" : scope
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

    /**
     * Tests how these tasks predicate against {@link wooga.gradle.release.utils.ProjectPropertyValueTaskSpec}
     * */
    @Unroll
    def "predicates for #taskName are #satisfiedMessage when release stage is #value and github repo #repoStatus"() {

        given: "a gradle project with release.stage being set"
        project.extensions["release.stage"] = value

        when: "a gradle project with plugin applied and evaluated"
        project.plugins.apply(PLUGIN_NAME)

        and: "configured repository branch"
        project.github.repositoryName.set(repositoryName)

        then:
        def task = project.tasks.getByName(taskName)
        Spec<? super Task> predicate = task.getOnlyIf()
        assert predicate.isSatisfiedBy(task) == satisfied

        where:
        value      | repositoryName   | satisfied | taskName
        'rc'       | "wooga/testRepo" | true      | GithubPublishPlugin.PUBLISH_TASK_NAME
        'rc'       | "wooga/testRepo" | true      | ReleasePlugin.RELEASE_NOTES_BODY_TASK_NAME
        'rc'       | null             | false     | GithubPublishPlugin.PUBLISH_TASK_NAME
        'rc'       | null             | true      | ReleasePlugin.RELEASE_NOTES_BODY_TASK_NAME
        'final'    | "wooga/testRepo" | true      | GithubPublishPlugin.PUBLISH_TASK_NAME
        'final'    | "wooga/testRepo" | true      | ReleasePlugin.RELEASE_NOTES_BODY_TASK_NAME
        'final'    | null             | false     | GithubPublishPlugin.PUBLISH_TASK_NAME
        'final'    | null             | true      | ReleasePlugin.RELEASE_NOTES_BODY_TASK_NAME
        'snapshot' | "wooga/testRepo" | false     | GithubPublishPlugin.PUBLISH_TASK_NAME
        'snapshot' | "wooga/testRepo" | false     | ReleasePlugin.RELEASE_NOTES_BODY_TASK_NAME
        'snapshot' | null             | false     | GithubPublishPlugin.PUBLISH_TASK_NAME
        'snapshot' | null             | false     | ReleasePlugin.RELEASE_NOTES_BODY_TASK_NAME

        satisfiedMessage = satisfied ? "satisfied" : "not satisfied"
        repoStatus = repositoryName ? "is set" : "is not set"
    }

}
