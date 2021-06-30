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

package wooga.gradle.release.releasenotes

import com.wooga.github.changelog.AbstractGeneratorStrategy
import com.wooga.github.changelog.changeSet.BaseChangeSet
import com.wooga.github.changelog.render.ChangeRenderer
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest

abstract class ReleaseNotesStrategy extends AbstractGeneratorStrategy<ChangeSet, BaseChangeSet<GHCommit, GHPullRequest>> {
    ReleaseNotesStrategy(ChangeRenderer<ChangeSet> renderer) {
        super(renderer)
    }

    @Override
    ChangeSet mapChangeSet(BaseChangeSet<GHCommit, GHPullRequest> changes) {
        def changesMap = changes.pullRequests.inject(new HashMap<ChangeNote, GHPullRequest>()) { m, pr ->
            def body = pr.body
            // Pick up our own convention for writing change notes for our releases,
            // which are written with a list of change notes, in the form of ("* ![CATEGORY] TEXT")
            def c = body.readLines().findAll { it.trim().startsWith("* ![") }
            def changeNotes = c.collect {
                def match = (it =~ /!\[(.*?)] (.+)/)
                if (match) {
                    String category = match[0][1].toString()
                    String text = match[0][2].toString()
                    return new ChangeNote(category, text)
                }
                return null
            } - null
            changeNotes.each { changeNote -> m[changeNote] = pr }
            return m
        }

        return new ChangeSet(changes, changesMap)
    }
}
