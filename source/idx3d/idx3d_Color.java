// | -----------------------------------------------------------------
// | idx3d III is (c)1999/2000 by Peter Walser
// | -----------------------------------------------------------------
// | idx3d is a 3d engine written in 100% pure Java (1.1 compatible)
// | and provides a fast and flexible API for software 3d rendering
// | on the Java platform.
// |
// | Feel free to use the idx3d API / classes / source code for
// | non-commercial purposes (of course on your own risk).
// | If you intend to use idx3d for commercial purposes, please
// | contact me with an e-mail [proxima@active.ch].
// |
// | Thanx & greetinx go to:
// | * Wilfred L. Guerin, 	for testing, bug report, and tons 
// |			of brilliant suggestions
// | * Sandy McArthur,	for reverse loops
// | * Dr. Douglas Lyons,	for mentioning idx3d1 in his book
// | * Hugo Elias,		for maintaining his great page
// | * the comp.graphics.algorithms people, 
// | 			for scientific concerns
// | * Tobias Hill,		for inspiration and awakening my
// |			interest in java gfx coding
// | * Kai Krause,		for inspiration and hope
// | * Incarom & Parisienne,	for keeping me awake during the 
// |			long coding nights
// | * Doris Langhard,	for being the sweetest girl on earth
// | * Etnica, Infinity Project, X-Dream and "Space Night"@BR3
// | 			for great sound while coding
// | and all coderz & scenerz out there (keep up the good work, ppl :)
// |
// | Peter Walser
// | proxima@active.ch
// | http://www2.active.ch/~proxima
// | "On the eigth day, God started debugging"
// | -----------------------------------------------------------------

package idx3d;

