package io.vokal.gradle.assetgen

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

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
        ldpi:0.5, 
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
        project.extensions.create("io.vokal.resgen", AssetGenExtension)

        def root = project.getProjectDir().getAbsolutePath();
        def fs = FileSystems.getDefault();

        project.android.sourceSets.each { source ->

            Path srcPath =  fs.getPath(root, "src", source.name)
            Path path = fs.getPath(srcPath.toString(), "res-pdf");
            Path res = FileSystems.getDefault().getPath(srcPath.toString(), DIR);

            // Always add incase there files are removed but generated is kept
            source.res.srcDirs += res.toString()

            if (Files.exists(path)) {
                Files.walkFileTree(path,  new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                        generateAssets(res, filePath)
                        return FileVisitResult.CONTINUE;
                    }
                })
            }
        }
    }

    private Path createFolder(Path path, String density) {
        Path folder = FileSystems.getDefault().getPath(path.toString(), "drawable-" + density);
        if (Files.notExists(folder)) Files.createDirectories(folder)
        return folder;
    }

    private void generateAssets(Path output, Path file) {
        def fileName = file.fileName.toString().split("\\.")[0]

        //  load a pdf from a file
        File f = new File(file.toString());
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        ReadableByteChannel ch = Channels.newChannel(new FileInputStream(f));
 
        FileChannel channel = raf.getChannel();
        ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        PDFFile pdffile = new PDFFile(buf);
        PDFPage page = pdffile.getPage(0);

        //  create new image
        Rectangle rect = page.getBBox().getBounds();

        types.each { density, scale ->
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
}

class AssetGenExtension {
}
