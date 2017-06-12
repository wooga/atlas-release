/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wooga.gradle.release

import nebula.plugin.release.NetflixOssStrategies
import org.ajoberstar.gradle.git.release.opinion.Strategies
import org.ajoberstar.gradle.git.release.semver.SemVerStrategy
import org.ajoberstar.gradle.git.release.semver.StrategyUtil

class WoogaStrategies {

    static final SemVerStrategy PRE_RELEASE = Strategies.PRE_RELEASE.copyWith(
            normalStrategy: NetflixOssStrategies.scopes,
            preReleaseStrategy: StrategyUtil.all(StrategyUtil.closure({ state ->
                def stage = Strategies.PreRelease.STAGE_FIXED.infer(state).inferredPreRelease
                def count = Strategies.PreRelease.COUNT_INCREMENTED.infer(state).inferredPreRelease
                def integration = "$count".padLeft(5, '0')
                state.copyWith(inferredPreRelease: "$stage$integration")
            }))
    )

    static final SemVerStrategy SNAPSHOT = Strategies.PRE_RELEASE.copyWith(
            name: 'snapshot',
            stages: ['snapshot','SNAPSHOT'] as SortedSet,
            normalStrategy: NetflixOssStrategies.scopes,
            preReleaseStrategy: StrategyUtil.all(StrategyUtil.closure({state ->

                String branchName = state.currentBranch.name
                String prefix = "branch"

                if( branchName == "HEAD" && System.getenv("BRANCH_NAME") ) {
                    branchName = System.getenv("BRANCH_NAME")
                }

                if( branchName != "master") {
                    branchName = "$prefix${branchName.capitalize()}"
                }
                branchName = branchName.replaceAll(/(\/|-|_)([\w])/) {all, delimiter, firstAfter -> "${firstAfter.capitalize()}" }
                branchName = branchName.replaceAll(/\./, "Dot")
                branchName = branchName.replaceAll(/0/, "Zero")
                branchName = branchName.replaceAll(/1/, "One")
                branchName = branchName.replaceAll(/2/, "Two")
                branchName = branchName.replaceAll(/3/, "Three")
                branchName = branchName.replaceAll(/4/, "Four")
                branchName = branchName.replaceAll(/5/, "Five")
                branchName = branchName.replaceAll(/6/, "Six")
                branchName = branchName.replaceAll(/7/, "Seven")
                branchName = branchName.replaceAll(/8/, "Eight")
                branchName = branchName.replaceAll(/9/, "Nine")

                def buildSinceAny = state.nearestVersion.distanceFromNormal
                def integration = "$buildSinceAny".padLeft(5, '0')
                state.copyWith(inferredPreRelease: "$branchName$integration")
            })),
            createTag: false,
            allowDirtyRepo: true,
            enforcePrecedence: false
    )
}
