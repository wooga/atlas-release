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
import org.ajoberstar.gradle.git.release.semver.ChangeScope
import org.ajoberstar.gradle.git.release.semver.PartialSemVerStrategy
import org.ajoberstar.gradle.git.release.semver.SemVerStrategy
import org.ajoberstar.gradle.git.release.semver.SemVerStrategyState
import org.ajoberstar.gradle.git.release.semver.StrategyUtil

import static org.ajoberstar.gradle.git.release.semver.StrategyUtil.closure
import static org.ajoberstar.gradle.git.release.semver.StrategyUtil.parseIntOrZero

class WoogaStrategies {

    private static final scopes = StrategyUtil.one(Strategies.Normal.USE_SCOPE_PROP,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_X,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_MINOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_MINOR_X,
            Strategies.Normal.USE_NEAREST_ANY, Strategies.Normal.useScope(ChangeScope.PATCH))

    static final PartialSemVerStrategy COUNT_INCREMENTED = closure { SemVerStrategyState state ->
        def nearest = state.nearestVersion
        def currentPreIdents = state.inferredPreRelease ? state.inferredPreRelease.split('\\.') as List : []
        if (nearest.any == nearest.normal || nearest.any.normalVersion != state.inferredNormal) {
            currentPreIdents << '1'
        } else {
            def indexOfFirstDiget = nearest.any.preReleaseVersion.findIndexOf { it ==~ /\d/ }
            def preReleaseversion = nearest.any.preReleaseVersion

            def nearestPreIdents = [preReleaseversion.substring(0,indexOfFirstDiget),preReleaseversion.substring(indexOfFirstDiget)]
            if (nearestPreIdents.size() <= currentPreIdents.size()) {
                currentPreIdents << '1'
            } else if (currentPreIdents == nearestPreIdents[0..(currentPreIdents.size() - 1)]) {
                def count = parseIntOrZero(nearestPreIdents[currentPreIdents.size()])
                currentPreIdents << Integer.toString(count + 1)
            } else {
                currentPreIdents << '1'
            }
        }
        return state.copyWith(inferredPreRelease: currentPreIdents.join('.'))
    }

    static final SemVerStrategy PRE_RELEASE = Strategies.PRE_RELEASE.copyWith(
            normalStrategy: scopes,
            preReleaseStrategy: StrategyUtil.all(StrategyUtil.closure({ state ->
                state = Strategies.PreRelease.STAGE_FIXED.infer(state)
                def stage = state.inferredPreRelease
                def count = WoogaStrategies.COUNT_INCREMENTED.infer(state).inferredPreRelease
                count = count.split(/\./).last()
                def integration = "$count".padLeft(5, '0')
                state.copyWith(inferredPreRelease: "$stage$integration")
            }))
    )

    static final SemVerStrategy FINAL = Strategies.FINAL.copyWith(normalStrategy: scopes)
    static final SemVerStrategy DEVELOPMENT = Strategies.DEVELOPMENT.copyWith(
            normalStrategy: scopes,
            buildMetadataStrategy: NetflixOssStrategies.BuildMetadata.DEVELOPMENT_METADATA_STRATEGY)

    static final SemVerStrategy SNAPSHOT = Strategies.PRE_RELEASE.copyWith(
            name: 'snapshot',
            stages: ['snapshot','SNAPSHOT'] as SortedSet,
            normalStrategy: scopes,
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