public final class idx3d_Color
// Faster Color handling for 24bit colors
{
	public static final int ALPHA=0xFF000000; // alpha mask
	public static final int RED=0xFF0000;  // red mask
	public static final int GREEN=0xFF00;  // green mask
	public static final int BLUE=0xFF;  // blue mask
	public static final int MASK7Bit=0xFEFEFF;  // mask for additive/subtractive shading
	public static final int MASK6Bit=0xFCFCFC;  // mask for additive/subtractive shading
	public static final int RGB=0xFFFFFF;  // rgb mask
	
	private static int pixel,color,overflow,scale,r,g,b;

	private idx3d_Color()
	//Allow no instances
	{
	}

	// PUBLIC STATIC METHODS
	
		public static int getRed(int c)
		// Returns the red channel of the given color
		{
			return (c&RED)>>16;
		}
	
		public static int getGreen(int c)
		// Returns the green channel of the given color
		{
			return (c&GREEN)>>8;
		}
	
		public static int getBlue(int c)
		// Returns the blue channel of the given color
		{
			return c&BLUE;
		}
	
		public static int getColor(int r, int g, int b)
		// Returns the color given by r,g,b as a packed 24bit-color
		{
			return ALPHA|(r<<16)|(g<<8)|b;
		}
		
		public static int getGray(int color)
		// Converts the color to gray
		{
			int r=((color&RED)>>16);
			int g=((color&GREEN)>>8);
			int b=(color&BLUE);
			int Y=(r*3+g*6+b)/10;
			return ALPHA|(Y<<16)|(Y<<8)|Y;
		}
		
		public static int getAverage(int color)
		// Returns the average of the color channels
		{
			return (((color&RED)>>16)+((color&GREEN)>>8)+(color&BLUE))/3;
		}
	
		public static int getCropColor(int r, int g, int b)
		{
			return ALPHA|(idx3d_Math.crop(r,0,255)<<16)|(idx3d_Math.crop(g,0,255)<<8)|idx3d_Math.crop(b,0,255);
		}
	
		public static int add(int color1, int color2)
		// Adds color1 and color2
		{
			pixel=(color1&MASK7Bit)+(color2&MASK7Bit);
			overflow=pixel&0x1010100;
			overflow=overflow-(overflow>>8);
			return ALPHA|overflow|pixel;
		}
	
		public static int sub(int color1, int color2)
		// Substracts color2 from color1
		{
			pixel=(color1&MASK7Bit)+(~color2&MASK7Bit);
			overflow=~pixel&0x1010100;
			overflow=overflow-(overflow>>8);
			return ALPHA|(~overflow&pixel);
		}
	
		public static int subneg(int color1, int color2)
		// Substracts the negative of color2 from color1
		{
			pixel=(color1&MASK7Bit)+(color2&MASK7Bit);
			overflow=~pixel&0x1010100;
			overflow=overflow-(overflow>>8);
			return ALPHA|(~overflow&pixel);
		}
	
		public static int inv(int color)
		// returns the inverse of the given color
		{
			return ALPHA|(~color);
		}
		
		public static int mix(int color1, int color2)
		// Returns the averidge color from 2 colors
		{
			return ALPHA|(((color1&MASK7Bit)>>1)+((color2&MASK7Bit)>>1));
		}
		
		public static int scale(int color, int factor)
		{
			if (factor==0) return 0;
			if (factor==255) return color;
			if (factor==127) return (color&0xFEFEFE)>>1;
			
			r=(((color>>16)&255)*factor)>>8;
			g=(((color>>8)&255)*factor)>>8;
			b=((color&255)*factor)>>8;
			return ALPHA|(r<<16)|(g<<8)|b;
		}
		
		public static int multiply(int color1, int color2)
		{
			if ((color1&RGB)==0) return 0;
			if ((color2&RGB)==0) return 0;
			r=(((color1>>16)&255)*((color2>>16)&255))>>8;
			g=(((color1>>8)&255)*((color2>>8)&255))>>8;
			b=((color1&255)*(color2&255))>>8;
			return ALPHA|(r<<16)|(g<<8)|b;
		}
		
		public static int transparency(int bkgrd, int color, int alpha)
		// alpha=0 : opaque , alpha=255: full transparent
		{
			if (alpha==0) return color;
			if (alpha==255) return bkgrd;
			if (alpha==127) return mix(bkgrd,color);
			
			r=(alpha*(((bkgrd>>16)&255)-((color>>16)&255))>>8)+((color>>16)&255);
			g=(alpha*(((bkgrd>>8)&255)-((color>>8)&255))>>8)+((color>>8)&255);
			b=(alpha*((bkgrd&255)-(color&255))>>8)+(color&255);
			
			return ALPHA|(r<<16)|(g<<8)|b;
			
		}
		
		public static int random(int color, int delta)
		{
			int r=(color>>16)&255;
			int g=(color>>8)&255;
			int b=color&255;
			r+=(int)(idx3d_Math.random()*(float)delta);
			g+=(int)(idx3d_Math.random()*(float)delta);
			b+=(int)(idx3d_Math.random()*(float)delta);
			return getCropColor(r,g,b);
		}
		
		public static final int random()
		{
			return (int)(Math.random()*16777216);
		}
			
		public static int[] makeGradient(int colors[], int size)
		{
			int[] pal=new int[size];
			int c1,c2,pos1,pos2,range;
			int r,g,b,r1,g1,b1,r2,g2,b2,dr,dg,db;
			if (colors.length==1)
			{
				c1=colors[0];
				for (int i=0;i<size;i++) pal[i]=c1;
				return pal;
			}
			
			for (int c=0;c<colors.length-1;c++)
			{
				c1=colors[c];
				c2=colors[c+1];
				pos1=size*c/(colors.length-1);
				pos2=size*(c+1)/(colors.length-1);
				range=pos2-pos1;
				r1=idx3d_Color.getRed(c1)<<16;
				g1=idx3d_Color.getGreen(c1)<<16;
				b1=idx3d_Color.getBlue(c1)<<16;
				r2=idx3d_Color.getRed(c2)<<16;
				g2=idx3d_Color.getGreen(c2)<<16;
				b2=idx3d_Color.getBlue(c2)<<16;
				dr=(r2-r1)/range;
				dg=(g2-g1)/range;
				db=(b2-b1)/range;
				r=r1;  g=g1;  b=b1;
				for (int i=pos1;i<pos2;i++)
				{
					pal[i]=getColor(r>>16,g>>16,b>>16);
					r+=dr;  g+=dg;  b+=db;
				}
			}
			return pal;
		}
}