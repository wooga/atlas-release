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

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRepository

/**
 * A generator class to create release notes from git log and pull request bodies.
 */
class ReleaseNotesGenerator {

    public static final String INITAL_RELEASE_MSG = "* ![NEW] Initial Release\n"
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
     * Generates a release note body message with given <code>version</code>.
     * The generator will parse the git log from <code>HEAD</code> to previous version
     * and reads change lists from referenced pull requests. If no pull requests commits
     * can be found, it will list the git log.
     * @param version The <code>ReleaseVersion</code> to create the release note for
     * @return a <code>String</code> containing the generated release notes
     */
    String generateReleaseNotes(ReleaseVersion version) {
        StringBuilder builder = new StringBuilder()
        List<String> includes = ['HEAD']
        List<String> excludes = createExcludes(version)

        if (!version.previousVersion) {
            builder << INITAL_RELEASE_MSG
        }

        List<Commit> log = git.log(includes: includes, excludes: excludes)
        List<GHPullRequest> pullRequests = fetchPullRequestsFromLog(log)

        List<String> changeList = []
        pullRequests.inject(changeList) { ch, pr ->
            def changes = pr.body.readLines().findAll { it.trim().startsWith("* ![") }
            changes = changes.collect { it + " [#${pr.number}]" }
            ch << changes.join("\n")
        }

        if (changeList.size() == 0) {
            changeList << log.collect({ "* ${it.shortMessage}" }).join("\n")
        }

        changeList.removeAll([""])

        if (changeList.size() > 0) {
            builder << changeList.join("\n")
            builder << "\n"
            builder << ICON_IDS
        }

        builder.toString().trim()
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
