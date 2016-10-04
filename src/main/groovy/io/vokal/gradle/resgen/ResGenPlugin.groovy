package io.vokal.gradle.resgen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory

class ResGenPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create('resgen', ResGenExtension)

        // Add 'pdf' as a source set extension
        project.android.sourceSets.all { sourceSet ->
            def (major, minor) = project.gradle.gradleVersion.tokenize(".")
            if (minor.indexOf("-") > 0) minor = minor.substring(0, minor.indexOf("-"))
            if (Integer.parseInt(major) >= 2 && Integer.parseInt(minor) >= 12) {
                sourceSet.extensions.create('pdf', DefaultSourceDirectorySet, 'pdf', project.fileResolver,
                        new DefaultDirectoryFileTreeFactory())
            } else {
                sourceSet.extensions.create('pdf', DefaultSourceDirectorySet, 'pdf', project.fileResolver)
            }

            def resgen = new File(project.getProjectDir(), "src/$sourceSet.name/res-gen")
            if (resgen.exists()) {
                sourceSet.pdf.srcDirs += resgen.getAbsolutePath()
            }
        }

        project.afterEvaluate {
            List<Density> densities = new ArrayList<>();
            project.resgen.densities.each { String density ->
                try {
                    densities.add(Density.valueOf(density.toUpperCase()))
                } catch (Exception e) {
                    // ignore
                }
            }
            densities = densities.sort() // Keep order consistent

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
                rasterizeOptions.mipmapDensities = rasterizeOptions.mipmapDensities.sort()
            }

            def variants = null
            if (project.android.hasProperty('applicationVariants')) {
                variants = project.android.applicationVariants
            } else if (project.android.hasProperty('libraryVariants')) {
                variants = project.android.libraryVariants
            } else {
                return
            }

            variants.all { variant ->
                def varNameCap = variant.name.capitalize()

                SourceDirectorySet pdfSources = null
                variant.sourceSets.each { src ->
                    if (pdfSources == null) {
                        pdfSources = src.pdf.asFileTree
                    } else {
                        pdfSources.source(src.pdf.asFileTree)
                    }
                }

                String outDir = "$project.buildDir/generated/res/resgen/$variant.dirName/"

                def rasterTaskName = "generate${varNameCap}ResRasterizePdf"
                Task rasterTask = project.task(rasterTaskName, type: RasterizeTask) {
                    sources = pdfSources
                    outputDir = project.file(outDir)
                    includeDensities = densities
                    options = rasterizeOptions
                }

                // we would do this but Android Studio doesn't see the generated resources
                // (maybe it will be fixed in the future, does not work as of 2.0-beta6)
//                  variant.registerResGeneratingTask(rasterTask, rasterTask.outputDir)

                // so, we register dependency directly and add outputs to most specific source set
                project.tasks["generate${varNameCap}Resources"].dependsOn(rasterTaskName)
                variant.sourceSets[variant.sourceSets.size() - 1].res.srcDirs += outDir
            }
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
