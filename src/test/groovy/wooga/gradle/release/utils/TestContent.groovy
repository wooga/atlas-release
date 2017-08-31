package wooga.gradle.release.utils;

class TestContent {

    public static final String ICON_IDS = """
    <!-- START icon Id's -->

    [NEW]:https://atlas-resources.wooga.com/icons/icon_new.svg "New"
    [ADD]:https://atlas-resources.wooga.com/icons/icon_add.svg "Add"
    [IMPROVE]:https://atlas-resources.wooga.com/icons/icon_improve.svg "IMPROVE"
    [CHANGE]:https://atlas-resources.wooga.com/icons/icon_change.svg "Change"
    [FIX]:https://atlas-resources.wooga.com/icons/icon_fix.svg "Fix"
    [UPDATE]:https://atlas-resources.wooga.com/icons/icon_update.svg "Update"

    [BREAK]:https://atlas-resources.wooga.com/icons/icon_break.svg "Remove"
    [REMOVE]:https://atlas-resources.wooga.com/icons/icon_remove.svg "Remove"
    [IOS]:https://atlas-resources.wooga.com/icons/icon_iOS.svg "iOS"
    [ANDROID]:https://atlas-resources.wooga.com/icons/icon_android.svg "Android"
    [WEBGL]:https://atlas-resources.wooga.com/icons/icon_webGL.svg "Web:GL"

    <!-- END icon Id's -->
    """.stripIndent()

    public static final String MIX_URL_ICON_IDS = """
    <!-- START icon Id's -->

    [NEW]:https://resources.atlas.wooga.com/icons/icon_new.svg "New"
    [ADD]:https://atlas-resources.wooga.com/icons/icon_add.svg "Add"
    [IMPROVE]:http://resources.atlas.wooga.com/icons/icon_improve.svg "IMPROVE"
    [CHANGE]:http://atlas-resources.wooga.com/icons/icon_change.svg "Change"

    <!-- END icon Id's -->
    """.stripIndent()

    public static final String PAKET_TEMPLATE_V1 = """
    type file
    id Wooga.Services
    owners Wooga
    authors Wooga
    projectUrl
        https://github.com/wooga/wdk-unity-CoreServices
    iconUrl
        http://wooga.github.io/wdk-unity-CoreServices/img/logo.png
    licenseUrl
        https://github.com/wooga/wdk-unity-CoreServices/blob/master/LICENSE.txt
    requireLicenseAcceptance
        false
    copyright
        Copyright 2017
    tags
        wdk
    summary
        Wooga Internal Services SDK
    description
        Wooga Internal Services SDK
    files
        Assets/Wooga/**/* ==> content
        !../**/AssemblyInfo.cs
        ../README.md ==> content
    dependencies
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
    """.stripIndent().trim()

    public static final String PAKET_TEMPLATE_V2 = """
    type file
    id Wooga.Services
    owners Wooga
    authors Wooga
    projectUrl
        https://github.com/wooga/wdk-unity-CoreServices
    iconUrl
        http://wooga.github.io/wdk-unity-CoreServices/img/logo.png
    licenseUrl
        https://github.com/wooga/wdk-unity-CoreServices/blob/master/LICENSE.txt
    requireLicenseAcceptance
        false
    copyright
        Copyright 2017
    tags
        wdk
    summary
        Wooga Internal Services SDK
    description
        Wooga Internal Services SDK
    files
        Assets/Wooga/**/* ==> content
        !../**/AssemblyInfo.cs
        ../README.md ==> content
    dependencies
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
    """.stripIndent().trim()


}
