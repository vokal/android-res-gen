Android Resource Generator
===============

Automatic resource exporter plugin for android projects: generating density specific drawables from PDF files.

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
        classpath 'com.android.tools.build:gradle:${toolsVersion}'
        classpath 'io.vokal.gradle:resgen:0.5.0'
    }
}
~~~

## If using precompiled JAR:
~~~gradle
buildscript {
    repositories {
        …
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${toolsVersion}'
        classpath files('libs/resgen-0.5.0.jar')
        classpath 'org.apache.pdfbox:pdfbox:2.0.0-RC1'
    }
}

~~~

# Usage

### Options

 * __densities__           [_array of strings_] - Default: ["hdpi", "xhdpi", "xxhdpi"]
 * __sourceDir__           [_string_]           - Default: "res-gen"
 * __generatedDir__        [_string_]           - Default: ".res-gen"
 * __jpeg__                [_array of strings_] - Usage: File pattern to match for jpeg rendering
 * __jpegQuality__         [_float_]            - Usage: Quality Range from 0 to 100
 * __mipmap__              [_array of strings_] - Usage: File pattern to match for mipmap rendering
 * __mipmapDensities__     [_array of strings_] - Default: ["hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]


~~~gradle
apply plugin: 'com.android.application'
apply plugin: 'io.vokal.resgen'

android {
   …
}

resgen {
   densities "mdpi", "hdpi", "xhdpi", "xxhdpi" 
   sourceDir "some-dir"
   generatedDir ".hidden-dir" 
   jpeg "bg_*", "exact_filename" // may contain wildcards (* or ?) or regex
   jpegQuality 80 // default is 85 if only jpeg patterns specified
   mipmap "ic_launcher", "*_image" // names of assets you would like in mipmap folders (wildcard or regex accepted)
   mipmapDensities "hdpi", "xhdpi", "xxhdpi", "xxxhdpi" // densities for mipmaps, defaults to densities
}
~~~


Densities are from the set: `["ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]`
(xxxhdpi is only used for mipmapDensities)

Place PDF assets file in `/main/src/res-gen` folder (or whatever your sourceDir is set to.)

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

If you change the generated file path, please manually delete any old generated items.
