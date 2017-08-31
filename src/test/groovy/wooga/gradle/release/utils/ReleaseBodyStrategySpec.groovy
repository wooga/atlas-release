package wooga.gradle.release.utils

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Grgit
import org.apache.tools.ant.filters.StringInputStream
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRef
import org.kohsuke.github.GHRepository
import spock.lang.Specification

class ReleaseBodyStrategySpec extends Specification {

    ReleaseBodyStrategy releaseBodyStrategy
    Grgit git
    GHRepository repository
    ReleaseVersion version

    def mockPullRequest(int number, Boolean changeSet = true) {
        def bodyOut = new StringBuilder()

        bodyOut << """
        ## Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        """.stripIndent()

        if(changeSet) {
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
        pr.repository >> repository
        return pr
    }

    def setup() {
        git = Grgit.init(dir: File.createTempDir())
        git.commit(message: 'initial commit')

        repository = Mock(GHRepository)
        version = new ReleaseVersion("1.1.0", "1.0.0", false)

        releaseBodyStrategy = new ReleaseBodyStrategy(version, git)

        GHContent ghContent = Mock()
        ghContent.read() >> {new StringInputStream(TestContent.PAKET_TEMPLATE_V1)}

        GHRef ref = Mock()

        repository.getRef(_) >> ref

        repository.getFileContent(_, _) >> ghContent
    }

    def "verify calls ReleaseNotesGenerator to generate release notes body"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "mocked pull requests"
        repository.getPullRequest(1) >> mockPullRequest(1)
        repository.getPullRequest(2) >> mockPullRequest(2)
        repository.getPullRequest(3) >> mockPullRequest(3)

        when:
        def body = releaseBodyStrategy.getBody(repository)

        then:
        body.normalize() == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#2]
        * ![REMOVE] some stuff [#2]
        * ![FIX] some stuff [#2]
        """.stripIndent().stripMargin() + TestContent.ICON_IDS).trim()
    }
}
