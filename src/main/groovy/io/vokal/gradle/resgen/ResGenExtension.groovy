package io.vokal.gradle.resgen

public class ResGenExtension {
    String[] densities = ["hdpi", "xhdpi", "xxhdpi"]
    String[] jpeg;
    Integer jpegQuality;
    String[] mipmap;
    String[] mipmapDensities = ["hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
    Boolean useScalePixelDimens = true
}