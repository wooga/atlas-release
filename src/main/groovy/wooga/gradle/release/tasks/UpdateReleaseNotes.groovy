package wooga.gradle.release.tasks

import org.gradle.api.tasks.*
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHFileNotFoundException
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
        logger.debug("update release notes")
        def body = getReleaseNotes().text.normalize()
        GHCommit lastCommit = ++repository.listCommits().iterator()
        String releaseNotesFileName = project.relativePath(getReleaseNotes())
        GHContent content
        try {
            content = repository.getFileContent(releaseNotesFileName, lastCommit.getSHA1())
            if (content.read().text == body) {
                logger.info("no content change in release notes")
                throw new StopActionException()
            }
            logger.info("update release notes")
            content.update(body, getCommitMessage())
        }
        catch (GHFileNotFoundException error) {
            logger.info("release notes file not found")
            logger.debug(error.message)
            logger.info("create release notes file")
            repository.createContent(body, getCommitMessage(), releaseNotesFileName)
        }
    }
}
