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
import org.kohsuke.github.GHLabel
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
        hub.fullName >> "wooga/TestRepo"

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

        if (changeSet) {
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
        pr.title >> "Pullrequest ${number}"
        pr.issueUrl >> new URL("https://github.com/${hub.fullName}/pull/${number}")
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
        hub.getPullRequest(2) >> { throw new FileNotFoundException("missing") }
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

        and: "mocked pull requests"
        hub.getPullRequest(1) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(2) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(3) >> { throw new FileNotFoundException("missing") }

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

    def "prints commit log when pull requests have no changeset list"() {
        given: "a git log with pull requests commits"

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
        hub.getPullRequest(1) >> mockPullRequest(1, false)
        hub.getPullRequest(2) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(3) >> mockPullRequest(3, false)

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

    def "creates full release notes for specific version"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateFullReleaseNotes(version, packageId)

        then:
        notes == ("""
        # $currentVersion - $date

        https://github.com/wooga/TestRepo/releases/tag/v$currentVersion

        Major Changes
        =============
        
        Pullrequest 2
        -------------
        
        ### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        ### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        Additional Changes in $currentVersion
        ===========================

        * [#1](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1
        * [#3](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3

        How to install
        ==============

        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()

        where:
        packageId = "Wooga.Test"
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes with multiple major changes for specific version"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#4)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        def majorPR2 = mockPullRequest(4)
        majorPR2.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)
        hub.getPullRequest(4) >> majorPR2

        when:
        def notes = releaseNoteGenerator.generateFullReleaseNotes(version, packageId)

        then:
        notes == ("""
        # $currentVersion - $date

        https://github.com/wooga/TestRepo/releases/tag/v$currentVersion

        Major Changes
        =============
        
        Pullrequest 2
        -------------
        
        ### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        ### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        Pullrequest 4
        -------------
        
        ### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        ### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        Additional Changes in $currentVersion
        ===========================

        * [#1](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1
        * [#3](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3

        How to install
        ==============

        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()

        where:
        packageId = "Wooga.Test"
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes with multiple major changes and no additional changes for specific version"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#4)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        def majorPR2 = mockPullRequest(4)
        majorPR2.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)
        hub.getPullRequest(4) >> majorPR2

        when:
        def notes = releaseNoteGenerator.generateFullReleaseNotes(version, packageId)

        then:
        notes == ("""
        # $currentVersion - $date

        https://github.com/wooga/TestRepo/releases/tag/v$currentVersion

        Major Changes
        =============
        
        Pullrequest 2
        -------------
        
        ### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        ### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        Pullrequest 4
        -------------
        
        ### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        ### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        How to install
        ==============

        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()

        where:
        packageId = "Wooga.Test"
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes for specific version without major versions when missing"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateFullReleaseNotes(version, packageId)

        then:
        notes == ("""
        # $currentVersion - $date

        https://github.com/wooga/TestRepo/releases/tag/v$currentVersion

        Changes in $currentVersion
        ================

        * [#1](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1
        * [#2](https://github.com/wooga/TestRepo/pull/2) Pullrequest 2
        * [#3](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3

        How to install
        ==============

        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()

        where:
        packageId = "Wooga.Test"
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes for specific version with list of commits when no pull requests are available"() {
        given: "a git log with pull requests commits"

        git.tag.add(name: 'v1.0.0')
        git.commit(message: 'initial commit')
        git.commit(message: 'Change this')
        git.commit(message: 'Add cool stuff')
        git.commit(message: 'Fix ugly bug')
        git.commit(message: 'Update that')
        git.commit(message: 'ugly message')

        and: "mocked pull requests"

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateFullReleaseNotes(version, packageId)

        then:
        notes == ("""
        # $currentVersion - $date

        https://github.com/wooga/TestRepo/releases/tag/v$currentVersion

        Changes in $currentVersion
        ================

        * Ugly message
        * Update that
        * Fix ugly bug
        * Add cool stuff
        * Change this
        * Initial commit

        How to install
        ==============

        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + ReleaseNotesGenerator.ICON_IDS).trim()

        where:
        packageId = "Wooga.Test"
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    // ALL Versions

    def "creates full release notes for all versions"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.tag.add(name: "v1.0.0")
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')
        git.tag.add(name: "v1.1.0")

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateFullReleaseNotes(versions, packageId)

        then:
        notes == releaseNoteGenerator.generateFullReleaseNotes(versions[1], packageId, false) + "\n\n" + releaseNoteGenerator.generateFullReleaseNotes(versions[0], packageId)

        where:
        packageId = "Wooga.Test"
        date = new Date().format("dd MMMM yyyy")
        versions = [
                new ReleaseVersion("1.0.0", null, false),
                new ReleaseVersion("1.1.0", "1.0.0", false)
        ]
    }
}
