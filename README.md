android-res-gen
===============

Automatic res exporter plugin for android projects

# Setup

## If using Artifactory:
~~~gradle
buildscript {
    repositories {
        maven {
            url 'http://vokal-repo.ngrok.com/artifactory/repo'
            credentials {
                username = "${artifactory_user}"
                password = "${artifactory_password}"
            }
        }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.2.3'
        classpath 'io.vokal.gradle:resgen:0.2.0'
    }
}
~~~

## If using precompiled JAR:
~~~gradle
buildscript {
    repositories {
        …
        maven { url 'http://repository.apache.org/content/groups/snapshots/' }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.2.3'
        classpath files('libs/android-res-gen-0.2.0.jar')
        classpath 'org.apache.pdfbox:pdfbox:2.0.0-SNAPSHOT'
    }
}

~~~

## Common:
~~~gradle
apply plugin: 'com.android.application'
apply plugin: 'io.vokal.resgen'

android {
   …
}

resgen {
   densities "hdpi", "xhdpi", "xxhdpi"
}
~~~
Where densities are in the set: `["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]`


# Usage
- Place PDF assets in `/main/src/res-pdf` folder.
- …
- Profit

Assets are generated as part of the build process or can be generated manually with gradle task `generateResFiles`.
Assets are can be cleared out by the `clearResCache` task. The `clean` task also depends on this task.
Assets will be re-generated automatically if a newer PDF is found in the `res-gen` folder.

The project will build normally and assets will resolve correctly but to have Android Studio find the generated assets for use in the Layout Editor, add this to your `android` closure:
~~~gradle
android {
    sourceSets.main.res.srcDir 'src/main/.res-gen'
}
~~~

