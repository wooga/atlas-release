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
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRepository

/**
 * A generator class to create release notes from git log and pull request bodies.
 */
class ReleaseNotesGenerator {

    private class PullRequestChange {
        String category
        String text
        int number

    }

    private class ReleaseNoteBody {
        List<Commit> logs
        List<PullRequestChange> pullrequestChanges
        boolean hasPreviousVersion
    }

    public static final String INITAL_RELEASE_MSG = "* ![NEW] Initial Release\n"
    public static final String LABEL_MAJOR_CHANGE = "Major Change"

    public static final String DATE_FORMAT = "dd MMMM yyyy"
    public static final String H1_FORMAT = "="
    public static final String H2_Format = "-"
    public static final String NEW_LINE = System.lineSeparator()

    public static final String ICON_IDS = """
    <!-- START icon Id's -->
        
    [NEW]:http://resources.atlas.wooga.com/icons/icon_new.svg "New"
    [ADD]:http://resources.atlas.wooga.com/icons/icon_add.svg "Add"
    [IMPROVE]:http://resources.atlas.wooga.com/icons/icon_improve.svg "IMPROVE"
    [CHANGE]:http://resources.atlas.wooga.com/icons/icon_change.svg "Change"
    [FIX]:http://resources.atlas.wooga.com/icons/icon_fix.svg "Fix"
    [UPDATE]:http://resources.atlas.wooga.com/icons/icon_update.svg "Update"
    
    [BREAK]:http://resources.atlas.wooga.com/icons/icon_break.svg "Remove"
    [REMOVE]:http://resources.atlas.wooga.com/icons/icon_remove.svg "Remove"
    [IOS]:http://resources.atlas.wooga.com/icons/icon_iOS.svg "iOS"
    [ANDROID]:http://resources.atlas.wooga.com/icons/icon_android.svg "Android"
    [WEBGL]:http://resources.atlas.wooga.com/icons/icon_webGL.svg "Web:GL"
    
    <!-- END icon Id's -->
    """.stripIndent()

    private Grgit git
    private GHRepository hub

    ReleaseNotesGenerator(Grgit git, GHRepository hub) {
        this.git = git
        this.hub = hub
    }

    /**
     * Generates the full release notes for all released versions of this repo
     *
     * @return a <code>String</code> containing the generated release notes
     */
    String generateFullReleaseNotes(List<ReleaseVersion> versions, String packageId) {
        StringBuilder builder = new StringBuilder()
        versions = versions.toSorted { a, b -> b.version <=> a.version }
        builder << versions.collect({ generateFullReleaseNotes(it, packageId, false) }).join("\n\n")
        builder << NEW_LINE
        builder << ICON_IDS
        builder.toString().trim()
    }

    /**
     * Generates the full release notes for a given version of this repo
     *
     * @param version The <code>ReleaseVersion</code> to create the release note for
     * @param appendIconIds A <boolean> value indicating if the icon id urls should be appended. Default: true
     * @return a <code>String</code> containing the generated release notes
     */
    String generateFullReleaseNotes(ReleaseVersion version, String packageId, Boolean appendIconIds = true) {
        StringBuilder builder = new StringBuilder()
        List<String> includes = createIncludes(version)
        List<String> excludes = createExcludes(version)

        List<Commit> log = git.log(includes: includes, excludes: excludes)
        List<GHPullRequest> pullRequests = fetchPullRequestsFromLog(log)

        builder << generateHeader(version)

        builder << generateGithubReleaseUrl(version)
        builder << NEW_LINE

        String majorChanges = generateMajorChanges(pullRequests)
        Boolean hasMajorChanges = !majorChanges.empty

        if (majorChanges) {
            builder << NEW_LINE
            builder << majorChanges
            builder << NEW_LINE
        }

        String changes = generateChanges(pullRequests, version, hasMajorChanges)
        Boolean hasChanges = !changes.empty

        if (hasChanges) {
            builder << NEW_LINE
            builder << generateChanges(pullRequests, version, hasMajorChanges)
            builder << NEW_LINE
        }

        if (!hasMajorChanges && !hasChanges) {
            def headline = "Changes in $version.version"
            builder << NEW_LINE
            builder << headline
            builder << NEW_LINE
            builder << "".padLeft(headline.size(), H1_FORMAT)
            builder << NEW_LINE
            builder << NEW_LINE
            builder << log.collect({ "* ${it.shortMessage.capitalize()}" }).join(NEW_LINE)
            builder << NEW_LINE
        }

        builder << NEW_LINE
        builder << generateHowToInstall(version, packageId)

        if (appendIconIds) {
            builder << NEW_LINE
            builder << ICON_IDS
        }

        builder.toString().trim()
    }

    protected String generateHeader(ReleaseVersion version) {
        """
        # ${version.version} - ${new Date().format(DATE_FORMAT)}

        """.stripIndent()
    }

    protected String generateGithubReleaseUrl(ReleaseVersion version) {
        "https://github.com/${hub.fullName}/releases/tag/v${version.version}"
    }

