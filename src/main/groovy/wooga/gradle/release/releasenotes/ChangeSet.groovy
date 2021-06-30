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

import com.wooga.github.changelog.changeSet.Compound
import com.wooga.github.changelog.changeSet.Logs
import com.wooga.github.changelog.changeSet.PullRequests
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest

class ChangeSet<B extends com.wooga.github.changelog.changeSet.ChangeSet<GHCommit, GHPullRequest>> implements com.wooga.github.changelog.changeSet.ChangeSet, Logs<GHCommit>, PullRequests<GHPullRequest>, Compound<B> {

	Map<ChangeNote, GHPullRequest> changes

	ChangeSet(B base, Map<ChangeNote, GHPullRequest> changes) {
		this.inner = base
		this.changes = changes
	}

	@Delegate
	final B inner

	@Override
	void mutate(Closure mutator) {
		this.with mutator
	}

	@Override
	<M> M map(Closure<M> map) {
		map.delegate = this
		map.resolveStrategy = Closure.DELEGATE_ONLY
		map.call(this)
	}
}
