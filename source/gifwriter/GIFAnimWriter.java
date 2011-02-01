// GIFAnimWriter.java
//
// Copyright 2003 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/


package gifwriter;

import java.util.Hashtable;
import java.util.Vector;
import java.io.*;
import java.awt.*;


public class GIFAnimWriter {
	int iterations;
	int maxwidth, maxheight;
	Vector frames, delays;
	
	boolean colormap_valid = false;
	Hashtable colormap;
	int defaultcolorkey;
	

		// The iterations parameter specifies the number of repetitions for the
		// animation, from 1 to 65535.  Using 0 loops infinitely.
    public GIFAnimWriter(int iterations) {
		this.iterations = iterations;
		colormap = null;
		colormap_valid = false;
	}

	public GIFAnimWriter() {
		this(0);
	}
	
	// This version allows us to manually add to the colormap
	public void doColorMap(Color color, boolean defaultcolor) throws IOException {
		if (colormap == null)
			colormap = new Hashtable();
		
		int index = colormap.size();
		int rgb = color.getRGB();

		GIFEncoderHashitem item =
						(GIFEncoderHashitem)colormap.get(Integer.valueOf(rgb));
		if (item == null) {
			if (index >= 256)
				throw new IOException( "Too many colors for a GIF" );
			item = new GIFEncoderHashitem(rgb, color, 1, index);
			++index;
			colormap.put(Integer.valueOf(rgb), item);
		} else
			++item.count;
			
		if (defaultcolor)
			this.defaultcolorkey = rgb;
	}
    
	// This version constructs a colormap by looking at the contents of an image.
	public void doColorMap(Image img) throws IOException {
		GIFWriter gw = new GIFWriter();
        gw.setImage(img);
		
		if (!colormap_valid) {
				// Create new color table
			maxwidth = gw.getWidth();
			maxheight = gw.getHeight();
			gw.loadData();
			gw.doColorMap();
			colormap = gw.getColorMap();
			colormap_valid = true;
		} else {
				// Add to existing one
			int temp;
			
			if ((temp = gw.getWidth()) > maxwidth)
				maxwidth = temp;
			if ((temp = gw.getHeight()) > maxheight)
				maxheight = temp;
				
			gw.loadData();
			gw.setColorMap(colormap, defaultcolorkey);
			gw.doColorMap();
		}
		
		gw.flushPixels();
	}
	
/*	
	public void doColorMap(Color[] colorarray) throws IOException {
		for (int i = 0; i < colorarray.length; i++)
			doColorMap(colorarray[i], false);
	}
	
	public void doColorMap(Color color) throws IOException {
		doColorMap(color, false);
	}
*/
	
	public Hashtable getColorMap() {
		return this.colormap;
	}
	
	public void writeHeader(OutputStream out) throws IOException {
			// write out the header.  Use the allocated GIFWriter to do this.
			// note that it contains the proper colormap info.
		GIFWriter.writeHeader(colormap, maxwidth, maxheight, out);
		
			// write Netscape looping extension.
		GIFWriter.Putbyte((byte)0x21, out);
		GIFWriter.Putbyte((byte)0xff, out);
		GIFWriter.Putbyte((byte)0x0b, out);
		GIFWriter.Putbyte((byte)'N', out);
		GIFWriter.Putbyte((byte)'E', out);
		GIFWriter.Putbyte((byte)'T', out);
		GIFWriter.Putbyte((byte)'S', out);
		GIFWriter.Putbyte((byte)'C', out);
		GIFWriter.Putbyte((byte)'A', out);
		GIFWriter.Putbyte((byte)'P', out);
		GIFWriter.Putbyte((byte)'E', out);
		GIFWriter.Putbyte((byte)'2', out);
		GIFWriter.Putbyte((byte)'.', out);
		GIFWriter.Putbyte((byte)'0', out);
		GIFWriter.Putbyte((byte)0x03, out);
		GIFWriter.Putbyte((byte)0x01, out);
		GIFWriter.Putword(iterations, out);
		GIFWriter.Putbyte((byte)0x00, out);
	}
	
	
	public void writeDelay(int delay, OutputStream out) throws IOException {
			// write out the delay graphic control extension
		GIFWriter.Putbyte((byte)0x21, out);
		GIFWriter.Putbyte((byte)0xf9, out);
		GIFWriter.Putbyte((byte)0x04, out);
		GIFWriter.Putbyte((byte)0x00, out);
		GIFWriter.Putword(delay, out);
		GIFWriter.Putbyte((byte)0x00, out);
		GIFWriter.Putbyte((byte)0x00, out);
	}
	
	public void writeGIF(Image img, OutputStream out) throws IOException {
		GIFWriter gw = new GIFWriter();
		gw.setImage(img);
				// write out the actual image data
		gw.loadData();
		gw.setColorMap(colormap, defaultcolorkey);
		gw.writeBody(out);
		gw.flushPixels();
	}
	
	public void writeTrailer(OutputStream out) throws IOException {
		GIFWriter.writeTrailer(out);
	}
	
			// Add a frame to the movie.
/*	public void addFrame(Image img, int delay) throws IOException
	{
		frames.addElement(img);
		delays.addElement(new Integer(delay));
		colormap_valid = false;
	}
	
		// Write out the movie.
	public void writeGIFAnim(OutputStream out) throws IOException
	{
		int i, temp;
		int numframes = frames.size();
			// We want to do the work in two passes through the image frames.
			// On the first pass we build the global colormap.
			// Keep this colormap around in the future, in case we want to
			// write out again.
			// The second pass actually encodes and writes the data.
		Image[] img = new Image[numframes];
		for (i = 0; i < numframes; i++)
			img[i] = (Image)frames.elementAt(i);
		
		if (!colormap_valid) {
				// Build up the color table
			for (i = 0; i < numframes; i++)
				doColorMap(img[i]);
		}
		
		writeHeader(out);
		
			// write out the individual frames
		for (i = 0; i < numframes; i++) {
			writeDelay(((Integer)delays.elementAt(i)).intValue(), out);
			writeGIF(img[i], out);
		}
		
		writeTrailer(out);
	}*/
	
	
}
