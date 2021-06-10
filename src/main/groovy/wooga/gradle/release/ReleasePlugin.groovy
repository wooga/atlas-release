/*
 * Copyright 2021 Wooga GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package wooga.gradle.release

import com.wooga.github.changelog.GeneratorStrategy
import org.ajoberstar.grgit.gradle.GrgitPlugin
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
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.language.base.plugins.LifecycleBasePlugin
import wooga.gradle.github.GithubPlugin
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.github.publish.tasks.GithubPublish
import wooga.gradle.githubReleaseNotes.tasks.GenerateReleaseNotes
import wooga.gradle.paket.PaketPlugin
import wooga.gradle.paket.base.PaketBasePlugin
import wooga.gradle.paket.base.PaketPluginExtension
import wooga.gradle.paket.get.PaketGetPlugin
import wooga.gradle.paket.pack.tasks.PaketPack
import wooga.gradle.paket.unity.PaketUnityPlugin
import wooga.gradle.release.internal.DefaultAtlasReleasePluginExtension
import wooga.gradle.release.releasenotes.ReleaseNotesBodyStrategy
import wooga.gradle.release.utils.ProjectStatusTaskSpec
import wooga.gradle.githubReleaseNotes.GithubReleaseNotesPlugin

//import wooga.gradle.releaseNotesGenerator.ReleaseNotesGeneratorPlugin
//import wooga.gradle.releaseNotesGenerator.utils.ReleaseBodyStrategy
import wooga.gradle.version.VersionPlugin
import wooga.gradle.version.VersionPluginExtension
import wooga.gradle.version.VersionScheme

/**
 * A Wooga internal plugin to develop and publish Unity library packages.
 * This plugin is very opinionated. It acts as an aggregation plugin which applies a series of helper plugins and
 * configures them according to the wooga sdk {@code wdk} standard.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     plugins {*         id "net.wooga.release" version "0.15.1"
 *}*}
 * </pre>
 */
class ReleasePlugin implements Plugin<Project> {

    private static Logger logger = Logging.getLogger(ReleasePlugin)

    static final String GROUP = "Wooga"
    static final String EXTENSION_NAME = "atlasRelease"

    static final String CLEAN_META_FILES_TASK = "cleanMetaFiles"
    static final String SETUP_TASK = "setup"
    static final String RC_TASK = "rc"
    static final String FINAL_TASK = "final"
    static final String SNAPSHOT_TASK = "snapshot"
    static final String TEST_TASK = "test"
    static final String RELEASE_NOTES_BODY_TASK = "generateReleaseNotesBody"

    static final String ARCHIVES_CONFIGURATION = "archives"
    static final String VERSION_SCHEME_DEFAULT = 'semver'
    static final String VERSION_SCHEME_SEMVER_1 = 'semver'
    static final String VERSION_SCHEME_SEMVER_2 = 'semver2'

    @Override
    void apply(Project project) {

        if (project == project.rootProject) {
            def extension = project.extensions.create(EXTENSION_NAME, DefaultAtlasReleasePluginExtension)

            configureDefaultMetaCleanPattern(extension)
            applyVersionPlugin(project)
            applyWoogaPlugins(project)

            configureArchiveConfiguration(project)
            configureSetupTask(project)
            configureReleaseLifecycle(project)
            configurePaketPackTasks(project)
            project.pluginManager.with {
                apply(GrgitPlugin)
                apply(GithubReleaseNotesPlugin)
            }
            configureReleaseNotesTask(project)
            configureGithubPublishTask(project)

            // TODO: Remove?
            configureVersionCode(project)

            configureUnityPackageIfPresent(project, extension)
            configureSetupTaskIfUnityPluginPresent(project)
            configurePaketConfigurationArtifacts(project)
        } else {
            project.rootProject.pluginManager.apply(this.class)
        }


    }

