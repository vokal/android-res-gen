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

import groovy.io.GroovyPrintStream
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails

import java.util.zip.ZipFile

/**
 * Task that processes TrueColors file into resources
 */
class TrueColorsTask extends DefaultTask {

    /**
     * The input PDFs.
     */
    @InputFiles
    FileCollection sources

    /**
     * The output directory.
     */
    @OutputDirectory
    File outputDir

    boolean useScalePixels

    File assetFolder

    @TaskAction
    def process(IncrementalTaskInputs inputs) {
        // If the whole thing isn't incremental, delete the build folder (if it exists)
        if (!inputs.isIncremental() && outputDir.exists()) {
            logger.debug("TrueColors is not incremental; deleting build folder and starting fresh!")
            outputDir.delete()
        }

        List<File> TCFiles = []
        inputs.outOfDate { InputFileDetails change ->
            logger.debug("$change.file.name out of date; processing…")
            TCFiles.add change.file
        }

        TCFiles.each { File TCFile ->
            generateTrueColors(TCFile, outputDir)
        }

        inputs.removed { change ->
            logger.debug("$change.file.name was removed; removing it from generated folder")
        }
    }

    def generateTrueColors(File tcFile, File outDir) {
        ArrayList<TrueColors> trueColorsData = new ArrayList<TrueColors>()
        Map<String, String> fontMap = new TreeMap<>()

        ZipFile zip = new ZipFile(tcFile.absolutePath)
        zip.entries().each { entry ->
            def parts = entry.name.split(File.separator)
            def fileName = parts[parts.length - 1]
            if (parts.length > 2 && parts[parts.length - 2].equals("fonts")) {
                File fonts = new File(assetFolder, "fonts")
                if (!fonts.exists()) {
                    fonts.mkdirs()
                }
                File font = new File(fonts.toString(), fileName)
                def out = new FileOutputStream(font);
                out << zip.getInputStream(entry)
            } else if (fileName.equals("flat-data.json")) {
                def slurper = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY)
                TrueColors data = slurper.parse(zip.getInputStream(entry))
                data.fonts.each { font ->
                    def fontName = font.font_name.replace(" ", "_").replace("-", "_")
                    font.font_name = fontName
                    fontMap.put(fontName, font.file_name)
                }
                trueColorsData.add(data)
            }
        }

        if (trueColorsData.size() > 0) {
            File values = new File(outDir, "values");
            if (!values.exists())
                values.mkdirs()

            def writer = new StringWriter()
            def xml = new MarkupBuilder(writer)
            def fontDimens = new HashSet<String>()

            // write colors
            xml.resources() {
                trueColorsData.each { data ->
                    data.colors.each { color ->
                        def name = color.path.join("_").replace(" ", "_")
                        xml.color(name: name, "#" + color.rgba.substring(7, 9) + color.rgba.substring(1, 7))
                    }
                }
            }
            File colors = new File(values.toString(), "true_colors.xml")
            new GroovyPrintStream(colors).print(writer.toString())

            // write font name strings
            writer = new StringWriter()
            xml = new MarkupBuilder(writer)
            xml.resources() {
                fontMap.each { name, font ->
                    xml.string(name: name, "fonts/" + font)
                }
            }
            File strings = new File(values.toString(), "true_strings.xml")
            new GroovyPrintStream(strings).print(writer.toString())

            // write font styles
            writer = new StringWriter()
            xml = new MarkupBuilder(writer)
            xml.resources() {
                trueColorsData.each { data ->
                    data.fonts.each { font ->
                        def name = font.path.join("_").replace(" ", "_")
                        if (font.color_path != null && font.size_path != null) {
                            def colorPath = font.color_path.join("_").replace(" ", "_")
                            def metricPath = font.size_path.join("_").replace(" ", "_")
                            if (useScalePixels) fontDimens.add(metricPath)
                            xml.style(name: name) {
                                item(name: "android:textColor", "@color/" + colorPath)
                                item(name: "android:textSize", "@dimen/" + metricPath)
                                item(name: "fontPath", "@string/" + font.font_name)
                            }
                        } else {
                            if (font.color_path == null) {
                                println "! missing  color: " + font.path.join(" > ")
                            }
                            if (font.size_path == null) {
                                println "! missing metric: " + font.path.join(" > ")
                            }
                        }
                    }
                }
            }
            File styles = new File(values.toString(), "true_styles.xml")
            new GroovyPrintStream(styles).print(writer.toString())

            // write dimens
            writer = new StringWriter()
            xml = new MarkupBuilder(writer)
            xml.resources() {
                trueColorsData.each { data ->
                    data.metrics.each { metric ->
                        def name = metric.path.join("_").replace(" ", "_")
                        if (fontDimens.contains(name)) {
                            xml.dimen(name: name, "${metric.value}sp")
                        } else {
                            xml.dimen(name: name, "${metric.value}dp")
                        }

                    }
                }
            }
            File dimens = new File(values.toString(), "true_dimens.xml")
            new GroovyPrintStream(dimens).print(writer.toString())
        }
    }

}