package io.vokal.gradle.resgen


class TrueColors {
    Color[] colors
    Metric[] metrics
    Font[] fonts

    static class Color {
        String   name
        Color[]  values
        String[] path

        String   rgba
    }

    static class Metric {
        String   name
        Metric[] values
        String[] path

        int      value
    }

    static class Font {
        String   name
        Font[]   values
        String[] path

        String[] color_path
        String[] size_path

        String   font_name
        String   file_name
    }
}
