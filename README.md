Android Resource Generator
===============

Automatic resource exporter plugin for android projects: generating density specific drawables from PDF files and styles from [TrueColors](https://github.com/vokal/TrueColors-OSX/blob/master/README.md) files.

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
        classpath 'io.vokal.gradle:resgen:0.4.0'
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
        classpath files('libs/resgen-0.4.0.jar')
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
   densities "mdpi", "hdpi", "xhdpi", "xxhdpi" // default: ["hdpi", "xhdpi", "xxhdpi"]

   // optional parameters
   jpeg "bg_*", "exact_filename" // may contain wildcards (* or ?) or regex
   jpegQuality 80 // default is 85 if only jpeg patterns specified
   mipmap "ic_launcher", "*_image" // names of assets you would like in mipmap folders (wildcard or regex accepted)
   mipmapDensities "hdpi", "xhdpi", "xxhdpi", "xxxhdpi" // densities for mipmaps, defaults to densities
}
~~~
Where densities are in the set: `["ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]`
(xxxhdpi is only used for mipmapDensities)

# Usage
- Place PDF assets and .truecolors file in `/main/src/res-gen` folder.
- …
- Profit

If you have alternative resources for different configurations (language, orientation, smallest width) you can nest the structure in `res-gen` folder:
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
You must follow the [Qualifier name rules](http://developer.android.com/guide/topics/resources/providing-resources.html#QualifierRules) and order the nesting as Android expects. *(currently only accepts qualifiers listed before density (dpi) in the table)*

Drawables are generated from PDF files as part of the build process or can be generated manually with gradle task `generateResFiles`.
Generated drawables can be cleared out by the `clearResCache` task. The `clean` task also depends on this task.
Drawables will be re-generated automatically if a newer PDF is found in the `res-gen` folder.

True Colors files will create colors, dimens, strings and styles defining the fonts in the .truecolors file.  It will copy the fonts to the `fonts` folder in assets.  The styles have the `fontName` attribute which is the default used by [Calligraphy](https://github.com/chrisjenx/Calligraphy/blob/master/README.md#getting-started).  Setting up your Activity to wrap the base Context and using the styles in your xml layouts is all you should need to do by default.