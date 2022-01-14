// GIFEncoderHashitem.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package gifwriter;

import java.awt.*;


public class GIFEncoderHashitem {
    public int rgb;
    public int count;
    public int index;
    public Color color;


    public GIFEncoderHashitem(int rgb, Color color, int count, int index) {
        this.rgb = rgb;
        this.count = count;
        this.index = index;
        this.color = color;
    }
}
