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
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.language.base.plugins.LifecycleBasePlugin
import wooga.gradle.github.GithubPlugin
import wooga.gradle.github.publish.GithubPublish
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.paket.PaketPlugin
import wooga.gradle.paket.base.PaketBasePlugin
import wooga.gradle.paket.base.PaketPluginExtension
import wooga.gradle.paket.get.PaketGetPlugin
import wooga.gradle.paket.pack.tasks.PaketPack
import wooga.gradle.paket.unity.PaketUnityPlugin
import wooga.gradle.release.internal.DefaultAtlasReleasePluginExtension
import wooga.gradle.release.utils.ProjectStatusTaskSpec
import wooga.gradle.release.utils.WoogaStrategies
import wooga.gradle.releaseNotesGenerator.ReleaseNotesGeneratorPlugin
import wooga.gradle.releaseNotesGenerator.utils.ReleaseBodyStrategy

/**
 * A Wooga internal plugin to develop and publish Unity library packages.
 * This plugin is very opinionated. It acts as an aggregation plugin which applies a series of helper plugins and
 * configures them according to the wooga sdk {@code wdk} standard.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     plugins {
 *         id "net.wooga.release" version "0.15.1"
 *     }
 * }
 * </pre>
 */
class ReleasePlugin implements Plugin<Project> {

    private static Logger logger = Logging.getLogger(ReleasePlugin)

    static final String ARCHIVES_CONFIGURATION = "archives"
    static final String UNTIY_PACK_TASK = "unityPack"
    static final String CLEAN_META_FILES_TASK = "cleanMetaFiles"
    static final String SETUP_TASK = "setup"
    static final String RC_TASK = "rc"
    static final String TEST_TASK = "test"
    static final String GROUP = "Wooga"
    static final String EXTENSION_NAME = "atlasRelease"

    @Override
    void apply(Project project) {
        def extension = project.extensions.create(EXTENSION_NAME, DefaultAtlasReleasePluginExtension)

        configureDefaultMetaCleanPattern(extension)

        applyNebularRelease(project)
        applyRCtoCandidateAlias(project)
        applyVisteg(project)
        applyWoogaPlugins(project)

        configureArchiveConfiguration(project)
        createUnityPackTask(project)

        ProjectType type = new ProjectType(project)
        if (type.isRootProject) {
            configureSetupTask(project)
            configureReleaseLifecycle(project)
            configureGithubPublishTask(project)
            configurePaketPackTasks(project)
            project.pluginManager.apply(ReleaseNotesGeneratorPlugin)
        }

        configureVersionCode(project)
        configureUnityPackageIfPresent(project, extension)
        configureSetupTaskIfUnityPluginPresent(project)
        configurePaketConfigurationArtifacts(project)
    }

    private static void configureReleaseLifecycle(final Project project) {
        def tasks = project.tasks
        def setup = tasks.maybeCreate(SETUP_TASK)

        //hook up into lifecycle
        def checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        def assembleTask = tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
        def buildTask = tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME)
        def releaseTask = tasks.getByName('release')
        def postReleaseTask = tasks.getByName(nebula.plugin.release.ReleasePlugin.POST_RELEASE_TASK_NAME)
        def publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        def testTask = tasks.maybeCreate(TEST_TASK)
        def unityPack = tasks.getByName(UNTIY_PACK_TASK)

