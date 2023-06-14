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

package wooga.gradle.release.releaseNotes

import com.wooga.github.changelog.changeSet.BaseChangeSet
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest
import wooga.gradle.release.releasenotes.ReleaseNotesBodyStrategy

class ReleaseNotesBodyStrategySpec extends ReleaseNotesStrategySpec<ReleaseNotesBodyStrategy> {

    @Override
    ReleaseNotesBodyStrategy generateStrategy() {
        return new ReleaseNotesBodyStrategy()
    }

    def "renders provided change list"() {
        given: "some basic changes"
        List<GHCommit> logs = generateMockLog(commitMessages.size())

        and: "a custom pull requests with change lists"
        GHPullRequest pr1 = mockPr("Fix test setup", 1)
        pr1.getBody() >> """
		## Changes
		* ![ADD] test suite startup
		* ![FIX] runner test setup code
		""".stripIndent()
        GHPullRequest pr2 = mockPr("Add custom test feature", 2)
        pr2.getBody() >> """
		## Changes
		* ![IMPROVE] test suite
		* ![FIX] test suite tools
		* ![FIX] test suite startup
		* ![ADD] new test feature
		""".stripIndent()


        def pullRequests = [pr1, pr2]

        and: "a changeset"
        def changes = new BaseChangeSet("test", null, null, logs, pullRequests)

        when:
        def result = strategy.render(changes)
        println(result)
        then:
        def expectedResult = """
        ## Changes
    
        * ![ADD] new test feature [#2](https://github.com/test/issue/2) [@TestUser](https://github.com/TestUser)
        * ![ADD] test suite startup [#1](https://github.com/test/issue/1) [@TestUser](https://github.com/TestUser)
        * ![FIX] test suite startup [#2](https://github.com/test/issue/2) [@TestUser](https://github.com/TestUser)
        * ![FIX] test suite tools [#2](https://github.com/test/issue/2) [@TestUser](https://github.com/TestUser)
        * ![FIX] runner test setup code [#1](https://github.com/test/issue/1) [@TestUser](https://github.com/TestUser)
        * ![IMPROVE] test suite [#2](https://github.com/test/issue/2) [@TestUser](https://github.com/TestUser)
        
        [ADD]: https://resources.atlas.wooga.com/icons/icon_add.svg
        [FIX]: https://resources.atlas.wooga.com/icons/icon_fix.svg
        [IMPROVE]: https://resources.atlas.wooga.com/icons/icon_improve.svg
		""".stripIndent().trim().readLines().collect{it.stripIndent().trim() }.join("\n")
        def res = result.stripIndent().trim().normalize()
        res == expectedResult
    }

    def "renders logs and pull requests when no change list can be generated"() {
        given: "a custom pull requests with change lists"
        GHPullRequest pr1 = mockPr("Add custom test feature", 1)
        pr1.getBody() >> """
		Just a description
		""".stripIndent()
        def pullRequests = [pr1]

        and: "a changeset"
        def changes = new BaseChangeSet("test", null, null, null, pullRequests)

        when:
        def result = strategy.render(changes)
        println(result)
        then:
        result.trim() == """
		## Pull Requests
 
		* [#1](https://github.com/test/issue/1): Add custom test feature [@TestUser](https://github.com/TestUser)
		 
		""".stripIndent().trim()
    }
}
