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

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile

class ResGenPlugin implements Plugin<Project> {

    public static class ResGenExtension {
        String[] densities = ["hdpi", "xhdpi", "xxhdpi"]
        String[] jpeg;
        Integer jpegQuality;
        String[] mipmap;
        String[] mipmapDensities = ["hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
    }

    public static final String DIR = ".res-gen";

    def types = [
            ldpi   : 0.75,
            mdpi   : 1.0,
            hdpi   : 1.5,
            xhdpi  : 2.0,
            xxhdpi : 3.0,
            xxxhdpi: 4.0
    ]

    Project project;

    String[] jpegPatterns = new String[0];
    float    jpegQuality  = 0.85f;

    String[] mipmapPatterns = new String[0];

    void apply(Project project) {

        this.project = project
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

                    if (project.resgen.jpeg != null) {
                        jpegPatterns = new String[project.resgen.jpeg.length]
                        project.resgen.jpeg.eachWithIndex { wildcard, i ->
                            jpegPatterns[i] = wildcardToRegex(wildcard)
                        }
                        if (project.resgen.jpegQuality != null) {
                            jpegQuality = Math.min(100, Math.max(0, project.resgen.jpegQuality)) / 100.0f;
                        }
                    }

                    if (project.resgen.mipmap != null) {
                        mipmapPatterns = new String[project.resgen.mipmap.length]
                        project.resgen.mipmap.eachWithIndex { wildcard, i ->
                            mipmapPatterns[i] = wildcardToRegex(wildcard)
                        }
                    }

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

        ArrayList<TrueColors> trueColorsData = new ArrayList<TrueColors>()
        Map<String, String> fontMap = new TreeMap<>()

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
                                data.fonts.each { font ->
                                    def fontName = font.font_name
                                            .replaceAll(/\B[A-Z]/) { '_' + it }
                                            .replace("-", "_")
                                            .replace(" ", "_")
                                            .toLowerCase()
                                    fontMap.put(fontName, font.file_name)
                                    font.font_name = fontName
                                }
                                trueColorsData.add(data)
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            })
        }

        if (trueColorsData.size() > 0) {
            Path values = FileSystems.getDefault().getPath(gen.toString(), "values");
            if (Files.notExists(values)) Files.createDirectories(values)

            def writer = new StringWriter()
            def xml = new MarkupBuilder(writer)

            // write colors
            xml.resources() {
                trueColorsData.each { data ->
                    data.colors.each { color ->
                        def name = color.path.join("_")
                        xml.color(name: name, "#" + color.rgba.substring(7, 9) + color.rgba.substring(1, 7))
                    }
                }
            }
            File colors = new File(values.toString(), "true_colors.xml")
            new GroovyPrintStream(colors).print(writer.toString())

            // write dimens
            writer = new StringWriter()
            xml = new MarkupBuilder(writer)
            xml.resources() {
                trueColorsData.each { data ->
                    data.metrics.each { metric ->
                        def name = metric.path.join("_")
                        xml.dimen(name: name, "${metric.value}dp")
                    }
                }
            }
            File dimens = new File(values.toString(), "true_dimens.xml")
            new GroovyPrintStream(dimens).print(writer.toString())

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
                        def name = font.path.join("_")
                        if (font.color_path != null && font.size_path != null) {
                            def colorPath = font.color_path.join("_")
                            def metricPath = font.size_path.join("_")
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


    private Path createFolder(String path, String type, String qualifier) {
        Path folder = FileSystems.getDefault().getPath(path, type + "-" + qualifier);
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

            String format = "png"
            Color bg = new Color(0, 0, 0 , 0)
            int bufferType = BufferedImage.TYPE_INT_ARGB
            jpegPatterns.find { regex ->
                if (fileName.matches(regex)) {
                    format = "jpg"
                    bg = new Color(255, 255, 255)
                    bufferType = BufferedImage.TYPE_INT_RGB
                    return true
                }
                return false
            }

            // get all image writers for format
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
            if (writers.hasNext()) {
                ImageWriter writer = (ImageWriter) writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();


                String type = "drawable"
                def list = filtered(types, project.resgen.densities)
                mipmapPatterns.find { regex ->
                    if (fileName.matches(regex)) {
                        type = "mipmap"
                        if (project.resgen.mipmapDensities != null) {
                            list = filtered(types, project.resgen.mipmapDensities)
                        }
                        return true
                    }
                    return false
                }

                list.each { density, scale ->
                    Path folder = createFolder(output.toString(), type, selector + density);
                    fileName = fileName.toLowerCase().replace(" ", "_").replace("-", "_")
                    String outputfile = String.format("%s/%s.%s", folder, fileName, format);

                    int width = Math.ceil(cropBox.getWidth() * scale);
                    int height = Math.ceil(cropBox.getHeight() * scale);

                    BufferedImage bufferedImage = new BufferedImage(width, height, bufferType);
                    Graphics g = bufferedImage.createGraphics();
                    g.setBackground(bg);
                    renderer.renderPageToGraphics(0, g, scale);
                    g.dispose();

                    File out = new File(outputfile);
                    if (out.exists()) {
                        out.delete();
                    }

                    ImageOutputStream ios = ImageIO.createImageOutputStream(out);
                    writer.setOutput(ios);

                    // compress to a given quality
                    if ("jpg".equals(format)) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        println "$fileName.$format $jpegQuality"
                        param.setCompressionQuality(jpegQuality);
                    }

                    // appends a complete image stream containing a single image and
                    //associated stream and image metadata and thumbnails to the output
                    writer.write(null, new IIOImage(bufferedImage, null, null), param);

                    // close all streams
                    ios.close();
                }
                writer.dispose();
            }

            document.close()
        }
        return f.lastModified()
    }

    public static String wildcardToRegex(String wildcard) {
        StringBuffer s = new StringBuffer(wildcard.length())
        s.append('^')
        int len = wildcard.length()
        for (int i = 0; i < len; i++) {
            char c = wildcard.charAt(i)
            switch(c) {
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
        return(s.toString())
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