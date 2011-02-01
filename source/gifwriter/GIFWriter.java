// GIFWriter.java
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

import java.util.Enumeration;
import java.util.Hashtable;
import java.io.*;
import java.awt.*;
import java.awt.image.*;


public class GIFWriter implements ImageObserver {
    private Image img;
    private int width;
    private int height;
    private boolean loadingdata;	// we need these flags since loading is
    private boolean loadingstarted;	// done from another thread
    private IOException iox;
    
    private Hashtable colorHash;
	private int defaultcolorkey;
    private int[] rgbPixels;
    
    private boolean gotwidthheight;
    private boolean gotcolormap;
    private boolean gotdata;
    

	public GIFWriter() {
	}
	
    public void setImage(Image img) {
		this.img = img;
		gotwidthheight = gotcolormap = gotdata = false;
		height = img.getHeight(this);
		width = img.getWidth(this);
		if (height != -1 && width != -1)
			gotwidthheight = true;
	}

		// If we want to know these before the image is in memory, then
		// we'll have to block.
	public synchronized int getWidth() throws IOException {
		while ( !gotwidthheight )
		    try {
				wait();
			} catch ( InterruptedException e ) {}
		if ( iox != null )
		    throw iox;
		return width;
	}
	
	public synchronized int getHeight() throws IOException {
		while ( !gotwidthheight )
		    try {
				wait();
			} catch ( InterruptedException e ) {}
		if ( iox != null )
		    throw iox;
		return height;
	}
	
					
    /// Write a single GIF to the output.  This blocks until the writing
    // is done, and does not close the output stream.
    // @param img The image to encode.
    // @param out The stream to write the GIF to.
    public void writeGIF(OutputStream out) throws IOException
	{
		if (!gotdata)				// skip if we already have
			loadData();
/*		if (!gotcolormap) {			// skip if we already have
			setColorMap(null);		// shouldn't be necessary
			doColorMap();
		}*/
		writeHeader(out);
		writeBody(out);
		writeTrailer(out);
//		stop();
	}

	public synchronized void loadData() throws IOException {
		gotdata = false;
		if (!gotwidthheight)
			getWidth();			// waits until dimensions available
		
		rgbPixels = null;
		rgbPixels = new int[width * height];

		PixelGrabber pg = new PixelGrabber(img, 0, 0, width, height,
							rgbPixels, 0, width);
		try {
			if (!pg.grabPixels())
				throw new IOException("grabPixels() unsuccessful");
		} catch (InterruptedException e) {
			throw new IOException("grabPixels() interrupted");
		}
			
		gotdata = true;
		img.getSource().removeConsumer(pg);
	}
	
	public void flushPixels() throws IOException {
		rgbPixels = null;
		gotdata = false;
		img.flush();		// flushes data cached by Image class
	}
	
	public void doColorMap() throws IOException {
		if (!gotdata)
			throw new IOException("Don't have image data");

	        // Put all the pixels into a hash table.
	    if (colorHash == null)
	    	colorHash = new Hashtable();
	    
		int index = colorHash.size();
		
        for ( int row = 0; row < height; ++row ) {
            int rowOffset = row * width;
            for ( int col = 0; col < width; ++col ) {
                int rgb = rgbPixels[rowOffset + col] & 0xffffff;

		        GIFEncoderHashitem item =
				    	(GIFEncoderHashitem) colorHash.get(Integer.valueOf(rgb));
		        if ( item == null ) {
				    if ( index < 256 ) {
                        item = new GIFEncoderHashitem(rgb, null, 1, index);
/*                        int red = (rgb >> 16) & 0xff;
                        int green = (rgb >> 8) & 0xff;
                        int blue = (rgb) & 0xff;
                        System.out.println("adding color "+index+" at (x="+col+",y="+row+"): r="+red+", g="+green+", b="+blue);
*/                        
                        ++index;
                        colorHash.put(Integer.valueOf(rgb), item);
                    } /* else
						throw new IOException( "Too many colors for a GIF" );*/
				} else
		            ++item.count;
	        }
        }
        
        gotcolormap = true;
	}
	
	public Hashtable getColorMap() {
		return colorHash;
	}
	
