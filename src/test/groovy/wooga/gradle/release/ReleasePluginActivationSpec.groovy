package wooga.gradle.release

import nebula.test.PluginProjectSpec
import org.ajoberstar.grgit.Grgit

class ReleasePluginActivationSpec extends PluginProjectSpec {
    Grgit git

    def setup() {
        new File(projectDir, '.gitignore') << """
        userHome/
        """.stripIndent()

        git = Grgit.init(dir: projectDir)
        git.add(patterns: ['.gitignore'])
        git.commit(message: 'initial commit')
        git.tag.add(name: 'v0.0.1')
    }

    @Override
    String getPluginName() { return 'net.wooga.release' }
}
