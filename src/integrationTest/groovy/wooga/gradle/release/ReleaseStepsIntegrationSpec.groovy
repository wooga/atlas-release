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

package wooga.gradle.release

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.operation.BranchAddOp
import org.gradle.internal.impldep.org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Unroll

class ReleaseStepsIntegrationSpec extends GithubIntegration {

    Grgit git

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

    def setup() {
        environmentVariables.set("GRGIT_USER", testUserName)
        environmentVariables.set("GRGIT_PASS", testUserToken)

        git = Grgit.init(dir: projectDir)

        git.remote.add(name: "origin", url: "https://github.com/${testRepositoryName}.git")
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.1.0")
    }
}