	public void setColorMap(Hashtable chash, int defaultcolorkey) {
		colorHash = chash;
		this.defaultcolorkey = defaultcolorkey;
		gotcolormap = (chash != null);
	}
	
	
	public void writeHeader(OutputStream outs) throws IOException {
		writeHeader(colorHash, width, height, outs);
	}
	
	
	public static void writeHeader(Hashtable colormap, int mywidth,
					int myheight, OutputStream outs) throws IOException {
		byte B;
		int i;
				// Figure out how many bits to use.
		int logColors;
		int index = colormap.size();
		
		if ( index <= 2 )
		    logColors = 1;
		else if ( index <= 4 )
		    logColors = 2;
		else if ( index <= 16 )
		    logColors = 4;
		else
		    logColors = 8;
	
		// Turn colors into colormap entries.
		int mapSize = 1 << logColors;
		byte[] reds = new byte[mapSize];
		byte[] grns = new byte[mapSize];
		byte[] blus = new byte[mapSize];
		for ( Enumeration e = colormap.elements(); e.hasMoreElements(); ) {
		    GIFEncoderHashitem item = (GIFEncoderHashitem) e.nextElement();
		    reds[item.index] = (byte) ( ( item.rgb >> 16 ) & 0xff );
		    grns[item.index] = (byte) ( ( item.rgb >>  8 ) & 0xff );
		    blus[item.index] = (byte) (   item.rgb         & 0xff );
		}
		
		// Write the Magic header
		Putbyte( (byte)'G', outs );
		Putbyte( (byte)'I', outs );
		Putbyte( (byte)'F', outs );
		Putbyte( (byte)'8', outs );
		Putbyte( (byte)'9', outs );
		Putbyte( (byte)'a', outs );
		//writeString( outs, "GIF89a" );
	
		// Write out the screen width and height
		Putword( mywidth, outs );
		Putword( myheight, outs );
	
		// Indicate that there is a global colour map
		B = (byte) 0x80;		// Yes, there is a color map
		// OR in the resolution
		B |= (byte) ( ( 8 - 1 ) << 4 );
		// Not sorted
		// OR in the Bits per Pixel
		B |= (byte) ( ( logColors - 1 ) );
	
		// Write it out
		Putbyte( B, outs );
	
		// Write out the Background colour
		Putbyte( (byte)0, outs );
	
		// Pixel aspect ratio - 1:1.
		//Putbyte( (byte) 49, outs );
		// Java's GIF reader currently has a bug, if the aspect ratio byte is
		// not zero it throws an ImageFormatException.  It doesn't know that
		// 49 means a 1:1 aspect ratio.  Well, whatever, zero works with all
		// the other decoders I've tried so it probably doesn't hurt.
		Putbyte( (byte) 0, outs );
	
		// Write out the Global Colour Map
		for ( i = 0; i < mapSize; i++ ) {
		    Putbyte( reds[i], outs );
		    Putbyte( grns[i], outs );
		    Putbyte( blus[i], outs );
		}
	}
	
	
	public void writeBody(OutputStream outs) throws IOException {
		if (!gotdata)
			throw new IOException("Don't have image data");
		if (!gotcolormap)
			throw new IOException("Don't have a colormap");
			
		int logColors;
		int index = colorHash.size();
		int InitCodeSize;
		
		if ( index <= 2 )
		    logColors = 1;
		else if ( index <= 4 )
		    logColors = 2;
		else if ( index <= 16 )
		    logColors = 4;
		else
		    logColors = 8;
		
		// Calculate number of bits we are expecting
		if (!gotwidthheight)
			getWidth();			// waits until dimensions are known
		CountDown = width * height;
	
		// The initial code size
		if ( logColors <= 1 )
		    InitCodeSize = 2;
		else
		    InitCodeSize = logColors;
	
		// Write an Image separator
		Putbyte( (byte) ',', outs );
	
		// Write the Image header
		Putword( 0, outs );
		Putword( 0, outs );
		Putword( this.width, outs );
		Putword( this.height, outs );
	
		Putbyte( (byte) 0x00, outs );
	
		// Write out the initial code size
		Putbyte( (byte) InitCodeSize, outs );
	
		// Set up the current x and y position
		curx = 0;
		cury = 0;
		
		// Go and actually compress the data
		compress( InitCodeSize+1, outs );
	
		// Write out a Zero-length packet (to end the series)
		Putbyte( (byte) 0, outs );
	}
	
	
	public static void writeTrailer(OutputStream outs) throws IOException {
		// Write the GIF file terminator
		Putbyte( (byte) ';', outs );
	}
	
	
		// Get width and height info asynchronously as they become
		// available.  This is from ImageObserver.
	public synchronized boolean imageUpdate(Image img, int infoflags,
						int x, int y, int width, int height) {
		boolean needmore = false;
		
		if ((infoflags & ImageObserver.ABORT) != 0) {
			iox = new IOException("Image aborted");
			gotwidthheight = true;
			return false;
		}
		
		if ((infoflags & ImageObserver.WIDTH) != 0)
			this.width = width;
		else
			needmore = true;
			
		if ((infoflags & ImageObserver.HEIGHT) != 0)
			this.height = height;
		else
			needmore = true;
		
		gotwidthheight = !needmore;
		if (gotwidthheight)
			notifyAll();
			
		return needmore;	
	}
		