    protected String generateMajorChanges(List<GHPullRequest> pullRequests) {
        StringBuilder changes = new StringBuilder()
        def majorPullRequests = pullRequests.findAll { it.labels.any { it.name == LABEL_MAJOR_CHANGE } }
        majorPullRequests = majorPullRequests.toSorted { a, b -> a.number <=> b.number }
        majorPullRequests = majorPullRequests.collect { pullRequest ->
            StringBuilder stringBuilder = new StringBuilder()
            stringBuilder << pullRequest.title.trim()
            stringBuilder << NEW_LINE
            stringBuilder << "".padLeft(pullRequest.title.size(), H2_Format)
            stringBuilder << NEW_LINE
            stringBuilder << NEW_LINE
            stringBuilder << pullRequest.body.replaceAll("(?m)^#", "##").trim()
            stringBuilder.toString()
        }

        if (majorPullRequests.size() > 0) {
            changes << """
            Major Changes
            =============
            
            """.stripIndent()

            changes << majorPullRequests.join("\n\n")
        }

        changes.toString().trim()
    }

    protected String generateChanges(List<GHPullRequest> pullRequests, ReleaseVersion version, Boolean hasMajorChanges) {
        StringBuilder changes = new StringBuilder()
        pullRequests = pullRequests.findAll { it.labels.every { it.name != LABEL_MAJOR_CHANGE } }
        pullRequests = pullRequests.toSorted { a, b -> a.number <=> b.number }

        if (pullRequests.size() > 0) {
            def headline = "Changes in $version.version"
            if (hasMajorChanges) {
                headline = "Additional $headline"
            }

            changes << headline
            changes << NEW_LINE
            changes << "".padLeft(headline.size(), H1_FORMAT)
            changes << NEW_LINE
            changes << NEW_LINE
            changes << pullRequests.collect({ pr -> "* [#${pr.number}]($pr.issueUrl) ${pr.title}" }).join(NEW_LINE)
        }

        changes.toString()
    }

    protected String generateHowToInstall(ReleaseVersion version, String packageId) {
        String v = version.version
        def parts = v.split(/\./)

        """
        How to install
        ==============
        
        ```bash
        # latest stable
        nuget $packageId ~> ${parts[0]}
        # latest stable with only patch updates
        nuget $packageId ~> ${parts[0]}.${parts[1]}
        # latest build from master
        nuget $packageId ~> ${parts[0]} master
        # latest build with release candidates
        nuget $packageId ~> ${parts[0]} rc
        ```
        """.stripIndent().trim()
    }

    /**
     * Generates a release note body message with given <code>version</code>.
     * The generator will parse the git log from <code>HEAD</code> to previous version
     * and reads change lists from referenced pull requests. If no pull requests commits
     * can be found, it will list the git log.
     * @param version The <code>ReleaseVersion</code> to create the release note for
     * @return a <code>String</code> containing the generated release notes
     */
    String generateReleaseNotes(ReleaseVersion version) {
        generateReleaseNotes(version, "github_release")
    }

    String generateReleaseNotes(ReleaseVersion version, String template) {
        //generate the model
        ReleaseNoteBody noteBodyModel = new ReleaseNoteBody()

        List<String> includes = createIncludes(version)
        List<String> excludes = createExcludes(version)

        noteBodyModel.hasPreviousVersion = (version.previousVersion != null)
        noteBodyModel.logs = git.log(includes: includes, excludes: excludes)

        List<GHPullRequest> pullRequests = fetchPullRequestsFromLog(noteBodyModel.logs)
        def pChanges = new ArrayList<PullRequestChange>()
        pullRequests.inject(pChanges) { List<PullRequestChange> list, pr ->
            def changes = pr.body.readLines().findAll { it.trim().startsWith("* ![") }
            changes = changes.collect {
                PullRequestChange change = new PullRequestChange()
                change.number = pr.number

                def match = (it =~ /\!\[(.*?)\] (.*)/)
                change.category = match[0][1]
                change.text = match[0][2]
                change
            }

            list.addAll(changes)
            list
        }

        noteBodyModel.pullrequestChanges = pChanges

        //render model
        StringWriter writer = new StringWriter()

        MustacheFactory mf = new DefaultMustacheFactory()
        Mustache mustache = mf.compile("${template}.mustache")
        mustache.execute(writer, noteBodyModel).flush()
        writer.toString()

//
//        List<String> changeList = []
//        pullRequests.inject(changeList) { ch, pr ->
//            def changes = pr.body.readLines().findAll { it.trim().startsWith("* ![") }
//            changes = changes.collect { it + " [#${pr.number}]" }
//            ch << changes.join(NEW_LINE)
//        }
//
//        if (changeList.size() == 0) {
//            changeList << log.collect({ "* ${it.shortMessage}" }).join(NEW_LINE)
//        }
//
//        changeList.removeAll([""])
//
//        if (changeList.size() > 0) {
//            builder << changeList.join(NEW_LINE)
//            builder << NEW_LINE
//            builder << ICON_IDS
//        }
//
//        builder.toString().trim()
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
