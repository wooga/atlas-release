package wooga.gradle.release.utils

import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Spec

class ProjectStatusTaskSpec implements Spec<Task> {

    private static final Logger logger = Logging.getLogger(ProjectStatusTaskSpec)

    private final List<Object> validStatusValues = new ArrayList<>()

    ProjectStatusTaskSpec(List<Object> validStatusValues) {
        this.validStatusValues.addAll(validStatusValues)
    }

    ProjectStatusTaskSpec(Object... validStatusValues) {
        this.validStatusValues.addAll(validStatusValues)
    }

    @Override
    boolean isSatisfiedBy(Task element) {
        Boolean satisfied = validStatusValues.contains(element.project.status)
        logger.info("'project.status' check satisfied $satisfied")
        return satisfied
    }
}
