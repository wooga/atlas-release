package wooga.gradle.release

abstract class GithubIntegrationWithDefaultAuth extends GithubIntegration {
    def setup() {
        buildFile << """
            github {
                userName = "$testUserName"
                repositoryName = "$testRepositoryName"
                token = "$testUserToken"
            }
        """.stripIndent()
    }
}