    byte GetPixel( int x, int y ) throws IOException
	{
		GIFEncoderHashitem item =
		    (GIFEncoderHashitem) colorHash.get( rgbPixels[y*width+x] & 0xffffff );
		if ( item == null ) {
//            System.out.println("Could not get color at (x="+x+",y="+y+")");
			item = (GIFEncoderHashitem) colorHash.get( defaultcolorkey );
		}
		return (byte) item.index;
	}

/*    public static void writeString( OutputStream out, String str ) throws IOException {
        int len = str.length();
        byte[] buf = new byte[len];
        str.getBytes( 0, len, buf, 0 );
        out.write( buf );
    }*/

    int curx, cury;
    int CountDown;
    static final int EOF = -1;

    // Return the next pixel from the image
    int GIFNextPixel() throws IOException
	{
		byte r;
	
		if ( CountDown == 0 )
		    return EOF;
	
		--CountDown;
	
		r = GetPixel( curx, cury );
	
		if ( ++curx == this.width ) {
		    curx = 0;
			++cury;
	    }
	
		return r & 0xff;
	}

    // Write out a word to the GIF file
    public static void Putword( int w, OutputStream outs ) throws IOException
	{
		Putbyte( (byte) ( w & 0xff ), outs );
		Putbyte( (byte) ( ( w >> 8 ) & 0xff ), outs );
	}

    // Write out a byte to the GIF file
    public static void Putbyte( byte b, OutputStream outs ) throws IOException
	{
		outs.write( b );
	}
	
    // GIFCOMPR.C       - GIF Image compression routines
    //
    // Lempel-Ziv compression based on 'compress'.  GIF modifications by
    // David Rowley (mgardi@watdcsu.waterloo.edu)

    // General DEFINEs

    static final int BITS = 12;

    static final int HSIZE = 5003;		// 80% occupancy

    // GIF Image compression - modified 'compress'
    //
    // Based on: compress.c - File compression ala IEEE Computer, June 1984.
    //
    // By Authors:  Spencer W. Thomas      (decvax!harpo!utah-cs!utah-gr!thomas)
    //              Jim McKie              (decvax!mcvax!jim)
    //              Steve Davies           (decvax!vax135!petsd!peora!srd)
    //              Ken Turkowski          (decvax!decwrl!turtlevax!ken)
    //              James A. Woods         (decvax!ihnp4!ames!jaw)
    //              Joe Orost              (decvax!vax135!petsd!joe)

    int n_bits;				// number of bits/code
    int maxbits = BITS;			// user settable max # bits/code
    int maxcode;			// maximum code, given n_bits
    int maxmaxcode = 1 << BITS; // should NEVER generate this code

    final int MAXCODE( int n_bits )
	{
		return ( 1 << n_bits ) - 1;
	}

    int[] htab = new int[HSIZE];
    int[] codetab = new int[HSIZE];

    int hsize = HSIZE;		// for dynamic table sizing

    int free_ent = 0;			// first unused entry

    // block compression parameters -- after all codes are used up,
    // and compression rate changes, start over.
    boolean clear_flg = false;

    // Algorithm:  use open addressing double hashing (no chaining) on the
    // prefix code / next character combination.  We do a variant of Knuth's
    // algorithm D (vol. 3, sec. 6.4) along with G. Knott's relatively-prime
    // secondary probe.  Here, the modular division first probe is gives way
    // to a faster exclusive-or manipulation.  Also do block compression with
    // an adaptive reset, whereby the code table is cleared when the compression
    // ratio decreases, but after the table fills.  The variable-length output
    // codes are re-sized at this point, and a special CLEAR code is generated
    // for the decompressor.  Late addition:  construct the table according to
    // file size for noticeable speed improvement on small files.  Please direct
    // questions about this implementation to ames!jaw.

    int g_init_bits;

    int ClearCode;
    int EOFCode;

