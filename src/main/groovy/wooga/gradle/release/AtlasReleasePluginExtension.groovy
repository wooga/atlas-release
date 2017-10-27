package wooga.gradle.release

import org.gradle.api.Action
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil

class AtlasReleasePluginExtension {

    private PatternSet metaCleanPattern = new PatternSet()

    PatternFilterable getMetaCleanPattern() {
        this.metaCleanPattern
    }

    void metaCleanPattern(Action<PatternFilterable> action) {
        action.execute(this.metaCleanPattern)
    }

    void metaCleanPattern(Closure configureClosure) {
        metaCleanPattern(ConfigureUtil.configureUsing(configureClosure))
    }
}
