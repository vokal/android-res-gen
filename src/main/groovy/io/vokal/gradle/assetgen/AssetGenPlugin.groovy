package io.vokal.gradle.assetgen

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

import groovy.json.*

import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFPage;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import javax.imageio.ImageIO;

import javax.inject.Inject

class AssetGenPlugin implements Plugin<Project> {
    public static final String DIR = ".res-gen";

    def types = [
        ldpi:0.75, 
        mdpi: 1.0, 
        hdpi: 1.5, 
        xhdpi: 2.0,
        xxhdpi: 3.0,
        xxxhdpi: 4.0
        ]

    Project project;
    void apply(Project project) {

        this.project = project
        def hasApp = project.hasProperty('android')
        def root = project.getProjectDir().getAbsolutePath();
        def fs = FileSystems.getDefault();

        project.extensions.create("resgen", AssetGenExtension)
        project.tasks.create(name: "generateResFiles") << { 

            project.android.sourceSets.each { source ->

                Path srcPath =  fs.getPath(root, "src", source.name)
                Path path = fs.getPath(srcPath.toString(), "res-pdf");
                Path res = FileSystems.getDefault().getPath(srcPath.toString(), DIR);

                // Always add incase there files are removed but generated is kept
                source.res.srcDirs += res.toString()

                if (Files.exists(path)) {
                    Path cache = FileSystems.getDefault().getPath(path.toString(), ".cache");
                    if (Files.notExists(cache)) {
                        Files.createFile(cache)
                        Files.write(cache, "{}".getBytes());
                    }

                    def slurper = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY)
                    Map meta = new HashMap((Map) slurper.parse(cache.toFile()));

                    Files.walkFileTree(path,  new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                            if (!filePath.toString().endsWith(".cache")) {
                                Long t = (Long) meta[filePath.fileName.toString()]
                                long timestamp = t == null ? 0 : t
                                meta[filePath.fileName.toString()] = (Long) generateAssets(res, filePath, timestamp)
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    })
                    Files.write(cache, JsonOutput.toJson(meta).getBytes())
                }
            }
        }
        project.tasks["preBuild"].dependsOn 'generateResFiles'

        project.tasks.create(name: "clearResCache") << { 
            project.android.sourceSets.each { source ->
                Path srcPath =  fs.getPath(root, "src", source.name)
                Path path = fs.getPath(srcPath.toString(), ".res-gen")
                Path cache = FileSystems.getDefault().getPath(srcPath.toString(), "res-pdf", ".cache");

                try { Files.deleteIfExists(cache); }
                catch(IOException e) { /* ... */ }

                if(Files.exists(path)) {
                    deleteRecursively(path)
                }
            }
        }

        project.tasks["clean"].dependsOn 'clearResCache'
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


    private Path createFolder(Path path, String density) {
        Path folder = FileSystems.getDefault().getPath(path.toString(), "drawable-" + density);
        if (Files.notExists(folder)) Files.createDirectories(folder)
        return folder;
    }

    def filtered(map, densities) {
        return map.findAll { densities.contains(it.key) }
    }

    private long generateAssets(Path output, Path file, long lastGenerated) {
        def fileName = file.fileName.toString().split("\\.")[0]

        //  load a pdf from a file
        File f = new File(file.toString());
        if (lastGenerated < f.lastModified()) {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            ReadableByteChannel ch = Channels.newChannel(new FileInputStream(f));
     
            FileChannel channel = raf.getChannel();
            ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            PDFFile pdffile = new PDFFile(buf);
            PDFPage page = pdffile.getPage(0);

            //  create new image
            Rectangle rect = page.getBBox().getBounds();

            def list = filtered(types, project.resgen.densities)
            list.each { density, scale ->
                Path folder = createFolder(output, density);
                String outputfile = String.format("%s/%s.png", folder, fileName); //Output File name

                int width = rect.width * scale;
                int height = rect.height * scale;

                Image img = page.getImage(width, height, null, null, false, true);

                BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics g = bufferedImage.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();

                File out = new File(outputfile);
                if (out.exists()) {
                    out.delete();
                }
                ImageIO.write(bufferedImage, "png", out);
            }
        }
        return f.lastModified()
    }
}

class AssetGenExtension {
    String[] densities = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
}
