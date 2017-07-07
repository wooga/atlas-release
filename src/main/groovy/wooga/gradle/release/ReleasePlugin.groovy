/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import cz.malohlava.VisTaskExecGraphPlugin
import cz.malohlava.VisTegPluginExtension
import nebula.core.ProjectType
import nebula.plugin.release.NetflixOssStrategies
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Copy
import org.gradle.language.base.plugins.LifecycleBasePlugin
import wooga.gradle.github.publish.GithubPublish
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.paket.PaketPlugin
import wooga.gradle.paket.base.PaketBasePlugin
import wooga.gradle.paket.get.PaketGetPlugin
import wooga.gradle.paket.pack.tasks.PaketPack
import wooga.gradle.paket.unity.PaketUnityPlugin

class ReleasePlugin implements Plugin<Project> {

    static Logger logger = Logging.getLogger(ReleasePlugin)

    static final String ARCHIVES_CONFIGURATION = "archives"
    static final String UNTIY_PACK_TASK = "unityPack"
    static final String SETUP_TASK = "setup"
    static final String RC_TASK = "rc"
    static final String TEST_TASK = "test"
    static final String GROUP = "Wooga"

    @Override
    void apply(Project project) {
        def tasks = project.tasks

        applyNebularRelease(project)
        applyVisteg(project)
        applyWoogaPlugins(project)

        Configuration archives = project.configurations.maybeCreate(ARCHIVES_CONFIGURATION)
        archives.extendsFrom(project.configurations.getByName(PaketBasePlugin.PAKET_CONFIGURATION))
        def unityPack = tasks.create(name: UNTIY_PACK_TASK, type: Copy, group: GROUP) {
            from(archives) {
                include '**/*.unitypackage'
            }
            into "${project.buildDir}/outputs"
        }

        ProjectType type = new ProjectType(project)

        if (type.isRootProject) {
            def setup = tasks.create(name: SETUP_TASK, dependsOn: PaketGetPlugin.INSTALL_TASK_NAME, group: GROUP, description: 'run project setup')

            //hook up into lifecycle
            def checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
            def assembleTask = tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
            def buildTask = tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME)
            buildTask.dependsOn setup

            tasks.withType(PaketPack, new org.gradle.api.Action<PaketPack>() {
                @Override
                void execute(PaketPack paketPack) {
                    paketPack.dependsOn setup
                }
            })

            def releaseTask = tasks.getByName('release')
            def postReleaseTask = tasks.getByName(nebula.plugin.release.ReleasePlugin.POST_RELEASE_TASK_NAME)
            def publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
            GithubPublish githubPublishTask = (GithubPublish) tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)
            def candiateTask = tasks.getByName(nebula.plugin.release.ReleasePlugin.CANDIDATE_TASK_NAME)
            def rcTask = tasks.create(name: RC_TASK, group: nebula.plugin.release.ReleasePlugin.GROUP, dependsOn: candiateTask)
            if (!tasks.hasProperty(TEST_TASK)) {
                def test = tasks.create(name: TEST_TASK, dependsOn: setup)
                checkTask.dependsOn test
            }

            assembleTask.dependsOn unityPack
            releaseTask.dependsOn assembleTask
            postReleaseTask.dependsOn publishTask
            publishTask.mustRunAfter postReleaseTask

            githubPublishTask.onlyIf(new Spec<Task>() {
                @Override
                boolean isSatisfiedBy(Task task) {
                    Boolean satisfied = project.status == 'candidate' || project.status == 'release'
                    println("'release.stage' check satisfied $satisfied")
                    return satisfied
                }
            })

            githubPublishTask.from(archives)
            githubPublishTask.dependsOn archives
            githubPublishTask.tagName = "v${project.version}"
            githubPublishTask.setReleaseName(project.version.toString())
        }

        configureVersionCode(project)
        configureUnityPackageIfPresent(project)
        configurePaketPackageIfPresent(project)
    }

    def configureVersionCode(Project project) {
        def version = project.version.toString()
        def versionMatch = version =~ /(\d+)\.(\d+)\.(\d+)/
        String versionMajor = versionMatch[0][1]
        String versionMinor = versionMatch[0][2]
        String versionPatch = versionMatch[0][3]
        int versionCode = versionMajor.toInteger() * 10000 + versionMinor.toInteger() * 100 + versionPatch.toInteger()

        project.rootProject.allprojects {
            ext.versionCode = versionCode
        }
    }

    private configureUnityPackageIfPresent(Project project) {
        DependencyHandler dependencies = project.dependencies
        project.subprojects { sub ->
            sub.afterEvaluate {
                logger.info("check subproject {} for unity plugin", sub.name)
                if (sub.plugins.hasPlugin("net.wooga.unity")) {
                    logger.info("subproject {} has unity plugin.", sub.name)
                    logger.info("configure dependencies {}", sub.path)
                    dependencies.add(ARCHIVES_CONFIGURATION, dependencies.project(path: sub.path, configuration: "unitypackage"))
                }
            }
        }
    }

    private configurePaketPackageIfPresent(Project project) {
        project.afterEvaluate {
            Configuration paketConfiguration = project.configurations.getByName(PaketBasePlugin.PAKET_CONFIGURATION)
            paketConfiguration.allArtifacts.each {
                project.dependencies.add(ARCHIVES_CONFIGURATION, project.files(it.file).builtBy(it.buildDependencies))
            }
        }
    }

    private void applyVisteg(Project project) {
        project.pluginManager.apply(VisTaskExecGraphPlugin)
        VisTegPluginExtension visTegPluginExtension = project.extensions.findByType(VisTegPluginExtension)
        visTegPluginExtension.with {
            enabled = true
            colouredNodes = true
            colouredEdges = true
            destination = 'build/reports/visteg.dot'
            exporter = 'dot'
            colorscheme = 'spectral11'
            nodeShape = 'box'
            startNodeShape = 'hexagon'
            endNodeShape = 'doubleoctagon'
        }
    }

    private void applyNebularRelease(Project project) {
        project.pluginManager.apply(nebula.plugin.release.ReleasePlugin)
        ProjectType type = new ProjectType(project)

        if (type.isRootProject) {
            ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)

            releaseExtension.with {
                versionStrategy(WoogaStrategies.SNAPSHOT)
                versionStrategy(WoogaStrategies.PRE_RELEASE)
                defaultVersionStrategy = NetflixOssStrategies.DEVELOPMENT
            }
        }
    }

    private void applyWoogaPlugins(Project project) {
        project.pluginManager.apply(GithubPublishPlugin)
        project.pluginManager.apply(PaketPlugin)
        project.pluginManager.apply(PaketUnityPlugin)
    }
}
