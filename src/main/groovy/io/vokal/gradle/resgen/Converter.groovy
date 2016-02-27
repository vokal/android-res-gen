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

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.gradle.api.logging.Logging

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream
import java.awt.*
import java.awt.image.BufferedImage

/**
 * Converts PDFs to PNGs.
 *
 * This is split out into its own class to make it easier to test
 * (since it doesn't require any of the Task architecture).
 */
class Converter {

    /**
     * Transcodes an PDFResource into a PNG.
     *
     * @param pdfResource the input PDF
     * @param density the density to output the PNG; determines scaling
     * @param destination the output destination
     */
    void transcode(PDFResource pdfResource, Density density, File destination) {
        if (!pdfResource.canBeRead) {
            Logging.getLogger(this.class)
                    .warn("Cannot convert PDF: $pdfResource.file.name; file cannot be opened")
            return
        }

        int outWidth = Math.round(pdfResource.width * density.multiplier)
        int outHeight = Math.round(pdfResource.height * density.multiplier)

        PDDocument document = PDDocument.load(pdfResource.file);
        PDFRenderer renderer = new PDFRenderer(document);

        String format = "png"
        Color bg = new Color(0, 0, 0, 0)
        int bufferType = BufferedImage.TYPE_INT_ARGB
        if (pdfResource.jpg) {
            format = "jpg"
            bg = new Color(255, 255, 255)
            bufferType = BufferedImage.TYPE_INT_RGB
        }

        // get all image writers for format
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (writers.hasNext()) {
            ImageWriter writer = (ImageWriter) writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam()

            BufferedImage bufferedImage = new BufferedImage(outWidth, outHeight, bufferType);
            Graphics g = bufferedImage.createGraphics();
            g.setBackground(bg);
            renderer.renderPageToGraphics(0, g, density.multiplier);
            g.dispose();

            File out = new File(destination, "${pdfResource.getOutputName()}.${format}")
            ImageOutputStream ios = ImageIO.createImageOutputStream(out);
            writer.setOutput(ios);

            // compress to a given quality
            if ("jpg".equals(format)) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(pdfResource.jpgQualtiy);
            }

            // appends a complete image stream containing a single image and
            // associated stream and image metadata and thumbnails to the output
            try {
                writer.write(null, new IIOImage(bufferedImage, null, null), param);
                Logging.getLogger(this.class).info("Converted $pdfResource to $out")
            } catch (Exception e ) {
                Logging.getLogger(this.class).error('Could not transcode $pdfResource.file.name', e)
                destination.delete()
            } finally {
                // close all streams
                ios.flush()
                ios.close()
            }
            writer.dispose();
        }
        document.close()
    }

}