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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.cache.internal.VersionStrategy
import spock.lang.Unroll
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

    def "adds nebular release plugin"() {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(ReleasePlugin)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(ReleasePlugin)
    }

    def "adds gradle-git through nebular release plugin"() {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(BaseReleasePlugin)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(BaseReleasePlugin)
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
        strategyName    | strategy
        "snapshot"      | WoogaStrategies.SNAPSHOT
        "pre-release"   | WoogaStrategies.PRE_RELEASE
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
}
