package io.vokal.gradle.resgen

import groovy.io.GroovyPrintStream
import groovy.json.JsonOutput
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.rendering.PDFRenderer
import org.gradle.api.Plugin
import org.gradle.api.Project

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile

class ResGenPlugin implements Plugin<Project> {
    public static final String DIR = ".res-gen";

    def types = [
            ldpi   : 0.75,
            mdpi   : 1.0,
            hdpi   : 1.5,
            xhdpi  : 2.0,
            xxhdpi : 3.0,
            xxxhdpi: 4.0
    ]

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

    Project project;

    void apply(Project project) {

        this.project = project
        def hasApp = project.hasProperty('android')
        def root = project.getProjectDir().getAbsolutePath();
        def fs = FileSystems.getDefault();

        project.extensions.create("resgen", ResGenExtension)
        project.tasks.create(name: "generateResFiles") << {
            project.android.sourceSets.each { source ->
                Path srcPath = fs.getPath(root, "src", source.name)
                Path path = fs.getPath(srcPath.toString(), "res-gen");
                Path res = FileSystems.getDefault().getPath(srcPath.toString(), DIR);

                // Always add in case there files are removed but generated is kept
                source.res.srcDirs += res.toString()

                if (Files.exists(path)) {
                    Path cache = FileSystems.getDefault().getPath(path.toString(), ".cache");
                    if (Files.notExists(cache)) {
                        Files.createFile(cache)
                        Files.write(cache, "{}".getBytes());
                    }

                    def slurper = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY)
                    Map meta = new HashMap((Map) slurper.parse(cache.toFile()));

                    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                            if (filePath.toString().endsWith(".pdf")) {
                                int baseDepth = res.nameCount;
                                int pdfDepth = filePath.parent.nameCount;
                                String selector = ""
                                if (pdfDepth > baseDepth) {
                                    Path subpath = filePath.subpath(baseDepth, pdfDepth)
                                    selector = subpath.join("-") + "-"
                                }

                                String key = selector + filePath.fileName.toString()
                                Long t = (Long) meta[key]
                                long timestamp = t == null ? 0 : t
                                meta[key] = (Long) generateAssets(res, selector, filePath, timestamp)
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    })
                    Files.write(cache, JsonOutput.toJson(meta).getBytes())
                }

                generateTrueColors(srcPath, res)
            }
        }
        project.tasks["preBuild"].dependsOn 'generateResFiles'

        project.tasks.create(name: "clearResCache") << {
            project.android.sourceSets.each { source ->
                Path srcPath = fs.getPath(root, "src", source.name)
                Path path = fs.getPath(srcPath.toString(), ".res-gen")
                Path cache = FileSystems.getDefault().getPath(srcPath.toString(), "res-gen", ".cache");

                try {
                    Files.deleteIfExists(cache);
                }
                catch (IOException e) { /* ... */
                }

                if (Files.exists(path)) {
                    deleteRecursively(path)
                }
            }
        }

        project.tasks["clean"].dependsOn 'clearResCache'
    }

    private void generateTrueColors(Path srcPath, Path gen) {
        def fs = FileSystems.getDefault();

        Map<String, String> colorMap = new HashMap<>()
        Map<String, String> dimenMap = new HashMap<>()
        Map<String, String> fontMap = new HashMap<>()
        Map<String, TrueColors.Font> styleMap = new HashMap<>()

        Path path = fs.getPath(srcPath.toString(), "res-gen");

        if (Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path asset, BasicFileAttributes attrs) throws IOException {
                    if (asset.toString().endsWith(".truecolors")) {
                        ZipFile zip = new ZipFile(asset.toString())
                        zip.entries().each { entry ->
                            def parts = entry.name.split(File.separator)
                            def fileName = parts[parts.length - 1]
                            if (parts.length > 2 && parts[parts.length - 2].equals("fonts")) {
                                File fonts = new File(fs.getPath(srcPath.toString(), "assets", "fonts").toString())
                                if (!fonts.exists()) {
                                    fonts.mkdirs()
                                }
                                File font = new File(fonts.toString(), fileName)
                                def out = new FileOutputStream(font);
                                out << zip.getInputStream(entry)
                            } else if (fileName.equals("flat-data.json")) {
                                def slurper = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY)
                                TrueColors data = slurper.parse(zip.getInputStream(entry))
                                data.colors.each { color ->
                                    def name = color.path.join("_")
                                    colorMap.put(name, "#" + color.rgba.substring(7, 9) + color.rgba.substring(1, 7))
                                }
                                data.metrics.each { metric ->
                                    def name = metric.path.join("_")
                                    dimenMap.put(name, "${metric.value}dp")
                                }
                                data.fonts.each { font ->
                                    def fontName = font.font_name.replaceAll(/\B[A-Z]/) { '_' + it }
                                            .replace("-", "_").toLowerCase()
                                    if (!fontMap.containsKey(fontName)) {
                                        fontMap.put(fontName, font.file_name)
                                    }
                                    def name = font.path.join("_")
                                    font.font_name = fontName
                                    styleMap.put(name, font)
                                }
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            })
        }

        def hasValues = styleMap.size() > 0 || fontMap.size() > 0 || colorMap.size() > 0 || dimenMap.size() > 0
        if (hasValues) {
            Path values = FileSystems.getDefault().getPath(gen.toString(), "values");
            if (Files.notExists(values)) Files.createDirectories(values)

            if (colorMap.size() > 0) {
                def writer = new StringWriter()
                def xml = new MarkupBuilder(writer)
                xml.resources() {
                    colorMap.each { name, color ->
                        xml.color(name: name, color)
                    }
                }

                File colors = new File(values.toString(), "colors.xml")
                new GroovyPrintStream(colors).print(writer.toString())
            }

            if (dimenMap.size() > 0) {
                def writer = new StringWriter()
                def xml = new MarkupBuilder(writer)
                xml.resources() {
                    dimenMap.each { name, dimen ->
                        xml.dimen(name: name, dimen)
                    }
                }

                File dimens = new File(values.toString(), "dimens.xml")
                new GroovyPrintStream(dimens).print(writer.toString())
            }

            if (fontMap.size() > 0) {
                def writer = new StringWriter()
                def xml = new MarkupBuilder(writer)
                xml.resources() {
                    fontMap.each { name, font ->
                        xml.string(name: name, "fonts/" + font)
                    }
                }

                File strings = new File(values.toString(), "strings.xml")
                new GroovyPrintStream(strings).print(writer.toString())
            }

            if (styleMap.size() > 0) {
                def writer = new StringWriter()
                def xml = new MarkupBuilder(writer)
                xml.resources() {
                    styleMap.each { name, font ->
                        def colorPath = font.color_path.join("_")
                        def metricPath = font.size_path.join("_")
                        xml.style(name: name) {
                            item(name: "android:textColor", "@color/" + colorPath)
                            item(name: "android:textSize", "@dimen/" + metricPath)
                            item(name: "fontPath", "@string/" + font.font_name)
                        }
                    }
                }

                File styles = new File(values.toString(), "styles.xml")
                new GroovyPrintStream(styles).print(writer.toString())
            }
        }
    }

    private void deleteRecursively(Path path) {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed; propagate exception
                    throw exc;
                }
            }
        });
    }


    private Path createFolder(String path, String qualifier) {
        Path folder = FileSystems.getDefault().getPath(path, "drawable-" + qualifier);
        if (Files.notExists(folder)) Files.createDirectories(folder)
        return folder;
    }

    def filtered(map, densities) {
        return map.findAll { densities.contains(it.key) }
    }

    private long generateAssets(Path output, String selector, Path file, long lastGenerated) {

        def fileName = file.fileName.toString().split("\\.")[0]

        //  load a pdf from a file
        File f = new File(file.toString());
        if (lastGenerated < f.lastModified()) {
            PDDocument document = PDDocument.load(f);
            PDFRenderer renderer = new PDFRenderer(document);

            PDRectangle cropBox = document.getPage(0).getCropBox();

            def list = filtered(types, project.resgen.densities)
            list.each { density, scale ->
                Path folder = createFolder(output.toString(), selector + density);
                fileName = fileName.toLowerCase().replace(" ", "_").replace("-", "_")
                String outputfile = String.format("%s/%s.png", folder, fileName);

                int width = Math.ceil(cropBox.getWidth() * scale);
                int height = Math.ceil(cropBox.getHeight() * scale);

                BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics g = bufferedImage.createGraphics();
                g.setBackground(new Color(0, 0, 0, 0));
                renderer.renderPageToGraphics(0, g, scale);
                g.dispose();

                File out = new File(outputfile);
                if (out.exists()) {
                    out.delete();
                }
                ImageIO.write(bufferedImage, "png", out);
            }

            document.close()
        }
        return f.lastModified()
    }

    static class ResGenExtension {
        String[] densities = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
    }
}



