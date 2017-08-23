package wooga.gradle.release.utils

import org.gradle.api.Project
import org.gradle.api.Task
import spock.lang.Specification
import spock.lang.Unroll

class ProjectStatusTaskSpecTest extends Specification {

    ProjectStatusTaskSpec spec
    Project project
    Task taskElement

    def setup() {
        project = Mock()
        taskElement = Mock()
        taskElement.project >> project
    }

    @Unroll
    def "verify isSatisfiedBy with #statusValues and project status #status"() {
        given: "a spec"
        spec = new ProjectStatusTaskSpec(statusValues)

        and: "a project status"
        project.status >> status

        expect:
        spec.isSatisfiedBy(taskElement) == expectedResult

        where:
        statusValues                 | status       | expectedResult
        ['c', 'a', 'd']              | 'a'          | true
        ['c', 'a', 'd']              | 'b'          | false
        ['c', 16, 'd', new Object()] | 22           | false
        ['c', 22, 'd', new Object()] | 22           | true
        ['c', 22, 'd', [:]]          | [:]          | true
        [[1, 2, 3], 22, 'd', [:]]    | [1, 2, 3]    | true
        [[1, 2, 3]]                  | [1, 2, 3, 4] | false
    }

    @Unroll
    def "can initialize with spec with vargs #statusValues"() {
        given: "a spec"
        spec = new ProjectStatusTaskSpec(*statusValues)

        and: "a project status"
        project.status >> status

        expect:
        spec.isSatisfiedBy(taskElement) == expectedResult

        where:
        statusValues                 | status       | expectedResult
        ['c', 'a', 'd']              | 'a'          | true
        ['c', 'a', 'd']              | 'b'          | false
        ['c', 16, 'd', new Object()] | 22           | false
        ['c', 22, 'd', new Object()] | 22           | true
        ['c', 22, 'd', [:]]          | [:]          | true
        [[1, 2, 3], 22, 'd', [:]]    | [1, 2, 3]    | true
        [[1, 2, 3]]                  | [1, 2, 3, 4] | false
    }
}
