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

import nebula.test.IntegrationSpec
import org.ajoberstar.grgit.Grgit
import spock.lang.Unroll

class ReleasePluginIntegrationSpec extends IntegrationSpec {

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
    }

    @Unroll("verify versionCode generation from version #tagVersion")
    def "creates versionCode from inferred version"() {
        given:
        git.tag.add(name: "v$tagVersion")
        buildFile << """
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            
            

            test.doLast {
                println "versionCode:" + versionCode
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("test")

        then:
        result.standardOutput.contains("versionCode:${versionCode}")

        where:
        tagVersion       | versionCode
        '1.1.0'          |  10200
        '2.10.99'        |  21100
        '0.3.0'          |    400
        '12.34.200'      | 123500
        '1.1.0-rc0001'   |  10100
        '2.10.99-branch' |  21099
        '0.3.0-a0000'    |    300
        '12.34.200-2334' | 123600
    }

    @Unroll("verify versionCode generation in subproject version #tagVersion")
    def "creates versionCode from inferred version in subproject"() {
        given:
        git.tag.add(name: "v$tagVersion")
        addSubproject("testSub", """
            task("test") {
                doLast {
                    println project.name + " versionCode:" + versionCode
                }
            }
        """.stripIndent())
        buildFile << """
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("test")

        then:
        result.standardOutput.contains("testSub versionCode:${versionCode}")

        where:
        tagVersion       | versionCode
        '1.1.0'          |  10200
        '2.10.99'        |  21100
        '0.3.0'          |    400
        '12.34.200'      | 123500
        '1.1.0-rc0001'   |  10100
        '2.10.99-branch' |  21099
        '0.3.0-a0000'    |    300
        '12.34.200-2334' | 123600
    }
}
