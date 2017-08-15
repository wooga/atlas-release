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
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.service.TagService
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRepository
import spock.lang.Specification

class ReleaseNotesGeneratorTest extends Specification {

    Grgit git
    TagService tag
    GHRepository hub
    ReleaseNotesGenerator releaseNoteGenerator

    def setup() {
        git = Grgit.init(dir: File.createTempDir())
        git.commit(message: 'initial commit')

        hub = Mock(GHRepository)

        releaseNoteGenerator = new ReleaseNotesGenerator(git, hub)
    }

    def mockPullRequest(int number, Boolean changeSet = true) {
        def bodyOut = new StringBuilder()

        bodyOut << """
        ## Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        """.stripIndent()

        if(changeSet) {
            bodyOut << """
            ## Changes
            * ![ADD] some stuff
            * ![REMOVE] some stuff
            * ![FIX] some stuff
            
            Yada Yada Yada Yada Yada
            Yada Yada Yada Yada Yada
            Yada Yada Yada Yada Yada
            """.stripIndent()
        }

        def pr = Mock(GHPullRequest)
        pr.body >> bodyOut.toString()
        pr.number >> number
        return pr
    }

    def "creates release notes from log and pull requests changes"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version)

        then:
        notes == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#2]
        * ![REMOVE] some stuff [#2]
        * ![FIX] some stuff [#2]
        """.stripIndent().stripMargin() + ReleaseNotesGenerator.ICON_IDS).trim()
    }

    def "creates release notes with full log when previousVersion tag can't be found"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version)

        then:
        notes == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#2]
        * ![REMOVE] some stuff [#2]
        * ![FIX] some stuff [#2]
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()
    }

    def "skips pull requests it can't find"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version)

        then:
        notes == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()
    }

    def "creates initial change message when previosVersion is not set"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", null, false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version)

        then:
        notes == ("""
        * ![NEW] Initial Release
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()
    }

    def "prints commit log when pull requests are empty"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version)

        then:
        notes == ("""
        * commit
        * commit (#3)
        * commit (#2)
        * commit
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()
    }

    def "creates empty notes when pull requests have no changeset list"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1, false)
        hub.getPullRequest(3) >> mockPullRequest(3, false)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version)

        then:
        notes == ""
    }
}
