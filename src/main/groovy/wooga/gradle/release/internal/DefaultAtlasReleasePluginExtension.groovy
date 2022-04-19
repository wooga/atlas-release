/*
 * Copyright 2017-2021 the original author or authors.
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

package wooga.gradle.release.internal

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Action
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil
import wooga.gradle.release.AtlasReleasePluginExtension
import wooga.gradle.release.upm.UpmPackageSpec

class DefaultAtlasReleasePluginExtension implements AtlasReleasePluginExtension, UpmPackageSpec {
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
