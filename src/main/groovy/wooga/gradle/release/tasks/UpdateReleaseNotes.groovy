package wooga.gradle.release.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHContent
import wooga.gradle.github.base.AbstractGithubTask

import java.util.concurrent.Callable

class UpdateReleaseNotes extends AbstractGithubTask {

    private Object commitMessage
    private Object releaseNotes

    UpdateReleaseNotes() {
        super(UpdateReleaseNotes)
        outputs.upToDateWhen { false }
    }

    @SkipWhenEmpty
    @InputFile
    File getReleaseNotes() {
        project.file(releaseNotes)
    }

    UpdateReleaseNotes setReleaseNotes(Object releaseNotes) {
        this.releaseNotes = releaseNotes
        this
    }

    UpdateReleaseNotes releaseNotes(Object releaseNotes) {
        this.setReleaseNotes(releaseNotes)
    }

    @Optional
    @Input
    String getCommitMessage() {
        if (commitMessage == null) {
            "Update release notes"
        } else if (commitMessage instanceof Callable) {
            ((Callable) commitMessage).call().toString()
        } else {
            commitMessage.toString()
        }
    }

    UpdateReleaseNotes setCommitMessage(Object commitMessage) {
        this.commitMessage = commitMessage
        this
    }

    UpdateReleaseNotes commitMessage(Object commitMessage) {
        this.setCommitMessage(commitMessage)
    }

    @TaskAction
    protected update() {
        GHCommit lastCommit = ++repository.listCommits().iterator()
        GHContent content = repository.getFileContent(project.relativePath(getReleaseNotes()), lastCommit.getSHA1())
        def body = getReleaseNotes().text.normalize()
        if (content.read().text == body) {
            logger.info("no content change in release notes")
            throw new StopActionException()
        }

        content.update(body, getCommitMessage())
    }
}
