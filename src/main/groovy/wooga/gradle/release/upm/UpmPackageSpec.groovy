package wooga.gradle.release.upm

import com.wooga.gradle.BaseSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

trait UpmPackageSpec extends BaseSpec {

    /**
     * @return The directory where the package sources is located (e.g: 'Wooga.Package/Assets/Wooga/Package'
     */
    DirectoryProperty getUpmPackageSourceDirectory() {
        upmPackageSourceDirectory
    }
    private final DirectoryProperty upmPackageSourceDirectory = objects.directoryProperty()

    /**
     * @return The name of the package to be published to the UPM artifactory (e.g: "test_package').
     * This will be appended to the base "com.wooga"
     */
    Property<String> getUpmPackageName(){
        upmPackageName
    }
    private final Property<String> upmPackageName = objects.property(String)

}
