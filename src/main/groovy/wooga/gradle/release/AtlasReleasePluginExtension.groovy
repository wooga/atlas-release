/*
 * Copyright 2021 Wooga GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


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
