atlas-release
===========

![Wooga Internal](https://img.shields.io/badge/wooga-internal-lightgray.svg?style=flat-square)
[![Build Status](https://img.shields.io/travis/wooga/atlas-release/master.svg?style=flat-square)](https://travis-ci.org/wooga/atlas-release)
[![Coveralls Status](https://img.shields.io/coveralls/wooga/atlas-release/master.svg?style=flat-square)](https://coveralls.io/github/wooga/atlas-release?branch=master)
[![Apache 2.0](https://img.shields.io/badge/license-Apache%202-blue.svg?style=flat-square)](https://raw.githubusercontent.com/wooga/atlas-release/master/LICENSE)
[![GitHub tag](https://img.shields.io/github/tag/wooga/atlas-release.svg?style=flat-square)]()
[![GitHub release](https://img.shields.io/github/release/wooga/atlas-release.svg?style=flat-square)]()

This plugin is an wooga internal helper plugin to configure the build setup for internal wooga library packages.

Conventions
-----------

* Applies [net.wooga.paket][atlas-paket]
* Applies [nebula.release][nebula-release]
* Applies [org.ajoberstar.release-base][gradle-git]
* Applies [cz.malohlava.visteg][visteg]
* adds archive dependencies to `unitypackage`'s when [net.wooga.unity][atlas-unity] project is applied to subproject
* adds `setup` task to lifecycle to setup paket dependencies and other installation routines
* configures version strategies for use with [Paket][paket] and [nuget][nuget]

Applying the plugin
-------------------

**build.gradle**
```groovy
plugins {
    id 'net.wooga.release' version '0.5.0'
}
```

Documentation
=============

- [API docs](https://wooga.github.io/atlas-release/docs/api/)
- [Release Notes](RELEASE_NOTES.md)]

Gradle and Java Compatibility
=============================

Built with Oracle JDK7
Tested with Oracle JDK8

| Gradle Version | Works       |
| :------------- | :---------: |
| <= 2.14        | ![no]       |
| 3.0            | ![yes]      |
| 3.1            | ![yes]      |
| 3.2            | ![yes]      |
| 3.4            | ![yes]      |
| 3.4.1          | ![yes]      |
| 3.5            | ![yes]      |
| 3.5.1          | ![yes]      |
| 4.0            | ![yes]      |
| 4.1            | ![yes]      |
| 4.2            | ![yes]      |
| 4.3            | ![yes]      |
| 4.4            | ![yes]      |
| 4.5            | ![no]      |
| 4.5            | ![no]      |

Development
===========
[Code of Conduct](docs/Code-of-conduct.md)

LICENSE
=======

Copyright 2017 Wooga GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

<!-- Links -->
[atlas-paket]:      https://github.com/wooga/atlas-paket
[atlas-unity]:      https://github.com/wooga/atlas-unity
[nebula-release]:   https://github.com/nebula-plugins/nebula-release-plugin
[gradle-git]:       https://github.com/ajoberstar/gradle-git
[visteg]:           https://github.com/mmalohlava/gradle-visteg
[paket]:            https://fsprojects.github.io/Paket/
[nuget]:            https://www.nuget.org/

[yes]:              https://atlas-resources.wooga.com/icons/icon_check.svg "yes"
[no]:               https://atlas-resources.wooga.com/icons/icon_uncheck.svg "no"
