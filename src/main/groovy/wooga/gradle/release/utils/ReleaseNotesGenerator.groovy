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
 */

package wooga.gradle.release.utils

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Tag
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHCommitQueryBuilder
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHQueryBuilder
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHTag

/**
 * A generator class to create release notes from git log and pull request bodies.
 */
class ReleaseNotesGenerator {
    private static final Logger logger = Logging.getLogger(ReleaseNotesGenerator)
    enum Template {
        githubRelease, releaseNotes
    }

    private Grgit git
    private GHRepository hub
    private String packageId

    ReleaseNotesGenerator(Grgit git, GHRepository hub) {
        this.git = git
        this.hub = hub
    }

    ReleaseNotesGenerator(Grgit git, GHRepository hub, String packageId) {
        this.git = git
        this.hub = hub
        this.packageId = packageId
    }

    /**
     * Generates a release note body message with given <code>version</code> and <code>Template</code>.
     * The generator will parse the git log from <code>HEAD</code> or current version to previous version
     * and reads change lists from referenced pull requests. If no pull requests commits
     * can be found, it will list the git log.
     * @param version The <code>ReleaseVersion</code> to create the release note for
     * @param template value to use to generate the release notes
     * @return a <code>String</code> containing the generated release notes
     */

    String generateReleaseNotes(ReleaseVersion version, Template template) {
        generateReleaseNotes([version], template)
    }

    /**
     * Generates a release note body message with given <code>versions</code> and <code>Template</code>.
     * The generator will parse the git log from <code>HEAD</code> or current version to previous version
     * and reads change lists from referenced pull requests. If no pull requests commits
     * can be found, it will list the git log.
     * @param versions A <code>List<ReleaseVersion></code> object to create the release notes for
     * @param template value to use to generate the release notes
     * @return a <code>String</code> containing the generated release notes
     */

    String generateReleaseNotes(List<ReleaseVersion> versions, Template template) {
        ReleaseNoteBodies model = new ReleaseNoteBodies(versions.collect { releaseNoteBodyFromVersion(it) })
        render(model, template)
    }

    protected String render(ReleaseNoteBodies noteBodyModel, Template template) {
        StringWriter writer = new StringWriter()
        MustacheFactory mf = new DefaultMustacheFactory()
        Mustache mustache = mf.compile("${template}.mustache")
        mustache.execute(writer, noteBodyModel).flush()
        writer.toString()
    }

    protected ReleaseNoteBody releaseNoteBodyFromVersion(ReleaseVersion version) {
        List<String> includes = createIncludes(version)
        List<String> excludes = createExcludes(version)

        logger.info("fetching logs includes ${includes} excludes ${excludes} for version ${version.version}")
        GHRelease release
        Date releaseDate = new Date()
        if (!includes.contains("HEAD")) {
            release = hub.listReleases().asList().find { it.name == version.version }
            if (release) {
                releaseDate = release.createdAt
            }
        }

        List<Commit> logs = git.log(includes: includes, excludes: excludes)
        List<GHPullRequest> pullRequests = fetchPullRequestsFromLog(logs)
        List<GHAsset> releaseAssets = (release == null) ? new ArrayList<GHAsset>() : release.assets

        ReleaseNoteBody noteBodyModel = new ReleaseNoteBody(version, releaseDate, packageId, hub, logs, pullRequests, releaseAssets)
        noteBodyModel
    }

    protected List<GHPullRequest> fetchPullRequestsFromLog(List<Commit> log) {
        String pattern = /#(\d+)/
        def prCommits = log.findAll { it.shortMessage =~ pattern }
        def prNumbers = prCommits.collect {
            def m = (it.shortMessage =~ pattern)
            m[0][1].toInteger()
        }
        def prs = prNumbers.collect {
            def pm
            try {
                pm = hub.getPullRequest(it)
            }
            finally {
                return pm
            }
        }
        prs.removeAll([null])
        prs
    }

    private List<String> createIncludes(ReleaseVersion version) {
        List<String> includes = []
        if (version.version) {
            String currentVersion = "v${version.version}^{commit}"
            if (tagExists(currentVersion)) {
                includes << currentVersion
            }
        }

        if (includes.empty) {
            includes << "HEAD"
        }

        includes
    }

    private List<String> createExcludes(ReleaseVersion version) {
        List<String> excludes = []
        if (version.previousVersion) {
            String previousVersion = "v${version.previousVersion}^{commit}"
            if (tagExists(previousVersion)) {
                excludes << previousVersion
            }
        }
        excludes
    }

    private boolean tagExists(String revStr) {
        try {
            git.resolve.toCommit(revStr)
            return true
        } catch (e) {
            return false
        }
    }
}
