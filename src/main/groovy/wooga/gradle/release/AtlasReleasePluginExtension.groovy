package wooga.gradle.release

import org.gradle.api.Action
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil

/**
 * Extension object for <code>net.wooga.release</code> plugin.
 * This extension allows to set further default configurations for the plugin.
 */
interface AtlasReleasePluginExtension {
    /**
     *
     * @return a pattern
     * @see org.gradle.api.tasks.util.PatternFilterable
     */
    PatternFilterable getMetaCleanPattern()

    /**
     * Allows to configure the <code>metaCleanPattern</code> filter object with an <code>Action</code> object.
     * @param action an action object to use for configuring the <code>metaCleanPattern</code>
     * @see #metaCleanPattern
     */
    void metaCleanPattern(Action<PatternFilterable> action)

    /**
     * Allows to configure the <code>metaCleanPattern</code> filter object with a <code>Closure</code> object.
     * @param configureClosure a closure object to use for configuring the <code>metaCleanPattern</code>
     * @see #metaCleanPattern
     */
    void metaCleanPattern(Closure configureClosure)
}
