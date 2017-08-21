package wooga.gradle.release.utils

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Grgit
import org.kohsuke.github.GHRepository
import wooga.gradle.github.publish.PublishBodyStrategy

class ReleaseBodyStrategy implements PublishBodyStrategy {

    private Grgit git
    private ReleaseVersion version

    ReleaseBodyStrategy(ReleaseVersion version, Grgit git) {
        this.git = git
        this.version = version
    }

    @Override
    String getBody(GHRepository repository) {
        return new ReleaseNotesGenerator(git, repository).generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)
    }
}
