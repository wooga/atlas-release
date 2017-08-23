package wooga.gradle.release.tasks

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Grgit
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*
import org.kohsuke.github.GHRelease
import wooga.gradle.github.base.AbstractGithubTask
import wooga.gradle.release.utils.ReleaseNotesGenerator

import java.util.concurrent.Callable

class GenerateReleaseNotes extends AbstractGithubTask {
    private static final Logger logger = Logging.getLogger(GenerateReleaseNotes)

    private Grgit git

    private Object releaseNotes
    private Object appendLatestRelease

    GenerateReleaseNotes() {
        super(GenerateReleaseNotes.class)
        outputs.upToDateWhen { false }
    }

    @Optional
    @Input
    Boolean getAppendLatestRelease() {
        if (appendLatestRelease == null) {
            null
        } else if (appendLatestRelease instanceof Callable) {
            appendLatestRelease.call().toBoolean()
        } else {
            appendLatestRelease.toBoolean()
        }
    }

    GenerateReleaseNotes setAppendLatestRelease(Object releaseNotes) {
        this.appendLatestRelease = releaseNotes
        this
    }

    GenerateReleaseNotes appendLatestRelease(Object releaseNotes) {
        this.setAppendLatestRelease(releaseNotes)
    }

    @OutputFile
    File getReleaseNotes() {
        project.file(releaseNotes)
    }

    GenerateReleaseNotes setReleaseNotes(Object releaseNotes) {
        this.releaseNotes = releaseNotes
        this
    }

    GenerateReleaseNotes releaseNotes(Object releaseNotes) {
        this.setReleaseNotes(releaseNotes)
    }

    @Internal
    Grgit getGit() {
        git
    }

    GenerateReleaseNotes setGit(Grgit git) {
        this.git = git
        this
    }

    GenerateReleaseNotes git(Grgit git) {
        this.setGit(git)
    }

    @Internal
    protected ReleaseVersion releaseVersionForIndex(int index, List<GHRelease> releases) {
        if (releases.size() > index) {
            GHRelease release = releases[index]
            GHRelease previousRelease = index < releases.size() ? releases[index + 1] : null

            String releaseName = (release) ? release.name : null
            String previousReleaseName = (previousRelease) ? previousRelease.name : null

            logger.info("Create ReleaseVersion with ${releaseName} -> ${previousReleaseName}")
            return new ReleaseVersion(releaseName, previousReleaseName, false)
        }
        return null
    }

    @TaskAction
    protected generate() {
        File notesFile = getReleaseNotes()
        List<GHRelease> r = getRepository().listReleases().asList().findAll { !it.isPrerelease() && !it.isDraft() }

        if (r.size() == 0) {
            logger.info("no releases available")
            throw new StopActionException()
        }

        r = r.toSorted(new Comparator<GHRelease>() {
            @Override
            int compare(GHRelease o1, GHRelease o2) {
                o2.createdAt <=> o1.createdAt
            }
        })

        ReleaseNotesGenerator generator = new ReleaseNotesGenerator(getGit(), getRepository(), project.group.toString())

        int items = getAppendLatestRelease() ? 1 : r.size()
        List<ReleaseVersion> versions = new ArrayList<ReleaseVersion>()

        for (int i = 0; i < items; i++) {
            versions.add(releaseVersionForIndex(i, r))
        }

        logger.info("generate release notes with ${versions.collect { it.version + '->' + it.previousVersion }}")

        if (getAppendLatestRelease() && notesFile.text.contains(versions[0].version)) {
            logger.info("release already contained in release notes")
            throw new StopActionException()
        }

        String notes = generator.generateReleaseNotes(versions, ReleaseNotesGenerator.Template.releaseNotes)

        writeReleaseNotes(notes)
    }

    private void writeReleaseNotes(String notes) {
        File notesFile = getReleaseNotes()
        File tempFile = File.createTempFile("releaseNotes_", "", notesFile.parentFile)
        tempFile.deleteOnExit()
        tempFile.append(notes)

        if (getAppendLatestRelease()) {
            tempFile.append(notesFile.newReader())
        }
        else {
            tempFile.append("\n")
        }

        notesFile.delete()
        tempFile.renameTo(notesFile)
    }
}
