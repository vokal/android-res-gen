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
import org.apache.pdfbox.pdmodel.common.PDRectangle

/**
 * Automatically calculates the width and height of an PDF file.
 */
class PDFResource {

    private File file

    // Basic way to tell if we could read the file or not
    private boolean canBeRead = false

    // Data read from the file, if it exists
    private int width
    private int height

    boolean jpg = false
    float jpgQualtiy = 0.85f

    PDFResource(File file) {
        this.file = file

        readPDFInfo()
    }

    public String getName() {
        int suffixStart = file.name.lastIndexOf '.'
        return suffixStart == -1 ? file.name : file.name.substring(0, suffixStart)
    }

    public String getOutputName() {
        getName().replaceAll(/\B[a-z]\B[A-Z]/) { it[0] + '_' + it[1] }
                .replaceAll(/\W/, "_")
                .toLowerCase()
    }

    private void readPDFInfo() {
        if (!file.exists()) {
            return
        }

        PDDocument document = PDDocument.load(file);
        PDRectangle cropBox = document.getPage(0).getCropBox();

        width = cropBox.getWidth()
        height = cropBox.getHeight()

        canBeRead = true

        document.close()
    }
}