    private static void configureReleaseLifecycle(final Project project) {
        def tasks = project.tasks
        def setup = tasks.maybeCreate(SETUP_TASK)

        // TODO: Add custom tasks previously provided by the nebula release plugin
        Task finalTask = project.tasks.create(FINAL_TASK)
        Task rcTask = project.tasks.create(RC_TASK)
        Task snapshotTask = project.tasks.create(SNAPSHOT_TASK)

        //hook up into lifecycle
        def checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        def assembleTask = tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
        def buildTask = tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME)

        def publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        def testTask = tasks.maybeCreate(TEST_TASK)

        [finalTask, rcTask, snapshotTask].each {
            it.dependsOn publishTask
        }

        testTask.dependsOn setup
        checkTask.dependsOn(testTask)
        buildTask.dependsOn setup
        publishTask.dependsOn assembleTask
    }

    private static void configureReleaseNotesTask(Project project) {

        // Body
        addReleaseNotesTask(project, RELEASE_NOTES_BODY_TASK, new ReleaseNotesBodyStrategy(), { t ->
            t.output.set(new File("${project.buildDir}/outputs/release-notes.md"))
        })
        // TODO: Implement task for generating/appending release notes to file on the repository;
    }

    private static void addReleaseNotesTask(Project project,
                                            String name,
                                            GeneratorStrategy strategy,
                                            Action<? super GenerateReleaseNotes> action) {

        def provider = project.tasks.register(name, GenerateReleaseNotes)
        provider.configure { task ->

            // Release notes for GitHub RELEASE
            def versionExt = project.extensions.findByType(VersionPluginExtension)
            if (versionExt) {
                task.from.set(versionExt.version.map { version ->
                    if (version.previousVersion) {
                        return "v${version.previousVersion}"
                    } else {
                        return null
                    }
                })
                task.branch.set(project.extensions.grgit.branch.current.name as String)
                task.strategy.set(strategy)
                action.execute(task)
            }
        }
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
     * It sets also the release notes with the help of.
     *
     * @param project
     * @see GithubPublishPlugin
     */
    protected static void configureGithubPublishTask(Project project) {
        def archives = project.configurations.maybeCreate(ARCHIVES_CONFIGURATION)

        TaskContainer tasks = project.tasks
        GenerateReleaseNotes releaseNotesTask = tasks.getByName(RELEASE_NOTES_BODY_TASK) as GenerateReleaseNotes
        GithubPublish githubPublishTask = (GithubPublish) tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)
        githubPublishTask.onlyIf(new ProjectStatusTaskSpec('candidate', 'release'))
        githubPublishTask.from(archives)
        githubPublishTask.dependsOn(archives)
        githubPublishTask.tagName.set("v${project.version}")
        githubPublishTask.releaseName.set(project.version.toString())
        githubPublishTask.prerelease.set(project.provider { project.status != 'release' })

        // Write the release description using the release notes generated by
        // the release strategy
        githubPublishTask.body.set(releaseNotesTask.output.map { it.asFile.text })
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
     *}
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
     * Applies and configures {@code nebula.release} plugins.
     * <p>
     * Nebular release is the base plugin which we borrow logic from.
     * We take apply our own versioning strategy pattern to be compatible
     * with <a href="https://fsprojects.github.io/Paket/">Paket</a> and <a href="https://www.nuget.org/">Nuget</a>.
     *
     * @param project
     */
    protected static void applyVersionPlugin(Project project) {
        project.pluginManager.apply(VersionPlugin)
        VersionPluginExtension versionExtension = project.extensions.findByType(VersionPluginExtension)

        def versionScheme = project.properties.getOrDefault("version.scheme", VERSION_SCHEME_DEFAULT)

        versionExtension.with {
            switch (versionScheme) {
                case VERSION_SCHEME_SEMVER_2:
                    versionExtension.versionScheme(VersionScheme.semver2)
                    break
                case VERSION_SCHEME_SEMVER_1:
                default:
                    versionExtension.versionScheme(VersionScheme.semver)
                    break
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
