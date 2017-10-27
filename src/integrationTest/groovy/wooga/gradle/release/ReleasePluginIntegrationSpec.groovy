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
import spock.lang.Ignore
import spock.lang.Unroll
import wooga.gradle.unity.UnityPlugin

class ReleasePluginIntegrationSpec extends IntegrationSpec {

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.0.1")
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
        '1.1.0'          | 10200
        '2.10.99'        | 21100
        '0.3.0'          | 400
        '12.34.200'      | 123500
        '1.1.0-rc0001'   | 10100
        '2.10.99-branch' | 21099
        '0.3.0-a0000'    | 300
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
        '1.1.0'          | 10200
        '2.10.99'        | 21100
        '0.3.0'          | 400
        '12.34.200'      | 123500
        '1.1.0-rc0001'   | 10100
        '2.10.99-branch' | 21099
        '0.3.0-a0000'    | 300
        '12.34.200-2334' | 123600
    }

    @Unroll("verify dependency setup to #testType unity sub-projects")
    def "verify dependency setup to unity sub-projects"() {
        given: "some subprojects with net.wooga.unity applied"

        [range].each {
            addSubproject("testSub$it", """
                apply plugin: 'net.wooga.unity'
                
             """.stripIndent())
        }

        and: "a buildfile with release plugin applied"
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("unityPack")

        then:
        [range].collect {
            result.wasExecuted(":testSub$it:exportUnityPackage")
        }.every()

        where:
        range << [1..1, 1..4]
        testType = range.size() > 1 ? "multiple" : "single"
    }

    def "verify dependency setup to missing unity sub-projects"() {
        given: "a buildfile with release plugin applied"
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("unityPack")

        then:
        result.standardOutput.contains("unityPack NO-SOURCE")
    }

    def gradleVersions() {
        ["2.14", "3.0", "3.2", "3.4", "3.4.1", "3.5", "3.5.1", "4.0"]
    }

    @Ignore
    @Unroll("verify plugin activation with gradle #gradleVersionToTest")
    def "activates with multiple gradle versions"() {
        given: "a buildfile with unity plugin applied"
        fork = true

        buildFile << """
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        gradleVersion = gradleVersionToTest

        expect:
        runTasksSuccessfully("tasks")

        where:
        gradleVersionToTest << gradleVersions()
    }

    def "cleanMetaFiles task removes all .meta files in given directory structure"() {
        given: "a subproject with unity plugin"
        File unitySub = addSubproject("unity.test", """
            ${applyPlugin(UnityPlugin)}
        """.stripIndent())

        and: "some .meta files"
        def assetsDir = new File(unitySub, "Assets/")
        assetsDir.mkdirs()
        def aSubDir = new File(assetsDir, "sub")
        aSubDir.mkdirs()

        def filesToDelete = [createFile("test.meta", assetsDir),
                             createFile(".meta", assetsDir),
                             createFile("test.meta", aSubDir),
                             createFile(".meta", aSubDir)]

        def filesToKeep = [createFile("test.dll.meta", assetsDir),
                           createFile("test.cs", assetsDir),
                           createFile("test.json", assetsDir),
                           createFile("test.cs", aSubDir),
                           createFile("test.json", aSubDir),
                           createFile("test.so.meta", assetsDir),
                           createFile("test.dll.meta", aSubDir),
                           createFile("test.so.meta", aSubDir),
                           createFile("test.meta", unitySub),
                           createFile(".meta", unitySub)]

        and: "a buildfile with release plugin applied"
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("cleanMetaFiles")

        then:
        result.wasExecuted(":unity.test:cleanMetaFiles")
        !result.wasUpToDate(":unity.test:cleanMetaFiles")
        filesToDelete.every { !it.exists() }
        filesToKeep.every { it.exists() }
    }

    def "verify cleanMetaFiles custom pattern set"() {
        given: "a subproject with unity plugin"
        File unitySub = addSubproject("unity.test", """
            ${applyPlugin(UnityPlugin)}
        """.stripIndent())

        and: "some .meta files"
        def assetsDir = new File(unitySub, "Assets/")
        assetsDir.mkdirs()
        def aSubDir = new File(assetsDir, "sub")
        aSubDir.mkdirs()

        def aSubDir2 = new File(assetsDir, "keep")
        aSubDir2.mkdirs()

        def filesToDelete = [
                createFile("file.cs.meta", assetsDir),
                createFile("file.json.meta", assetsDir),
                createFile(".meta", assetsDir),
                createFile("test.cs", assetsDir),
                createFile("test.json", assetsDir),
                createFile("test.cs", aSubDir),
                createFile("test.json", aSubDir),
        ]

        def filesToKeep = [
                createFile("test.dll.meta", assetsDir),
                createFile("file.cs.meta", aSubDir2),
                createFile("file.json.meta", aSubDir2),
                createFile(".meta", aSubDir2),
                createFile("test.dll.meta", aSubDir),
        ]

        and: "a buildfile with release plugin applied"
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        and: "a custom meta clean pattern"
        buildFile << """
            atlasRelease.metaCleanPattern {
                include("**/*.cs")
                include("**/*.json")
                exclude("**/*.dll.meta")
                exclude("**/keep/*.cs.meta")
                exclude("**/keep/*.json.meta")
                exclude("**/keep/.meta")
                    
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("cleanMetaFiles")

        then:
        result.wasExecuted(":unity.test:cleanMetaFiles")
        !result.wasUpToDate(":unity.test:cleanMetaFiles")
        filesToDelete.every { !it.exists() }
        filesToKeep.every { it.exists() }
    }

    def "verify cleanMetaFiles replace pattern set"() {
        given: "a subproject with unity plugin"
        File unitySub = addSubproject("unity.test", """
            ${applyPlugin(UnityPlugin)}
        """.stripIndent())

        and: "some .meta files"
        def assetsDir = new File(unitySub, "Assets/")
        assetsDir.mkdirs()
        def aSubDir = new File(assetsDir, "sub")
        aSubDir.mkdirs()

        def aSubDir2 = new File(assetsDir, "keep")
        aSubDir2.mkdirs()

        def filesToDelete = []

        def filesToKeep = [
                createFile("test.meta", assetsDir),
                createFile(".meta", assetsDir),
                createFile("test.meta", aSubDir),
                createFile(".meta", aSubDir),
                createFile("test.dll.meta", assetsDir),
                createFile("test.cs", assetsDir),
                createFile("test.json", assetsDir),
                createFile("test.cs", aSubDir),
                createFile("test.json", aSubDir),
                createFile("test.so.meta", assetsDir),
                createFile("test.dll.meta", aSubDir),
                createFile("test.so.meta", aSubDir),
                createFile("test.meta", unitySub),
                createFile(".meta", unitySub)]

        and: "a buildfile with release plugin applied"
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
        """.stripIndent()

        and: "a custom meta clean pattern"
        buildFile << """
            atlasRelease.metaCleanPattern {
                exclude("**/*.meta")     
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully("cleanMetaFiles")

        then:
        result.wasExecuted(":unity.test:cleanMetaFiles")
        result.wasUpToDate(":unity.test:cleanMetaFiles")
        filesToDelete.every { !it.exists() }
        filesToKeep.every { it.exists() }
    }
}
