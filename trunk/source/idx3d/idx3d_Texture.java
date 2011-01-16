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

import java.awt.Image;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.image.PixelGrabber;
import java.net.URL;

public class idx3d_Texture
// defines a texture
{
	// F I E L D S

		public int width;
		public int height;
		public int bitWidth;
		public int bitHeight;
		public int pixel[];
		
		public String path=null;

	// C O N S T R U C T O R S

		public idx3d_Texture(int w, int h)
		{
			height=h;
			width=w;
			pixel=new int[w*h];
			cls();
		}

		public idx3d_Texture(int w, int h, int data[])
		{
			height=h;
			width=w;
			pixel=new int[width*height];
			//System.arraycopy(data,0,pixel,0,width*height);
		}

		public idx3d_Texture(Image img)
		{
			loadTexture(img);
		}

		public idx3d_Texture(URL docURL, String filename)
		// Call from Applet
		{			
			int pos=0;
			String temp=docURL.toString();
			while (temp.indexOf("/",pos)>0) pos=temp.indexOf("/",pos)+1;
			temp=temp.substring(0,pos)+filename;
			while (temp.indexOf("/",pos)>0) pos=temp.indexOf("/",pos)+1;
			String file=temp.substring(pos);
			String base=temp.substring(0,pos);
			
			try{
				loadTexture(Toolkit.getDefaultToolkit().getImage(new URL(base+file)));
			}
			catch (Exception e){System.err.println(e+"");}
		}

		public idx3d_Texture(String filename)
		{
			path=new java.io.File(filename).getName();
			loadTexture(Toolkit.getDefaultToolkit().getImage(filename));
		}
		

	// P U B L I C   M E T H O D S

		public void resize()
		{
			double log2inv=1/Math.log(2);
			int w=(int)Math.pow(2,bitWidth=(int)(Math.log(width)*log2inv));
			int h=(int)Math.pow(2,bitHeight=(int)(Math.log(height)*log2inv));
			resize(w,h);
		}

		public void resize(int w, int h)
		{
			setSize(w,h);
		}

		public idx3d_Texture put(idx3d_Texture newData)
		// assigns new data for the texture
		{
			System.arraycopy(newData.pixel,0,pixel,0,width*height);
			return this;
		}

		public idx3d_Texture mix(idx3d_Texture newData)
		// mixes the texture with another one
		{
			for (int i=width*height-1;i>=0;i--)
				pixel[i]=idx3d_Color.mix(pixel[i],newData.pixel[i]);
			return this;
		}

		public idx3d_Texture add(idx3d_Texture additive)
		// additive blends another texture with this
		{
			for (int i=width*height-1;i>=0;i--)
				pixel[i]=idx3d_Color.add(pixel[i],additive.pixel[i]);
			return this;
		}

		public idx3d_Texture sub(idx3d_Texture subtractive)
		// subtractive blends another texture with this
		{
			for (int i=width*height-1;i>=0;i--)
				pixel[i]=idx3d_Color.sub(pixel[i],subtractive.pixel[i]);
			return this;
		}

		public idx3d_Texture inv()
		// inverts the texture
		{
			for (int i=width*height-1;i>=0;i--)
				pixel[i]=idx3d_Color.inv(pixel[i]);
			return this;
		}
		
		public idx3d_Texture multiply(idx3d_Texture multiplicative)
		// inverts the texture
		{
			for (int i=width*height-1;i>=0;i--)
				pixel[i]=idx3d_Color.multiply(pixel[i],multiplicative.pixel[i]);
			return this;
		}


		public void cls()
		// clears the texture
		{
			idx3d_Math.clearBuffer(pixel,0);
		}

		public idx3d_Texture toAverage()
		// builds the averidge of the channels
		{
			for (int i=width*height-1;i>=0;i--) 
				pixel[i]=idx3d_Color.getAverage(pixel[i]);
			return this;
		}

		public idx3d_Texture toGray()
		// converts this texture to gray
		{
			for (int i=width*height-1;i>=0;i--) 
				pixel[i]=idx3d_Color.getGray(pixel[i]);
			return this;
		}
		
		public idx3d_Texture valToGray()
		{
			int intensity;
			for (int i=width*height-1;i>=0;i--)
			{
				intensity=idx3d_Math.crop(pixel[i],0,255);
				pixel[i]=idx3d_Color.getColor(intensity,intensity,intensity);
			}
			
			return this;
		}
		
		public idx3d_Texture colorize(int[] pal)
		{
			int range=pal.length-1;
			for (int i=width*height-1;i>=0;i--)
				pixel[i]=pal[idx3d_Math.crop(pixel[i],0,range)];
			return this;
		}
		
		public static idx3d_Texture blendTopDown(idx3d_Texture top, idx3d_Texture down)
		{
			down.resize(top.width,top.height);
			idx3d_Texture t=new idx3d_Texture(top.width,top.height);
			int pos=0;
			int alpha;
			for (int y=0;y<top.height;y++)
			{
				alpha=255*y/(top.height-1);
				for (int x=0;x<top.width;x++)
				{
					t.pixel[pos]=idx3d_Color.transparency(down.pixel[pos],top.pixel[pos],alpha);
					pos++;
				}
			}
			return t;
		}		

	// P R I V A T E   M E T H O D S


		private void loadTexture(Image img)
		// grabbs the pixels out of an image
		{
			try
			{
				while (((width=img.getWidth(null))<0)||((height=img.getHeight(null))<0));

				pixel=new int[width*height];
				PixelGrabber pg=new PixelGrabber(img,0,0,width,height,pixel,0,width);
				pg.grabPixels();
			}
			catch (InterruptedException e) {}
		}

		private void setSize(int w, int h)
		// resizes the texture
		{
			int offset=w*h;
			int offset2;
			if (w*h!=0)
			{
				int newpixels[]=new int[w*h];
				for(int j=h-1;j>=0;j--)
				{
					offset-=w;
					offset2=(j*height/h)*width;
					for (int i=w-1;i>=0;i--)
						newpixels[i+offset]=pixel[(i*width/w)+offset2];
				}
				width=w; height=h; pixel=newpixels;
			}
		}

		private boolean inrange(int a, int b, int c)
		{
			return (a>=b)&(a<c);
		}
		
		public idx3d_Texture getClone()
		{
			idx3d_Texture t=new idx3d_Texture(width,height);
			idx3d_Math.copyBuffer(pixel,t.pixel);
			return t;
		}
		
}