    void compress( int init_bits, OutputStream outs ) throws IOException {
		int fcode;
		int i;
		int c;
		int ent;
		int disp;
		int hsize_reg;
		int hshift;
	
		// Set up the globals:  g_init_bits - initial number of bits
		g_init_bits = init_bits;
	
		// Set up the necessary values
		clear_flg = false;
		n_bits = g_init_bits;
		maxcode = MAXCODE( n_bits );
	
		ClearCode = 1 << ( init_bits - 1 );
		EOFCode = ClearCode + 1;
		free_ent = ClearCode + 2;
	
		char_init();
	
		ent = GIFNextPixel();
	
		hshift = 0;
		for ( fcode = hsize; fcode < 65536; fcode *= 2 )
		    ++hshift;
		hshift = 8 - hshift;			// set hash code range bound
	
		hsize_reg = hsize;
		cl_hash( hsize_reg );	// clear hash table
	
		output( ClearCode, outs );
	
		outer_loop:
		while ( (c = GIFNextPixel()) != EOF ) {
		    fcode = ( c << maxbits ) + ent;
		    i = ( c << hshift ) ^ ent;		// xor hashing
	
		    if ( htab[i] == fcode ) {
				ent = codetab[i];
				continue;
			} else if ( htab[i] >= 0 ) {	// non-empty slot
				disp = hsize_reg - i;	// secondary hash (after G. Knott)
				if ( i == 0 )
					disp = 1;
				do {
					if ( (i -= disp) < 0 )
						i += hsize_reg;
		
					if ( htab[i] == fcode ) {
						ent = codetab[i];
						continue outer_loop;
					}
				} while ( htab[i] >= 0 );
			}
			
		    output( ent, outs );
		    ent = c;
		    if ( free_ent < maxmaxcode ) {
				codetab[i] = free_ent++;	// code -> hashtable
				htab[i] = fcode;
			} else
				cl_block( outs );
		}
		// Put out the final code.
		output( ent, outs );
		output( EOFCode, outs );
	}

    // output
    //
    // Output the given code.
    // Inputs:
    //      code:   A n_bits-bit integer.  If == -1, then EOF.  This assumes
    //              that n_bits =< wordsize - 1.
    // Outputs:
    //      Outputs code to the file.
    // Assumptions:
    //      Chars are 8 bits long.
    // Algorithm:
    //      Maintain a BITS character long buffer (so that 8 codes will
    // fit in it exactly).  Use the VAX insv instruction to insert each
    // code in turn.  When the buffer fills up empty it and start over.

    int cur_accum = 0;
    int cur_bits = 0;

    int masks[] = { 0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
		    0x001F, 0x003F, 0x007F, 0x00FF,
		    0x01FF, 0x03FF, 0x07FF, 0x0FFF,
		    0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF };

	void output( int code, OutputStream outs ) throws IOException {
		cur_accum &= masks[cur_bits];
	
		if ( cur_bits > 0 )
		    cur_accum |= ( code << cur_bits );
		else
		    cur_accum = code;
	
		cur_bits += n_bits;
	
		while ( cur_bits >= 8 ) {
		    char_out( (byte) ( cur_accum & 0xff ), outs );
		    cur_accum >>= 8;
		    cur_bits -= 8;
		}
	
		// If the next entry is going to be too big for the code size,
		// then increase it, if possible.
		if ( free_ent > maxcode || clear_flg ) {
			if ( clear_flg ) {
				maxcode = MAXCODE(n_bits = g_init_bits);
				clear_flg = false;
			} else {
				++n_bits;
				if ( n_bits == maxbits )
					maxcode = maxmaxcode;
				else
					maxcode = MAXCODE(n_bits);
			}
		}
	
		if ( code == EOFCode ) {
		    // At EOF, write the rest of the buffer.
		    while ( cur_bits > 0 ) {
				char_out( (byte) ( cur_accum & 0xff ), outs );
				cur_accum >>= 8;
				cur_bits -= 8;
			}
	
		    flush_char( outs );
		}
	}

    // Clear out the hash table

    // table clear for block compress
    void cl_block( OutputStream outs ) throws IOException {
		cl_hash( hsize );
		free_ent = ClearCode + 2;
		clear_flg = true;
	
		output( ClearCode, outs );
	}

    // reset code table
    void cl_hash( int hsize ) {
		for ( int i = 0; i < hsize; ++i )
	    	htab[i] = -1;
	}

    // GIF Specific routines

    // Number of characters so far in this 'packet'
    int a_count;

    // Set up the 'byte output' routine
    void char_init() {
		a_count = 0;
	}

    // Define the storage for the packet accumulator
    byte[] accum = new byte[256];

    // Add a character to the end of the current packet, and if it is 254
    // characters, flush the packet to disk.
    void char_out( byte c, OutputStream outs ) throws IOException
	{
	accum[a_count++] = c;
	if ( a_count >= 254 )
	    flush_char( outs );
	}

    // Flush the packet to disk, and reset the accumulator
    void flush_char( OutputStream outs ) throws IOException {
		if ( a_count > 0 ) {
		    outs.write( a_count );
		    outs.write( accum, 0, a_count );
		    a_count = 0;
		}
	}
}
