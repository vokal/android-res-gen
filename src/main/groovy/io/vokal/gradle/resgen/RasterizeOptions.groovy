package io.vokal.gradle.resgen

public class RasterizeOptions {

    String[] jpegPatterns = new String[0];
    float    jpegQuality  = 0.85f;

    String[] mipmapPatterns = new String[0];
    List<Density> mipmapDensities = [Density.HDPI,
                                     Density.XHDPI,
                                     Density.XXHDPI,
                                     Density.XXXHDPI]
}