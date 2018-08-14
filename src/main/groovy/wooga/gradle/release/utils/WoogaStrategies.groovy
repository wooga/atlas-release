/*
 * Copyright 2017 the original author or authors.
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

package wooga.gradle.release.utils

import org.ajoberstar.gradle.git.release.semver.PartialSemVerStrategy
import org.ajoberstar.gradle.git.release.semver.SemVerStrategy
import wooga.gradle.release.version.semver.VersionStrategies

/**
 * Set of {@code SemVerStrategy} properties to determine current version.
 * <p>
 * All strategies are base on <a href="https://github.com/nebula-plugins/nebula-release-plugin">Nebular Release</a> or
 * it's super set <a href="https://github.com/ajoberstar/gradle-git">Gradle Git</a>.
 */
class WoogaStrategies {

    static final PartialSemVerStrategy COUNT_INCREMENTED = VersionStrategies.COUNT_INCREMENTED

    /**
     * Returns a version strategy to be used for {@code pre-release}/{@code candidate} builds.
     * <p>
     * This strategy infers the release version by checking the nearest any release.
     * If a {@code pre-release} with the same {@code major}.{@code minor}.{@code patch} version exists, bumps the count part.
     * <p>
     * Example <i>new pre release</i>:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.2.0"
     * nearestAnyVersion = ""
     * inferred = "1.3.0-rc00001"
     * }
     * </pre>
     * <p>
     * Example <i>pre release version exists</i>:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.2.0"
     * nearestAnyVersion = "1.3.0-rc00001"
     * inferred = "1.3.0-rc00002"
     * }
     * </pre>
     * <p>
     * Example <i>last final release higher than pre-release</i>:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.3.0"
     * nearestAnyVersion = "1.3.0-rc00001"
     * inferred = "1.4.0-rc00001"
     * }
     * </pre>
     */
    static final SemVerStrategy PRE_RELEASE = VersionStrategies.PRE_RELEASE

    /**
     * Returns a version strategy to be used for {@code final} builds.
     * <p>
     * This strategy infers the release version by checking the nearest release.
     * <p>
     * Example:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.3.0"
     * inferred = "1.4.0"
     * }
     * </pre>
     */
    static final SemVerStrategy FINAL = VersionStrategies.FINAL

    /**
     * Returns a version strategy to be used for {@code development} builds.
     * <p>
     * This strategy creates a unique version string based on last commit hash and the
     * distance of commits to nearest normal version.
     * This version string is not compatible with <a href="https://fsprojects.github.io/Paket/">Paket</a> or
     * <a href="https://www.nuget.org/">Nuget</a>
     * <p>
     * Example:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.3.0"
     * commitHash = "90c90b9"
     * distance = 22
     * inferred = "1.3.0-dev.22+90c90b9"
     * }
     * </pre>
     */
    static final SemVerStrategy DEVELOPMENT = VersionStrategies.DEVELOPMENT

    /**
     * Returns a version strategy to be used for {@code snapshot} builds.
     * <p>
     * This strategy creates a snapshot version suitable for <a href="https://fsprojects.github.io/Paket/">Paket</a> and
     * <a href="https://www.nuget.org/">Nuget</a>. {@code Nuget} and {@code Paket} don't support
     * {@code SNAPSHOT} versions or any numerical values after the {@code patch} component. We trick these package managers
     * by creating a unique version based on {@code branch} and the distance of commits to nearest normal version.
     * If the branch name contains numerical values, they will be converted into alphanumerical counterparts.
     * The {@code master} branch is a special case so it will end up being higher than any other branch build (m>b)
     * <p>
     * Example <i>from master branch</i>:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.3.0"
     * branch = "master"
     * distance = 22
     * inferred = "1.4.0-master00022"
     * }
     * </pre>
     * <p>
     * Example <i>from topic branch</i>:
     * <pre>
     * {@code
     * releaseScope = "minor"
     * nearestVersion = "1.3.0"
     * branch = "feature/fix_22"
     * distance = 34
     * inferred = "1.4.0-branchFeatureFixTwoTow00034"
     * }
     * </pre>
     */
    static final SemVerStrategy SNAPSHOT = VersionStrategies.SNAPSHOT
}
