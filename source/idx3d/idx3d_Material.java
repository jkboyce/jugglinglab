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

import java.io.*;
import java.net.URL;

public final class idx3d_Material
// Material description object
{
	int color=0;
	int transparency=0;
	int reflectivity=255;
	idx3d_Texture texture=null;
	idx3d_Texture envmap=null;
	boolean flat=false;
	boolean wireframe=false;
	boolean opaque=true;
	String texturePath=null;
	String envmapPath=null;
	public idx3d_TextureSettings textureSettings=null;
	public idx3d_TextureSettings envmapSettings=null;
	
	// Constructor
	
		public idx3d_Material()
		{
		}
		
		public idx3d_Material(int color)
		{
			setColor(color);
		}
		
		public idx3d_Material(idx3d_Texture t)
		{
			setTexture(t);
			reflectivity=255;
		}
		
		public idx3d_Material(URL docURL, String filename)
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
				importFromStream(new java.net.URL(base+file).openStream(),new URL(base));
			}
			catch (Exception e){System.err.println(e+"");}
		}
		
		public idx3d_Material(String filename)
		// Call from Application
		{	
			String base=filename.substring(0,filename.length()-(new File(filename).getName().length()));
			try{
				importFromStream(new FileInputStream(new File(filename)),base);
			}
			catch (Exception e){System.err.println(e+"");}
		}
		
		
	// Setters
	
		public void setTexture(idx3d_Texture t)
		{
			texture=t;
			if (texture!=null) texture.resize();
		}
		
		public void setEnvmap(idx3d_Texture env)
		{
			envmap=env;
			env.resize(256,256);
		}
		
		public void setColor(int c)
		{
			color=c;
		}
		
		public void setTransparency(int factor)
		{
			transparency=idx3d_Math.crop(factor,0,255);
			opaque=(transparency==0);
		}
		
		public void setReflectivity(int factor)
		{
			reflectivity=idx3d_Math.crop(factor,0,255);
		}
		
		public void setFlat(boolean flat)
		{
			this.flat=flat;
		}
		
		public void setWireframe(boolean wireframe)
		{
			this.wireframe=wireframe;
		}
		
	// Getters
		
		public idx3d_Texture getTexture()
		{
			return texture;
		}
		
		public idx3d_Texture getEnvmap()
		{
			return envmap;
		}
		
		public int getColor()
		{
			return color;
		}
		
		public int getTransparency()
		{
			return transparency;
		}
		
		public int getReflectivity()
		{
			return reflectivity;
		}
		
		public boolean isFlat()
		{
			return flat;
		}
		
		public boolean isWireframe()
		{
			return wireframe;
		}
		
	// Material import
	
		private void importFromStream(InputStream inStream, Object baseURL) throws IOException
		{
			DataInputStream in=new DataInputStream(new BufferedInputStream(inStream));
			readSettings(in);
			readTexture(in,baseURL,true);
			readTexture(in,baseURL,false);				
		}
		
		private void readSettings(DataInputStream in) throws IOException
		{
			setColor(in.readInt());
			setTransparency(in.readUnsignedByte());
			setReflectivity(in.readUnsignedByte());
			setFlat(in.readBoolean());
		}
		
		private void readTexture(DataInputStream in, Object baseURL, boolean textureId) throws IOException
		{
			idx3d_Texture t=null;
			int id=in.readByte();
			if (id==1) 
			{
				if (baseURL instanceof URL) t=new idx3d_Texture((URL)baseURL,in.readUTF());
				else t=new idx3d_Texture((String)baseURL+in.readUTF());
				
				if (t!=null && textureId)
				{
					texturePath=t.path;
					textureSettings=null;
					setTexture(t);
				}
				if (t!=null && !textureId)
				{
					envmapPath=t.path;
					envmapSettings=null;
					setEnvmap(t);
				}
			}
						
			if (id==2)
			{
				int w=in.readInt();
				int h=in.readInt();
				int type=in.readByte();
				float persistency=in.readFloat();
				float density=in.readFloat();
				int samples=in.readUnsignedByte();
				int numColors=in.readUnsignedByte();
				int[] colors=new int[numColors];
				for (int i=0;i<numColors;i++) colors[i]=in.readInt();
				
				if (type==1)
					t=idx3d_TextureFactory.PERLIN(w,h, persistency, density, samples, 1024).colorize(
					idx3d_Color.makeGradient(colors,1024));
				if (type==2)
					t=idx3d_TextureFactory.WAVE(w,h, persistency, density, samples, 1024).colorize(
					idx3d_Color.makeGradient(colors,1024));
				if (type==3)
					t=idx3d_TextureFactory.GRAIN(w,h, persistency, density, samples, 20, 1024).colorize(
					idx3d_Color.makeGradient(colors,1024));
					
				if (textureId)
				{
					texturePath=null;
					textureSettings=new idx3d_TextureSettings(t,w,h,type,persistency, density, samples,colors);
					setTexture(t);
				}
				else
				{
					envmapPath=null;
					envmapSettings=new idx3d_TextureSettings(t,w,h,type,persistency, density, samples,colors);
					setEnvmap(t);			
				}
			}	
		}		
}
	