        testTask.dependsOn setup
        checkTask.dependsOn(testTask)
        buildTask.dependsOn setup
        assembleTask.dependsOn unityPack
        releaseTask.dependsOn assembleTask
        postReleaseTask.dependsOn publishTask
        publishTask.mustRunAfter releaseTask
    }

    private static Task configureSetupTask(final Project project) {
        project.tasks.maybeCreate(SETUP_TASK).with {
            PaketPluginExtension paketPluginExtension = project.extensions.getByType(PaketPluginExtension);
            def installTask = paketPluginExtension.getPaketLockFile().exists() ? PaketGetPlugin.RESTORE_TASK_NAME : PaketGetPlugin.INSTALL_TASK_NAME
            dependsOn(installTask)
            group = GROUP
            setDescription("run project setup")
        }
    }

    /**
     * Configures all tasks of type {@link PaketPack}.
     * <p>
     * It sets the paket version on each task because version 0.8.0 of {@code net.wooga.paket-pack} changed the
     * default behavior for picking the package version.
     *
     * @param project
     * @link wooga.gradle.paket.pack.PaketPackPlugin
     */
    protected static void configurePaketPackTasks(Project project) {
        def setup = project.tasks.maybeCreate(SETUP_TASK)
        project.tasks.withType(PaketPack, new Action<PaketPack>() {
            @Override
            void execute(PaketPack paketPack) {
                /**
                 * Sets project version to all {@code PaketPack} tasks.
                 * Version 0.8.0 changed the default behavior for picking the package version.
                 * This is just a security measure.
                 *
                 * */
                paketPack.version = { project.version }
                paketPack.dependsOn setup
            }
        })
    }

    /**
     * Configures the {@code GithubPublish} task.
     * <p>
     * The method fetches the {@code githubPublish} task and sets up the artifacts to publish.
     * Configures the tasks based on the release {@code stage} ({@code candidate} builds == {@code prerelease}).
     * It sets also the release notes with the help of {@link ReleaseBodyStrategy}.
     *
     * @param project
     * @see GithubPublishPlugin
     * @see ReleaseNotesGeneratorPlugin
     */
    protected static void configureGithubPublishTask(Project project) {
        def tasks = project.tasks
        def archives = project.configurations.maybeCreate(ARCHIVES_CONFIGURATION)
        def releaseExtension = project.extensions.findByType(ReleasePluginExtension)

        def githubPublishTask = tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME) as GithubPublish
        githubPublishTask.onlyIf(new ProjectStatusTaskSpec('candidate', 'release'))
        githubPublishTask.from(archives)
        githubPublishTask.dependsOn archives
        githubPublishTask.tagName = "v${project.version}"
        githubPublishTask.setReleaseName(project.version.toString())
        githubPublishTask.setPrerelease({ project.status != 'release' })

        //infer the ReleaseVersion in the private class DelayedVersion to be able to access the `inferredVersion` property
        //the release plugin sets this object as version to all projects
        project.version.toString()
        githubPublishTask.body(new ReleaseBodyStrategy(project.version.inferredVersion as ReleaseVersion, releaseExtension.grgit))
    }

    /**
     * Creates and Configures the projects {@code archive} configuration.
     * @param project
     * @return
     */
    protected static Configuration configureArchiveConfiguration(Project project) {
        Configuration archives = project.configurations.maybeCreate(ARCHIVES_CONFIGURATION)
        archives.extendsFrom(project.configurations.getByName(PaketBasePlugin.PAKET_CONFIGURATION))
    }

    /**
     * Creates Unity Pack task.
     * <p>
     * Creates a {@link Copy} task which will copy all {@code .unitypackage} artifacts into the output directory.
     *
     * @param project
     */
    protected static void createUnityPackTask(Project project) {
        Configuration archives = project.configurations.maybeCreate(ARCHIVES_CONFIGURATION)
        project.tasks.create(UNTIY_PACK_TASK, Copy).with {
            group = GROUP
            from(archives) {
                include '**/*.unitypackage'
            }
            into "${project.buildDir}/outputs"
        }
    }

    /**
     * Configures default clean pattern for the to be exported Unity nuget packages.
     * <p>
     * By default we need to keep the {@code .meta} files for {@code .dll} and {@code .so} files.
     * This value can be reconfigured at a later time.
     * @param extension
     */
    protected static void configureDefaultMetaCleanPattern(AtlasReleasePluginExtension extension) {
        extension.metaCleanPattern(new Action<PatternFilterable>() {
            @Override
            void execute(PatternFilterable pattern) {
                pattern.with {
                    include("**/*.meta")
                    exclude("**/*.dll.meta")
                    exclude("**/*.so.meta")
                }
            }
        })
    }

    /**
     * The {@code NebularRelease} plugin will provide slightly better error messages when using the official
     * cli tasks (final, candidate, snapshot, ...). Because of internal naming reasons it makes sense for us to use
     * {@code rc} instead of {@code candidate}. All other resources are named with the
     * pattern (final, rc and snapshot). I used a custom task with the name {@code rc} which depends on
     * {@code candidate} but this will fall through the error check in {@code NebularRelease}. So instead we
     * now change the cli tasklist on the fly. If we find the {@code rc} taskname in the cli tasklist we remove it
     * and add {@code candidate} instead.
     * @param project
     * @return
     */
    protected static void applyRCtoCandidateAlias(Project project) {
        List<String> cliTasks = project.rootProject.gradle.startParameter.taskNames
        if (cliTasks.contains(RC_TASK)) {
            cliTasks.remove(RC_TASK)
            cliTasks.add(nebula.plugin.release.ReleasePlugin.CANDIDATE_TASK_NAME)
            project.rootProject.gradle.startParameter.setTaskNames(cliTasks)
        }
    }

    /**
     * Creates a versioncode string used by Android sub-projects.
     * <p>
     * Android applications or libraries need a version string and version code value.
     * This method creates the versioncode based on the current version and sets it as an external property to
     * all sub-projects.
     * <p>
     * The version will be splitted into {@code Major},{@code Minor} and {@code Patch} components
     * and multiplied by an offset value. {@code Minor} and {@code Patch} must be lower < 100 otherwise the versionCode
     * is no longer readable.
     * <ul>
     *     <li> {@code} Major * 10000
     *     <li> {@code} Minor * 100
     *     <li> {@code} Patch
     * These values will be added together to form a single integer value.
     * <p>
     * Example:
     * <pre>
     * {@code
     *      String version = "3.1.0"
     *      Integer versionCode = 301000
     *
     *      String version = "1.24.33"
     *      Integer versionCode = 12433
     *
     *      //version overflow
     *      String version = "22.2245.455"
     *      Integer versionCode = 444955
     * }
     * </pre>
     *
     * @param project
     */
    protected static void configureVersionCode(Project project) {
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

    /**
     * Configures {@code net.wooga.unity} plugin on subprojects if available.
     * <p>
     * Iterates through all sub-projects and looks for the {@code net.wooga.unity} plugin.
     * When applied, sets a project dependency to the {@code unitypackage} configuration and adds
     * a clean metadata task dependency to {@code paketPack}.
     *
     * @param project
     * @param extension
     */
    protected static void configureUnityPackageIfPresent(Project project, AtlasReleasePluginExtension extension) {
        DependencyHandler dependencies = project.dependencies
        project.subprojects { sub ->
            sub.pluginManager.withPlugin("net.wooga.unity", new Action<AppliedPlugin>() {
                @Override
                void execute(AppliedPlugin appliedPlugin) {
                    logger.info("subproject {} has unity plugin.", sub.name)
                    logger.info("configure dependencies {}", sub.path)
                    dependencies.add(ARCHIVES_CONFIGURATION, dependencies.project(path: sub.path, configuration: "unitypackage"))
                    logger.info("create cleanMetaFiles task")

                    def files = project.fileTree(new File(sub.projectDir, 'Assets/'))

                    files.include(extension.metaCleanPattern.includes)
                    files.exclude(extension.metaCleanPattern.excludes)

                    def cleanTask = sub.tasks.create(CLEAN_META_FILES_TASK, Delete).with {
                        delete(files)
                    }

                    project.tasks.withType(PaketPack, new Action<PaketPack>() {
                        @Override
                        void execute(PaketPack paketPack) {
                            paketPack.dependsOn cleanTask
                        }
                    })
                }
            })
        }
    }

    private static configureSetupTaskIfUnityPluginPresent(Project project) {
        def rootSetupTask = project.rootProject.tasks[SETUP_TASK]
        project.subprojects { sub ->
            sub.afterEvaluate {
                logger.info("check subproject {} for WDK unity plugin", sub.name)
                if (sub.plugins.hasPlugin("net.wooga.wdk-unity")) {
                    logger.info("subproject {} has WDK unity plugin.", sub.name)
                    logger.info("configure dependencies {}", sub.path)
                    logger.info("create cleanMetaFiles task")
                    def setupTask = sub.tasks[SETUP_TASK]
                    rootSetupTask.dependsOn(setupTask)
                }
            }
        }
    }

    private static configurePaketConfigurationArtifacts(Project project) {
        project.afterEvaluate {
            Configuration paketConfiguration = project.configurations.getByName(PaketBasePlugin.PAKET_CONFIGURATION)
            paketConfiguration.allArtifacts.each {
                project.dependencies.add(ARCHIVES_CONFIGURATION, project.files(it.file).builtBy(it.buildDependencies))
            }
        }
    }

    /**
     * Applies {@code cz.malohlava.visteg}
     *
     * @param project
     */
    protected static void applyVisteg(Project project) {
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

    /**
     * Applies and configures {@code nebula.release} plugins.
     * <p>
     * Nebular release is the base plugin which we borrow logic from.
     * We take apply our own versioning strategy pattern to be compatible
     * with <a href="https://fsprojects.github.io/Paket/">Paket</a> and <a href="https://www.nuget.org/">Nuget</a>.
     *
     * @param project
     * @see WoogaStrategies
     */
    protected static void applyNebularRelease(Project project) {
        project.pluginManager.apply(nebula.plugin.release.ReleasePlugin)
        ProjectType type = new ProjectType(project)

        if (type.isRootProject) {
            ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)

            releaseExtension.with {
                releaseExtension.versionStrategy(WoogaStrategies.SNAPSHOT)
                releaseExtension.versionStrategy(WoogaStrategies.DEVELOPMENT)
                releaseExtension.versionStrategy(WoogaStrategies.PRE_RELEASE)
                releaseExtension.versionStrategy(WoogaStrategies.FINAL)
                releaseExtension.defaultVersionStrategy = WoogaStrategies.DEVELOPMENT
            }

            replaceReleaseTask(project, releaseExtension)
        }
    }

    /**
     * Replaces the {@code release} task from {@link BaseReleasePlugin}.
     * <p>
     * On CI systems that check out the repo with a temp branch name could lead to
     * unwanted side effects.
     *
     * @param project
     * @param extension
     */
    protected static void replaceReleaseTask(final Project project, final ReleasePluginExtension extension) {
        def releaseTask = project.tasks.getByName('release')
        releaseTask.deleteAllActions()
        releaseTask.doLast {
            // force version inference if it hasn't happened already
            project.version.toString()

            ext.grgit = extension.grgit
            ext.toPush = []

            ext.tagName = extension.tagStrategy.maybeCreateTag(grgit, project.version.inferredVersion)
            if (tagName) {
                toPush << tagName
            }

            if (toPush) {
                logger.warn('Pushing changes in {} to {}', toPush, extension.remote)
                grgit.push(remote: extension.remote, refsOrSpecs: toPush)
            } else {
                logger.warn('Nothing to push.')
            }
        }
    }

    /**
     * Applies Wooga plugins.
     * <p>
     * <ul>
     *     <li>net.wooga.github
     *     <li>net.wooga.paket
     *     <li>net.wooga.paket-unity
     *
     * @param project
     */
    protected static void applyWoogaPlugins(Project project) {
        project.pluginManager.apply(GithubPlugin)
        project.pluginManager.apply(PaketPlugin)
        project.pluginManager.apply(PaketUnityPlugin)
    }
}
