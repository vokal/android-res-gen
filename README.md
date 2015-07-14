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
        classpath 'io.vokal.gradle:resgen:0.3.0'
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
        classpath files('libs/resgen-0.3.0.jar')
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
- Place PDF assets and .truecolors file in `/main/src/res-gen` folder.
- …
- Profit

If you have alternative resources for different configurations (language, orientation, smallest width) you can nest the structure in `res-gen` folder *(you must follow the [Qualifier name rules](http://developer.android.com/guide/topics/resources/providing-resources.html#QualifierRules) and order the nesting as Android expects)*:
~~~
res-gen/
    en/
        port/
            background.pdf
        land/
            background.pdf
    es/
        port/
            background.pdf
        land/
            background.pdf
~~~

Drawables are generated from PDF files as part of the build process or can be generated manually with gradle task `generateResFiles`.
Generated drawables can be cleared out by the `clearResCache` task. The `clean` task also depends on this task.
Drawables will be re-generated automatically if a newer PDF is found in the `res-gen` folder.

True Colors files will create colors, dimens, strings and styles defining the fonts in the .truecolors file.  It will copy the fonts to the `fonts` folder in assets.  The styles have the `fontName` attribute which is the default used by [Calligraphy](https://github.com/chrisjenx/Calligraphy/blob/master/README.md#getting-started).  Setting up your Activity to wrap the base Context and using the styles in your xml layouts is all you should need to do by default.

The project will build normally and assets will resolve correctly but to have Android Studio find the generated resources for use in the Layout Editor, add this near the bottom of the `android` closure (below `buildTypes`):
~~~gradle
android {
    buildTypes {
        release {
            …
        }
    }
    
    sourceSets.main.res.srcDir 'src/main/.res-gen'
}
~~~

*NOTE: flavor folders should work, and create .res-gen folders specific to the flavor, you would need to setup the sourceSets.res.srcDir addition for Android Studio in the flavor block*

