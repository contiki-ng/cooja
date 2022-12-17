/*
 * Copyright (c) 2022, RISE Research Institutes of Sweden AB.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.ui;
import java.awt.Color;

/**
 * Utility methods for managing colors.
 */
public class ColorUtils {

    /** Prevent instances of this class */
    private ColorUtils() {
    }

    /**
     * Decodes a string as a color and returns the corresponding opaque Color object.
     * This method handles standard color names such as black, blue, cyan, etc., and
     * colors specified as 24 bit integers.
     *
     * @param colorString a string representing a color.
     * @return a Color object or null if the method failed to decode the text as a color.
     * @see java.awt.Color#decode(String)
     */
    public static Color decodeColor(String colorString) {
        return colorString == null ? null : switch (colorString.toLowerCase()) {
            case "black" -> Color.black;
            case "blue" -> Color.blue;
            case "cyan" -> Color.cyan;
            case "darkgray" -> Color.darkGray;
            case "gray" -> Color.gray;
            case "green" -> Color.green;
            case "lightgray" -> Color.lightGray;
            case "magenta" -> Color.magenta;
            case "orange" -> Color.orange;
            case "pink" -> Color.pink;
            case "red" -> Color.red;
            case "white" -> Color.white;
            case "yellow" -> Color.yellow;
            default -> {
                try {
                    yield Color.decode(colorString);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }
}
