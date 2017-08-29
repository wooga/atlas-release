package wooga.gradle.release.utils

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Commit
import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHLabel
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRepository

import java.util.concurrent.Callable

class ReleaseNoteBody {

    public static final String LABEL_MAJOR_CHANGE = "Major Change"
    public static final String DATE_FORMAT = "dd MMMM yyyy"

    private static class Version {
        int major
        int minor
        int patch

        String v

        Version(String versionString) {
            def match = (versionString =~ /(\d+)\.(\d+)\.(\d+)/)
            if (match) {
                major = match[0][1].toInteger()
                minor = match[0][2].toInteger()
                patch = match[0][3].toInteger()
            }

            v = versionString
        }

        String toString() {
            v
        }
    }

    private static class PullRequest {
        String body
        String title
        String issueUrl
        int number
        List<GHLabel> labels

        List<ChangeNote> changeList

        String cleanupBody(String body) {
            body.replaceAll("(?m)^#", "###")
                    .replaceAll(/\[.*?\]:http:\/\/resources.atlas.wooga.com\/icons\/.*/, "")
                    .replaceAll(/(?m)<!--.*?-->/, "")
                    .trim()
        }

        PullRequest(GHPullRequest pr) {
            title = pr.title
            issueUrl = "https://github.com/${pr.repository.fullName}/pull/${pr.number}"
            body = cleanupBody(pr.body)
            number = pr.number
            labels = pr.labels

            def changes = body.readLines().findAll { it.trim().startsWith("* ![") }
            changeList = changes.collect {
                def match = (it =~ /\!\[(.*?)\] (.*)/)
                String category = match[0][1].toString()
                String text = match[0][2].toString()
                new ChangeNote(category, text)
            }
        }
    }

    private static class ChangeNote {

        ChangeNote(String category, String text) {
            this.category = category
            this.text = text
        }

        String category
        String text
    }

    List<Commit> logs
    List<PullRequest> pullrequests
    List<GHAsset> releaseAssets

    String releaseDate
    Version version
    String releaseUrl
    String packageId
    GHRepository repo

    ChangeNote initialChange = new ChangeNote("NEW", "Initial Release")

    boolean hasPreviousVersion

    ReleaseNoteBody(ReleaseVersion version, Date releaseDate, String packageId, GHRepository repo, List<Commit> logs, List<GHPullRequest> prs, List<GHAsset> releaseAssets) {
        this.logs = logs
        this.repo = repo
        this.hasPreviousVersion = version.previousVersion != null
        this.version = new Version(version.version)
        this.releaseDate = releaseDate.format(DATE_FORMAT)
        this.packageId = packageId
        this.releaseUrl = "https://github.com/${repo.fullName}/releases/tag/v${this.version.toString()}"
        this.pullrequests = prs.toSorted({ a, b -> b.number <=> a.number }).collect {
            new PullRequest(it)
        }
        this.releaseAssets = releaseAssets.toSorted({ a, b -> a.name <=> b.name })
    }

    Callable<List<PullRequest>> additionalChanges() {
        new Callable<List<PullRequest>>() {
            @Override
            List<PullRequest> call() throws Exception {
                return pullrequests.findAll { it.labels.every { it.name != LABEL_MAJOR_CHANGE } }
            }
        }
    }

    Callable<List<PullRequest>> majorChanges() {
        new Callable<List<PullRequest>>() {
            @Override
            List<PullRequest> call() throws Exception {
                return pullrequests.findAll { it.labels.any { it.name == LABEL_MAJOR_CHANGE } }
            }
        }
    }

    Callable<Boolean> hasChangeList() {
        new Callable<Boolean>() {
            @Override
            Boolean call() throws Exception {
                return pullrequests.any { !it.changeList.empty }
            }
        }
    }

    Callable<Boolean> hasMajorChanges() {
        new Callable<Boolean>() {
            @Override
            Boolean call() throws Exception {
                return !majorChanges().call().empty
            }
        }
    }

    Callable<Boolean> hasAdditionalChanges() {
        new Callable<Boolean>() {
            @Override
            Boolean call() throws Exception {
                return !additionalChanges().call().empty
            }
        }
    }

    Callable<Boolean> hasReleaseAssets() {
        new Callable<Boolean>() {
            @Override
            Boolean call() throws Exception {
                return !releaseAssets.empty
            }
        }
    }
}