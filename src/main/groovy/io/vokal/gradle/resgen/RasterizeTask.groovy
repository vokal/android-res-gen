/*
 * Copyright 2016 Vokal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vokal.gradle.resgen

import org.gradle.api.DefaultTask
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails

/**
 * Task that rasterizes PDFs into PNGs.
 */
class RasterizeTask extends DefaultTask {

    /**
     * The input PDFs.
     */
    @InputFiles
    SourceDirectorySet sources

    /**
     * The output directory.
     */
    @OutputDirectory
    File outputDir

    /**
     * The densities to scale assets for.
     */
    List<Density> includeDensities

    /**
     * JPG and mipmap options
     */
    RasterizeOptions options

    /**
     * There is a bug determining whether this task is up-to-date with
     * `includeDensities`: because it is a List<Enum>, it fails when you
     * have the daemon running the build. This method is provided as a
     * workaround to test up-to-dateness, but should never actually used
     * beyond that.
     *
     * References:
     * - https://discuss.gradle.org/t/custom-task-never-up-to-date-when-running-daemon/9525
     * - https://issues.gradle.org/browse/GRADLE-3018
     */
    @Input
    List<String> getDensitiesWorkaround() {
        includeDensities.collect { it.toString() }
    }

    @TaskAction
    def rasterize(IncrementalTaskInputs inputs) {
        // If the whole thing isn't incremental, delete the build folder (if it exists)
        if (!inputs.isIncremental() && outputDir.exists()) {
            logger.debug("PDF rasterization is not incremental; deleting build folder and starting fresh!")
            outputDir.delete()
        }

        List<File> PDFFiles = []
        inputs.outOfDate { InputFileDetails change ->
            logger.debug("$change.file.name out of date; converting")
            if (change.file.name.endsWith(".pdf"))
                PDFFiles.add change.file
        }

        Converter converter = new Converter()
        PDFFiles.each { File pdfFile ->
            PDFResource PDFResource = new PDFResource(pdfFile)
            def name = PDFResource.getName()

            def selector = ""
            sources.srcDirs.each {
                def pdfPath = pdfFile.toPath()
                if (pdfPath.startsWith(it.toPath())) {
                    int pathCount = it.toPath().nameCount
                    int pdfCount = pdfPath.nameCount - 1
                    if (pdfCount > pathCount)
                        selector = pdfPath.subpath(it.toPath().nameCount, pdfPath.nameCount - 1).join("-") + "-"
                }
            }

            options.jpegPatterns.each { regex ->
                if (name.matches(regex)) {
                    PDFResource.jpgQualtiy = options.jpegQuality
                    PDFResource.jpg = true
                }
            }

            boolean mipmap = false
            options.mipmapPatterns.each { regex ->
                if (name.matches(regex)) {
                    mipmap = true
                }
            }

            if (mipmap) {
                options.mipmapDensities.each { Density density ->
                    converter.transcode(PDFResource, density, getResourceDir("mipmap", selector, density))
                }
            } else {
                includeDensities.each { Density density ->
                    converter.transcode(PDFResource, density, getResourceDir("drawable", selector, density))
                }
            }
        }

        inputs.removed { change ->
            logger.debug("$change.file.name was removed; removing it from generated folder")

            int suffixStart = change.file.name.lastIndexOf '.'
            def name = suffixStart == -1 ? change.file.name : change.file.name.substring(0, suffixStart)

            boolean mipmap = false
            options.mipmapPatterns.each { regex ->
                if (name.matches(regex)) {
                    mipmap = true
                }
            }

            if (mipmap) {
                options.mipmapDensities.each { Density density ->
                    File resDir = getResourceDir("mipmap", density)
                    File file = new File(resDir, "${change.file.name}.png")
                    file.delete()
                }
            } else {
                includeDensities.each { Density density ->
                    File resDir = getResourceDir("drawable", density)
                    try {
                        File file = new File(resDir, "${change.file.name}.png")
                        file.delete()
                    } catch (IOException e) {
                        // ignore
                    }
                    try {
                        File file = new File(resDir, "${change.file.name}.jpg")
                        file.delete()
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

        }
    }

    File getResourceDir(String type, String selector, Density density) {
        def resDir = new File(outputDir, "/$type-$selector${density.name().toLowerCase()}")
        resDir.mkdirs()
        return resDir
    }
}