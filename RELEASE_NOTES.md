### 1.0.0 - Mar 27 2018

* ![NEW] stable major release
* ![UPDATE] minimum java version to 8
* ![BREAK] restructure package layout
* ![REMOVE] releaseNotesGenerator -> [net.wooga.releaseNotesGenerator]
* ![ADD] groovy [api docs](https://wooga.github.io/atlas-release/docs/api/)
* ![FIX] branch push during release
* ![IMPROVE] use paket restore during setup


### 0.15.1 - Jan 18 2018

* ![IMPROVE] relax dependency version restriction for [net.wooga.paket]
* ![IMPROVE] ensure sub project setup task gets called

### 0.15.0 - Dec 12 2017

* ![Fix] error messages for rc builds

### 0.14.0 - Dec 12 2017

### 0.12.0 - Nov 13 2017

* ![UPDATE] dependency [net.wooga.paket] to `0.8.0`
* ![IMPROVE] add configureable meta clean pattern

### 0.11.1 - Oct 11 2017

* ![FIX] ReleaseNoteGenerator handle multiple hashsigns in commit message
* ![FIX] Ensure release notes file on repository

### 0.11.0 - Oct 06 2017

* ![IMPROVE] add paket dependencies to release notes

### 0.10.2 - Aug 30 2017

* ![FIX] cleanup multiple changeset icon links
* ![FIX] base url for change set icons
* ![IMPROVE] normalize release note text after generation

### 0.10.1 - Aug 30 2017

* ![FIX] missing newline between appended release notes

### 0.10.0 - Aug 24 2017

* ![ADD] Release Notes Generator

### 0.9.1 - Aug 24 2017

* ![FIX] case when pull request can't be loaded

### 0.9.0 - Aug 24 2017

* ![IMPROVE] set github release isPrerelease as lazy value

### 0.8.0 - Aug 16 2017

* ![IMPROVE] custom GitHub release body

### 0.7.0 - July 25 2017

* ![IMPROVE] set default release.scope to patch
* ![UPDATE]  dependency [net.wooga.paket] to `0.7.0`

### 0.6.0 - July 18 2017

* ![Add] custom static delete pattern for cleanMetaFiles task
* ![IMPROVE] make paketPack task depend on all cleanMetaFiles
* ![IMPROVE] create cleanMetaFiles task on subproject with unity

### 0.5.2 - July 10 2017

* ![FIX] publish task must run after release not postRelease

### 0.5.1 - July 10 2017

* ![UPDATE] integrate fix from [net.wooga.paket] (`0.6.1`)

### 0.5.0 - July 10 2017

* ![ADD]  dependency [net.wooga.github] plugin

### 0.4.0 - June 13 2017

* ![NEW] initial release
* ![ADD] `net.wooga.release` plugin


<!-- START icon Id's -->

[NEW]:https://atlas-resources.wooga.com/icons/icon_new.svg "New"
[ADD]:https://atlas-resources.wooga.com/icons/icon_add.svg "Add"
[IMPROVE]:https://atlas-resources.wooga.com/icons/icon_improve.svg "IMPROVE"
[CHANGE]:https://atlas-resources.wooga.com/icons/icon_change.svg "Change"
[FIX]:https://atlas-resources.wooga.com/icons/icon_fix.svg "Fix"
[UPDATE]:https://atlas-resources.wooga.com/icons/icon_update.svg "Update"

[BREAK]:https://atlas-resources.wooga.com/icons/icon_break.svg "Break"
[REMOVE]:https://atlas-resources.wooga.com/icons/icon_remove.svg "Remove"
[IOS]:https://atlas-resources.wooga.com/icons/icon_iOS.svg "iOS"
[ANDROID]:https://atlas-resources.wooga.com/icons/icon_android.svg "Android"
[WEBGL]:https://atlas-resources.wooga.com/icons/icon_webGL.svg "Web:GL"

<!-- END icon Id's -->

[net.wooga.github]:https://wooga.github.io/atlas-github/
[net.wooga.paket]:https://wooga.github.io/atlas-paket/
[net.wooga.releaseNotesGenerator]:https://wooga.github.io/atlas-releaseNotesGenerator/
