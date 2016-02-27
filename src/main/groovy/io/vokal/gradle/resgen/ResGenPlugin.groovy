package io.vokal.gradle.resgen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.DefaultSourceDirectorySet

class ResGenPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create('resgen', ResGenExtension)

        // Add 'pdf' as a source set extension
        project.android.sourceSets.all { sourceSet ->
            sourceSet.extensions.create('pdf', DefaultSourceDirectorySet, 'pdf', project.fileResolver)
        }

        project.afterEvaluate {

            RasterizeOptions rasterizeOptions = new RasterizeOptions()
            if (project.resgen.jpeg != null) {
                rasterizeOptions.jpegPatterns = new String[project.resgen.jpeg.length]
                project.resgen.jpeg.eachWithIndex { wildcard, i ->
                    rasterizeOptions.jpegPatterns[i] = wildcardToRegex(wildcard)
                }
                if (project.resgen.jpegQuality != null) {
                    rasterizeOptions.jpegQuality = Math.min(100, Math.max(0, project.resgen.jpegQuality)) / 100.0f;
                }
            }
            if (project.resgen.mipmap != null) {
                rasterizeOptions.mipmapPatterns = new String[project.resgen.mipmap.length]
                project.resgen.mipmap.eachWithIndex { wildcard, i ->
                    rasterizeOptions.mipmapPatterns[i] = wildcardToRegex(wildcard)
                }
            }
            if (project.resgen.mipmapDensities != null) {
                rasterizeOptions.mipmapDensities = new ArrayList<>()
                project.resgen.mipmapDensities.each { String density ->
                    try {
                        rasterizeOptions.mipmapDensities.add(Density.valueOf(density.toUpperCase()))
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }

            List<Density> densities = new ArrayList<>();
            project.resgen.densities.each { String density ->
                try {
                    densities.add(Density.valueOf(density.toUpperCase()))
                } catch (Exception e) {
                    // ignore
                }
            }

            // Keep order consistent
            densities = densities.sort()

            def variants = null
            if (project.android.hasProperty('applicationVariants')) {
                variants = project.android.applicationVariants
            } else if (project.android.hasProperty('libraryVariants')) {
                variants = project.android.libraryVariants
            } else {
                throw new IllegalStateException('Android project must have applicationVariants or libraryVariants!')
            }

            variants.all { variant ->
                FileCollection pdfFiles = project.files()
                FileCollection tcFiles = project.files()
                variant.sourceSets.each { sourceSet ->
                    FileCollection pdfCollection = sourceSet.pdf.filter { File file ->
                        file.name.endsWith '.pdf'
                    }
                    FileCollection tcCollection = sourceSet.pdf.filter { File file ->
                        file.name.endsWith '.truecolors'
                    }
                    pdfFiles.from pdfCollection
                    tcFiles.from tcCollection
                }

                if (pdfFiles.size() > 0) {
                    String outDir = "$project.buildDir/generated/res/$flavorName/$buildType.name/resgen/"
                    variant.sourceSets.each { sourceSet ->
                        println sourceSet
                        if (sourceSet.displayName == buildType.name &&
                                !sourceSet.res.srcDirs.contains(outDir)) {
                            sourceSet.res.srcDirs += outDir
                        }
                    }

                    Task rasterizationTask = project.task("generateResFiles${variant.name.capitalize()}", type: RasterizeTask) {
                        sources = pdfFiles
                        outputDir = project.file(outDir)
                        includeDensities = densities
                        options = rasterizeOptions
                    }

                    // Makes the magic happen (inserts resources so devs can use it)
                    variant.registerResGeneratingTask(rasterizationTask, rasterizationTask.outputDir)
                }

                if (tcFiles.size() > 0) {
                    String outDir = "$project.buildDir/generated/res/$flavorName/$buildType.name/truecolors/"
                    String assetsDir = null
                    variant.sourceSets.each { sourceSet ->
                        if (sourceSet.displayName == buildType.name &&
                                !sourceSet.res.srcDirs.contains(outDir)) {
                            sourceSet.res.srcDirs += outDir
                        }
                        if (sourceSet.displayName == flavorName || sourceSet.displayName == "main") {
                            assetsDir = sourceSet.assets.srcDirs[0]
                            if (!sourceSet.assets.srcDirs.contains(assetsDir)) {
                                sourceSet.assets.srcDirs += assetsDir
                            }
                        }
                    }
                    Task truecolorsTask = project.task("processTrueColors${variant.name.capitalize()}", type: TrueColorsTask) {
                        sources = tcFiles
                        outputDir = project.file(outDir)
                        assetFolder = project.file(assetsDir)
                        useScalePixels = project.resgen.useScalePixelDimens
                    }

                    variant.registerResGeneratingTask(truecolorsTask, truecolorsTask.outputDir)
                }
            }

//            project.task("preBuildResSetup", type: SetupTask) {
//                proj = project
//            }
//            project.tasks["preBuild"].dependsOn 'preBuildResSetup'
        }
    }

    public static String wildcardToRegex(String wildcard) {
        StringBuffer s = new StringBuffer(wildcard.length())
        s.append('^')
        int len = wildcard.length()
        for (int i = 0; i < len; i++) {
            char c = wildcard.charAt(i)
            switch (c) {
                case '*':
                    s.append(".*")
                    break
                case '?':
                    s.append(".")
                    break
            // escape special regexp-characters
                case '(': case ')': case '[': case ']': case '$':
                case '^': case '.': case '{': case '}': case '|':
                case '\\':
                    s.append("\\")
                    s.append(c)
                    break
                default:
                    s.append(c)
                    break
            }
        }
        s.append('$')
        return (s.toString())
    }

    static {
        System.setProperty("java.awt.headless", "true")

        // workaround for an Android Studio issue
        try {
            Class.forName(System.getProperty("java.awt.graphicsenv"))
        } catch (ClassNotFoundException e) {
            System.err.println("[WARN] java.awt.graphicsenv: " + e)
            System.setProperty("java.awt.graphicsenv", "sun.awt.CGraphicsEnvironment")
        }
        try {
            Class.forName(System.getProperty("awt.toolkit"))
        } catch (ClassNotFoundException e) {
            System.err.println("[WARN] awt.toolkit: " + e)
            System.setProperty("awt.toolkit", "sun.lwawt.macosx.LWCToolkit")
        }
    }
}
