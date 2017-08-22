package wooga.gradle.release

import org.ajoberstar.grgit.Grgit

class ReleaseNotesGenerationIntegrationSpec extends GithubIntegrationWithDefaultAuth {

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.1.0")
    }

    def "skips when no release is available"() {
        given: "a RELEASE_NOTES.md file"
        def releaseNotes = createFile("RELEASE_NOTES.md")
        releaseNotes << """
        ** FIRST RELEASE **
        """.stripIndent().trim()

        when:
        def result = runTasksSuccessfully("appendReleaseNotes")

        then:
        result.standardOutput.contains("no releases available")
        releaseNotes.text == "** FIRST RELEASE **"
    }

    def "skips when release is already in release notes"() {
        given: "a RELEASE_NOTES.md file"
        def releaseNotes = createFile("RELEASE_NOTES.md")
        releaseNotes << """
        # 0.1.0 - 01 June 2012 #
        """.stripIndent().trim()

        and: "one release"
        createRelease("0.1.0")

        when:
        def result = runTasksSuccessfully("appendReleaseNotes")

        then:
        result.standardOutput.contains("release already contained in release notes")
        releaseNotes.text == "# 0.1.0 - 01 June 2012 #"
    }


    def "append release notes with single release"() {
        given: "a RELEASE_NOTES.md file"
        def releaseNotes = createFile("RELEASE_NOTES.md")
        releaseNotes << """
        ** FIRST RELEASE **
        """.stripIndent()

        and: "one release"
        createRelease("0.1.0")

        when:
        def result = runTasksSuccessfully("appendReleaseNotes")

        then:
        releaseNotes.text.contains("** FIRST RELEASE **")
        releaseNotes.text.contains("# 0.1.0 -")
    }

    def "append release notes with multiple releases"() {
        given: "a RELEASE_NOTES.md file"
        def releaseNotes = createFile("RELEASE_NOTES.md")
        releaseNotes << """
        ** FIRST RELEASE **
        """.stripIndent()

        and: "one release"
        createRelease("0.0.1")
        createRelease("0.1.0")

        when:
        def result = runTasksSuccessfully("appendReleaseNotes")

        then:
        releaseNotes.text.contains("** FIRST RELEASE **")
        releaseNotes.text.contains("# 0.1.0 -")
        !releaseNotes.text.contains("# 0.0.1 -")
    }

    def "generate release notes with multiple releases"() {
        given: "a RELEASE_NOTES.md file"
        def releaseNotes = createFile("RELEASE_NOTES.md")
        releaseNotes << """
        ** FIRST RELEASE **
        """.stripIndent()

        and: "logs"
        git.commit(message: 'a change')
        git.tag.add(name: "v1.0.0")
        git.commit(message: 'a change')
        git.tag.add(name: "v1.1.0")
        git.commit(message: 'a change')
        git.tag.add(name: "v1.2.0")

        and: "some releases"
        createRelease("1.0.0")
        createRelease("1.1.0")
        createRelease("1.2.0")

        when:
        def result = runTasksSuccessfully("generateReleaseNotes")

        then:
        !releaseNotes.text.contains("** FIRST RELEASE **")
        releaseNotes.text.contains("# 1.0.0 -")
        releaseNotes.text.contains("# 1.1.0 -")
        releaseNotes.text.contains("# 1.2.0 -")

        releaseNotes.text =~ /(?s)(# 1\.2\.0).*(1\.1\.0).*(1\.0\.0)/
    }
}
