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


public class idx3d_FXLensFlare extends idx3d_FXPlugin
// Creates a lens flare
{
	public idx3d_Object flareObject;
	
	private int flares=0;
	private boolean zBufferSensitive=true;
	private idx3d_Texture[] flare;
	private float[] flareDist;
	
	// C O N S T R U C T O R S
	
		private idx3d_FXLensFlare(idx3d_Scene scene)
		{
			super(scene);
		}

		public idx3d_FXLensFlare(String name, idx3d_Scene scene, boolean zBufferSensitive)
		{
			super(scene);
			this.zBufferSensitive=zBufferSensitive;
			flareObject=new idx3d_Object();
			flareObject.addVertex(new idx3d_Vertex(1f,1f,1f));
			flareObject.rebuild();
			scene.addObject(name,flareObject);
		}
		
	// P R E S E T S
	
		public void preset1()
		{
			clear();
			addGlow(144,0x330099);
			addStrike(320,48,0x003366);
			addStrike(48,240,0x660033);
			addRing(120,0x660000);
			addRays(320, 32, 20, 0x111111);
			addSecs(12,100,64,0x6633cc,64);
		}
		
		public void preset2()
		{
			clear();
			addGlow(144,0x995500);
			addStrike(640,64,0xCC0000);
			addStrike(32,480,0x0000FF);
			addStrike(64,329,0x330099);
			addRing(160,0x990000);
			addRays(320, 24, 32, 0x332211);
			addSecs(12,100,64,0x333333,100);
			addSecs(12,60,40,0x336699,80);
		}
		
		public void preset3()
		{
			clear();
			addGlow(144,0x993322);
			addStrike(400,200,0xCC00FF);
			addStrike(480,32,0x0000FF);
			addRing(180,0x990000);
			addRays(240, 32, 48, 0x332200);
			addSecs(12,80,64,0x555555,50);
			addSecs(12,60,40,0x336699,80);
		}

	// I N T E R F A C E
	
		public void setPos(idx3d_Vector pos)
		{
			flareObject.vertex[0].pos=pos;
		}	
	
		public void clear()
		// Deletes all flares
		{
			flares=0;
			flare=null;
			flareDist=null;
		}

		public void addGlow(int size, int color)
		{
			addFlare(createGlow(size,size,color,256),0f);
		}
		
		public void addStrike(int width, int height, int color)
		{
			addFlare(createGlow(width,height,color,48),0f);
		}
		
		public void addRing(int size, int color)
		{
			addFlare(createRing(size,color),0f);
		}
		
		public void addRays(int size, int num, int rad, int color)
		{
			addFlare(createRays(size, num, rad, color),0f);
		}
		
		public void addSecs(int count, int averidgeSize, int sizeDelta, int averidgeColor, int colorDelta)
		{
			for (int i=0;i<count;i++) addFlare(createSec(averidgeSize,sizeDelta,averidgeColor,colorDelta),idx3d_Math.random(-0.5f,3f));
		}
					

		public void apply()
		// Applys the effect on the scene / screen
		{
			// Get projected coordinates
				int px=flareObject.vertex[0].x;
				int py=flareObject.vertex[0].y;
			
			// Determine visibility
				if (!flareObject.vertex[0].visible) return;
				if (zBufferSensitive&&(flareObject.vertex[0].z>scene.renderPipeline.zBuffer[px+py*screen.w])) return;

			// Apply the effect
			
				int cx=screen.width/2;
				int cy=screen.height/2;
				if (screen.antialias()) { px>>=1; py>>=1; }
				float dx=(float)(cx-px);
				float dy=(float)(cy-py);
				int posx,posy,xsize,ysize;
				float zoom;
								
				for (int i=0; i<flares;i++)
				{
					zoom=idx3d_Math.pythagoras(dx,dy)/idx3d_Math.pythagoras(cx,cy);
					zoom=(1-zoom)/2+1;
					xsize=flare[i].width;
					ysize=flare[i].height;
					posx=px+(int)(dx*flareDist[i]);
					posy=py+(int)(dy*flareDist[i]);
					screen.add(flare[i],posx-xsize/2,posy-ysize/2,xsize,ysize);
				}					
		}

	// P R I V A T E   M E T H O D S
	
		private void addFlare(idx3d_Texture texture, float relPos)
		{
			flares++;
			
			if (flares==1)
			{
				flare=new idx3d_Texture[1];
				flareDist=new float[1];
			}
			else
			{
				idx3d_Texture[] temp1=new idx3d_Texture[flares];
				System.arraycopy(flare,0,temp1,0,flares-1);
				flare=temp1;
				
				float[] temp2=new float[flares];
				System.arraycopy(flareDist,0,temp2,0,flares-1);
				flareDist=temp2;
			}
			
			flare[flares-1]=texture;
			flareDist[flares-1]=relPos;
		}
		
		private idx3d_Texture createRadialTexture(int w, int h, int[] colormap, int[] alphamap)
		{
			int offset;
			float relX,relY;
			idx3d_Texture newTexture=new idx3d_Texture(w,h);
			int[] palette=getPalette(colormap,alphamap);
			
			for(int y=h-1;y>=0;y--)
			{
				offset=y*w;
				for (int x=w-1;x>=0;x--)
				{
					relX=(float)(x-(w>>1))/(float)(w>>1);
					relY=(float)(y-(h>>1))/(float)(h>>1);
					newTexture.pixel[offset+x]=palette[idx3d_Math.crop((int)(255*Math.sqrt(relX*relX+relY*relY)),0,255)];
				}
			}
			return newTexture;			
		}
		
		private int[] getPalette(int[] color, int[] alpha)
		{
			int r,g,b;
			int[] palette=new int[256];
			for (int i=255;i>=0;i--)
			{
				r=(((color[i]>>16)&255)*alpha[i])>>8;
				g=(((color[i]>>8)&255)*alpha[i])>>8;
				b=((color[i]&255)*alpha[i])>>8;
				palette[i]=idx3d_Color.getColor(r,g,b);
			}
			return palette;
		}
		
		private idx3d_Texture createGlow(int w, int h, int color, int alpha)
		{
			return createRadialTexture(w,h,getGlowPalette(color),getConstantAlpha(alpha));
		}
		
		private idx3d_Texture createRing(int size, int color)
		{
			return createRadialTexture(size,size,getColorPalette(color,color),getRingAlpha(40));
		}
		
		private idx3d_Texture createSec(int size, int sizedelta, int color, int colordelta)
		{
			int s=(int)idx3d_Math.randomWithDelta(size,sizedelta);
			int c1=idx3d_Color.random(color,colordelta);
			int c2=idx3d_Color.random(color,colordelta);
			return createRadialTexture(s,s,getColorPalette(c1,c2),getSecAlpha());
		}
		
		private idx3d_Texture createRays(int size, int rays, int rad, int color)
		{
			int pos;	float relPos;
			idx3d_Texture texture=new idx3d_Texture(size,size);
			int[] radialMap=new int [1024];
			idx3d_Math.clearBuffer(radialMap, 0);
			for (int i=0;i<rays;i++)
			{
				pos=(int)idx3d_Math.random(rad,1023-rad);
				for (int k=pos-rad; k<=pos+rad; k++)
				{
					relPos=(float)(k-pos+rad)/(float)(rad*2);
					radialMap[k]+=(int)(255*(1+Math.sin((relPos-0.25)*3.14159*2))/2);
				}
			}
			int angle,offset,reldist;
			float xrel,yrel;
			for (int y=size-1;y>=0;y--)
			{
				offset=y*size;
				for (int x=size-1;x>=0;x--)
				{
					xrel=(float)(2*x-size)/(float)size;
					yrel=(float)(2*y-size)/(float)size;
					angle=(int)(1023*Math.atan2(xrel,yrel)/3.14159/2)&1023;
					reldist=Math.max((int)(255-255*idx3d_Math.pythagoras(xrel,yrel)),0);
					texture.pixel[x+offset]=idx3d_Color.scale(color,radialMap[angle]*reldist/255);
				}
			}
			return texture;
		}
		
		private int[] getGlowPalette(int color)
		{
			int r,g,b;
			float relDist,diffuse,specular;
			int[] palette=new int[256];
			int cr=(color>>16)&255;
			int cg=(color>>8)&255;
			int cb=color&255;
			for (int i=255;i>=0;i--)
			{
				relDist=(float)i/255;
				diffuse=(float)Math.cos(relDist*1.57);
				specular=(float)(255/Math.pow(2.718,relDist*2.718)-(float)i/16);
				r=(int)((float)cr*diffuse+specular);
				g=(int)((float)cg*diffuse+specular);
				b=(int)((float)cb*diffuse+specular);
				palette[i]=idx3d_Color.getCropColor(r,g,b);
			}
			return palette;
		}
		
		private int[] getConstantAlpha(int alpha)
		{
			int[] alphaPalette=new int[256];
			for (int i=255;i>=0;i--) alphaPalette[i]=alpha;
			return alphaPalette;
		}
		
		private int[] getLinearAlpha()
		{
			int[] alphaPalette=new int[256];
			for (int i=255;i>=0;i--) alphaPalette[i]=255-i;
			return alphaPalette;
		}
		
		private int[] getRingAlpha(int ringsize)
		{
			int[] alphaPalette=new int[256];
			float angle;
			for (int i=0;i<256;i++) alphaPalette[i]=0;
			for (int i=0;i<ringsize;i++)
			{
				angle=3.14159f/180*(float)(180*i/ringsize);				
				alphaPalette[255-ringsize+i]=(int)(64*Math.sin(angle));
			}
			return alphaPalette;
		}
		
		private int[] getSecAlpha()
		{
			int[] alphaPalette=getRingAlpha((int)idx3d_Math.random(0,255));
			for (int i=0;i<256;i++) alphaPalette[i]=(alphaPalette[i]+255-i)>>2;
			return alphaPalette;
		}
		
		
		private int[] getColorPalette(int color1, int color2)
		{
			int[] palette=new int[256];
			int r1=(color1>>16)&255;
			int g1=(color1>>8)&255;
			int b1=color1&255;
			int r2=(color2>>16)&255;
			int g2=(color2>>8)&255;
			int b2=color2&255;
			int dr=r2-r1;
			int dg=g2-g1;
			int db=b2-b1;
			int r=r1<<8;
			int g=g1<<8;
			int b=b1<<8;

			for (int i=0;i<256;i++)
			{
				palette[i]=idx3d_Color.getColor(r>>8,g>>8,b>>8);
				r+=dr; g+=dg; b+=db;
			}
			return palette;
		}
		
		
			

}