package wooga.gradle.release.utils

import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Spec

class ProjectPropertyValueTaskSpec implements Spec<Task> {

    private final String propertyName
    private final List<Object> validValues = new ArrayList<>()

    private static final Logger logger = Logging.getLogger(ProjectPropertyValueTaskSpec)

    ProjectPropertyValueTaskSpec(String name, List<Object> values) {
        this.propertyName = name
        this.validValues.addAll(values)
    }

    ProjectPropertyValueTaskSpec(String name, Object... values) {
        this.propertyName = name
        this.validValues.addAll(values)
    }

    @Override
    boolean isSatisfiedBy(Task task) {
        def value = task.project.properties[propertyName]
        Boolean satisfied = false
        String messagePrefix = "Predicate property '${propertyName}' for task '${task.name}'"
        if (value) {
            satisfied = value in validValues
            if (satisfied) {
                logger.info("${messagePrefix} satisfies the condition as it has a valid value of '${value}'")
            } else {
                logger.info("${messagePrefix} did not satisfy the condition as its value of '${value}' was not among those valid: ${validValues}")
            }
        } else {
            logger.warn("${messagePrefix} did not satisfy the condition as it was not found among the project's properties")
        }
        return satisfied
    }
}
