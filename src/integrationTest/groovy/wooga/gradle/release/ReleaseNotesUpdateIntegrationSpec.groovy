package wooga.gradle.release

import org.ajoberstar.grgit.Grgit
import spock.lang.Unroll

class ReleaseNotesUpdateIntegrationSpec extends GithubIntegrationWithDefaultAuth {

    Grgit git
    File releaseNotes
    String fileName = "RELEASE_NOTES.md"

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.1.0")

        releaseNotes = createFile(fileName)
        releaseNotes << """
        # 1.0.0 - Test #
        """.stripIndent().trim()

        createRelease("0.1.0")

        buildFile << """

        task (customUpdateNotes, type: wooga.gradle.release.tasks.UpdateReleaseNotes) {
            releaseNotes project.file("$fileName")
        }
        """.stripIndent()
    }

    def cleanup() {
        try {
            testRepo.getFileContent(fileName).delete("cleanup")
        }
        catch (Exception e) {

        }
        releaseNotes.delete()
    }

    def "updates release note content on remote"() {
        given: "release notes file on remote repo"
        testRepo.createContent(initialContent, "initial release notes", fileName)

        when:
        def result = runTasksSuccessfully("customUpdateNotes")

        then:
        def lastCommit = ++testRepo.listCommits().iterator()
        testRepo.getFileContent(fileName, lastCommit.getSHA1()).read().text == "# 1.0.0 - Test #"

        where:
        initialContent = "** FIRST RELEASE **"
    }

    def "skips update commit when file content is equal"() {
        given: "release notes file on remote repo"
        testRepo.createContent(initialContent, "initial release notes", fileName)

        when:
        runTasksSuccessfully("customUpdateNotes")

        then:
        def lastCommit = ++testRepo.listCommits().iterator()
        lastCommit.getCommitShortInfo().getMessage() == "initial release notes"

        where:
        initialContent = "# 1.0.0 - Test #"
    }

    def "normalize release note content"() {
        given: "release notes file on remote repo"
        testRepo.createContent(initialContent, "initial release notes", fileName)

        and: "local release notes with CRLF line endings"
        releaseNotes.write("""
        # 1.0.0 - Test #
        
        # Changes
        Some changes described here
        """.stripIndent().trim().readLines().join("\r\n"))

        when:
        runTasksSuccessfully("customUpdateNotes")

        then:
        def lastCommit = ++testRepo.listCommits().iterator()
        !testRepo.getFileContent(fileName, lastCommit.getSHA1()).read().text.matches("(?s).*\r\n.*")

        where:
        initialContent = "# 1.0.0 - Test #"
    }

    def "creates release notes file if it doesn't exist"() {
        given: "a repo without a release notes file"

        when:
        runTasksSuccessfully("customUpdateNotes")

        then:
        def lastCommit = ++testRepo.listCommits().iterator()
        lastCommit.getCommitShortInfo().getMessage() == "Update release notes"

        where:
        initialContent = "# 1.0.0 - Test #"

    }

    @Unroll
    def "updates release note with message #commitMessageObject value set via #methodName"() {
        given: "release notes file on remote repo"
        testRepo.createContent(initialContent, "initial release notes", fileName)

        and: "commit message override"

        if (commitMessageObject) {
            buildFile << """
            customUpdateNotes {
                $methodName($commitMessageObject)
            }
            """.stripIndent()
        }

        when:
        runTasksSuccessfully("customUpdateNotes")

        then:
        def lastCommit = ++testRepo.listCommits().iterator()
        testRepo.getFileContent(fileName, lastCommit.getSHA1()).read().text == "# 1.0.0 - Test #"
        lastCommit.getCommitShortInfo().getMessage() == expectedMessage

        where:
        commitMessageObject           | expectedMessage           | useSetter
        "'String value'"              | "String value"            | false
        "'String value'"              | "String value"            | true
        "{'String value in clojure'}" | "String value in clojure" | false
        "{'String value in clojure'}" | "String value in clojure" | true
        null                          | "Update release notes"    | false
        null                          | "Update release notes"    | true

        methodName = (useSetter) ? "setCommitMessage" : "commitMessage"
        initialContent = "** FIRST RELEASE **"
    }
}
