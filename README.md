Android Resource Generator
===============

Automatic resource exporter plugin for android projects: generating density specific drawables from PDF files.

# Setup

## If using Artifactory:
~~~gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${toolsVersion}'
        classpath 'io.vokal.gradle:resgen:1.0.1'
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
        classpath files('libs/resgen-1.0.1.jar')
        classpath 'org.apache.pdfbox:pdfbox:2.0.0'
    }
}

~~~

# Usage

### Options

 * __densities__           [_array of strings_] - Default: ["hdpi", "xhdpi", "xxhdpi"]
 * __jpeg__                [_array of strings_] - Usage: File pattern to match for jpeg rendering
 * __jpegQuality__         [_float_]            - Usage: Quality Range from 0 to 100
 * __mipmap__              [_array of strings_] - Usage: File pattern to match for mipmap rendering
 * __mipmapDensities__     [_array of strings_] - Default: ["hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]


~~~gradle
apply plugin: 'com.android.application'
apply plugin: 'io.vokal.resgen'

android {
   …
   // if PDF files are placed in a folder named 'res-gen' they will be recognized automatically
   // or you can configure the location of your PDF files with the following
   sourceSets {
       main {
           pdf.srcDir 'src/main/pdf'
       }
   }
}

resgen {
   densities "mdpi", "hdpi", "xhdpi", "xxhdpi"
   jpeg "bg_*", "exact_filename" // may contain wildcards (* or ?) or regex
   jpegQuality 80 // default is 85 if only jpeg patterns specified
   mipmap "ic_launcher", "*_image" // names of assets you would like in mipmap folders (wildcard or regex accepted)
   mipmapDensities "hdpi", "xhdpi", "xxhdpi", "xxxhdpi" // densities for mipmaps, defaults to densities
}
~~~


Densities are from the set: `["ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]`
(xxxhdpi is only applicable for mipmapDensities)

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

Drawables are generated from PDF files as part of the build process or can be generated manually with gradle task `generate{variantName}ResRasterizePdf`.
eg. `generateDebugResRasterizePdf`, `generateStageDebugResRasterizePdf`
Drawables will be re-generated automatically when PDF file is updated.
