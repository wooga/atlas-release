package wooga.gradle.release

import org.gradle.api.Action
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil

/**
 * Extension object for <code>net.wooga.release</code> plugin.
 * This extension allows to set further default configurations for the plugin.
 */
class AtlasReleasePluginExtension {

    private PatternSet metaCleanPattern = new PatternSet()

    /**
     *
     * @return a pattern
     * @see org.gradle.api.tasks.util.PatternFilterable
     */
    PatternFilterable getMetaCleanPattern() {
        this.metaCleanPattern
    }

    /**
     * Allows to configure the <code>metaCleanPattern</code> filter object with an <code>Action</code> object.
     * @param action an action object to use for configuring the <code>metaCleanPattern</code>
     * @see #metaCleanPattern
     */
    void metaCleanPattern(Action<PatternFilterable> action) {
        action.execute(this.metaCleanPattern)
    }

    /**
     * Allows to configure the <code>metaCleanPattern</code> filter object with a <code>Closure</code> object.
     * @param configureClosure a closure object to use for configuring the <code>metaCleanPattern</code>
     * @see #metaCleanPattern
     */
    void metaCleanPattern(Closure configureClosure) {
        metaCleanPattern(ConfigureUtil.configureUsing(configureClosure))
    }
}
