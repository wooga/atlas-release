package wooga.gradle.release

import nebula.test.IntegrationSpec
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.PagedIterable
import spock.lang.Shared

abstract class GithubIntegration extends IntegrationSpec {

    String uniquePostfix() {
        String key = "TRAVIS_JOB_NUMBER"
        def env = System.getenv()
        if (env.containsKey(key)) {
            return env.get(key)
        }
        return ""
    }

    @Shared
    def testUserName = System.getenv("ATLAS_GITHUB_INTEGRATION_USER")

    @Shared
    def testUserToken = System.getenv("ATLAS_GITHUB_INTEGRATION_PASSWORD")

    @Shared
    def testRepositoryName = "${testUserName}/atlas-release-integration" + uniquePostfix()

    @Shared
    GitHub client

    @Shared
    GHRepository testRepo

    def maybeDelete(String repoName) {
        try {
            def repository = client.getRepository(repoName)
            repository.delete()
        }
        catch (Exception e) {
        }
    }

    def createTestRepo() {
        maybeDelete(testRepositoryName)

        def builder = client.createRepository(testRepositoryName.split('/')[1])
        builder.description("Integration test repo for wooga/atlas-github")
        builder.autoInit(false)
        builder.licenseTemplate('MIT')
        builder.private_(false)
        builder.issues(false)
        builder.wiki(false)
        testRepo = builder.create()
    }

    def createRelease(String name) {
        def releaseBuilder = testRepo.createRelease(name)
        releaseBuilder.name(name)
        releaseBuilder.draft(false)
        releaseBuilder.prerelease(false)
        releaseBuilder.create()
    }

    def setupSpec() {
        client = GitHub.connectUsingOAuth(testUserToken)
        createTestRepo()
    }

    def setup() {
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()
    }

    def cleanup() {
        cleanupReleases()
    }

    void cleanupReleases() {
        try {
            PagedIterable<GHRelease> releases = testRepo.listReleases()
            releases.each {
                it.delete()
            }
        }
        catch(Error e) {

        }

    }

    def cleanupSpec() {
        maybeDelete(testRepositoryName)
    }
}
