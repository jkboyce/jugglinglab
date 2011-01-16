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

public class idx3d_TextureFactory
// generates idx3d_Textures
{
	public final static float pi=3.1415926535f;
	public final static float deg2rad=pi/180;
	private static float[][] noiseBuffer;
	private static boolean noiseBufferInitialized=false;
	static int minx,maxx,miny,maxy;
	
	// C O N S T R U C T O R S
	
		private idx3d_TextureFactory() {} // Allow no instances
		
	// E X A M P L E   M A T E R I A L S
	
		public static idx3d_Texture SKY(int w, int h, float density)
		{
			int[] colors=new int[2];
			colors[0]=0x003399;
			colors[1]=0xFFFFFF;
			return PERLIN(w,h, 0.5f, 2.8f*density, 8, 1024).colorize(
				idx3d_Color.makeGradient(colors,1024));
		}
		
		public static idx3d_Texture MARBLE(int w, int h, float density)
		{
			int[] colors=new int[3];
			colors[0]=0x111111;
			colors[1]=0x696070;
			colors[2]=0xFFFFFF;
			return WAVE(w,h,0.5f,0.64f*density,6,1024).colorize(
				idx3d_Color.makeGradient(colors,1024));
		}
		
		public static idx3d_Texture WOOD(int w, int h, float density)
		{
			int[] colors=new int[3];
			colors[0]=0x332211;
			colors[1]=0x523121;
			colors[2]=0x996633;		
			
			return GRAIN(w,h, 0.5f, 3f*density, 3, 8, 1024).colorize(
				idx3d_Color.makeGradient(colors,1024));
		}
		
		public static idx3d_Texture RANDOM(int w, int h)
		{
			int nc=(int)idx3d_Math.random(2,6);
			int[] colors=new int[nc];
			for (int i=0;i<nc;i++)
				colors[i]=idx3d_Color.random();
			
			float persistency=idx3d_Math.random(0.4f,0.9f);
			float density=idx3d_Math.random(0.5f,3f);
			int samples=(int)idx3d_Math.random(1,7f);
			
			return PERLIN(w,h, persistency, density, samples, 1024).colorize(
				idx3d_Color.makeGradient(colors,1024));
		}
		
		public static idx3d_Texture CHECKERBOARD(int w, int h, int cellbits, int oddColor, int evenColor)
		{
			idx3d_Texture t=new idx3d_Texture(w,h);
			
			int pos=0;
			for (int y=0;y<h;y++)
				for (int x=0;x<w;x++)
					t.pixel[pos++]=(((x>>cellbits)+(y>>cellbits))&1)==0 ? evenColor : oddColor;
					
			return t;
		}

	// B A S E  T Y P E S
		
		public static idx3d_Texture PERLIN(int w, int h, float persistency, float density, int samples, int scale)
		{
			initNoiseBuffer();
			idx3d_Texture t=new idx3d_Texture(w,h);
			int pos=0;
			float wavelength=(float)((w>h)?w:h)/density;
			
			for(int y=0;y<h;y++)
				for(int x=0;x<w;x++)
					t.pixel[pos++]=(int)((float)scale*perlin2d(x,y,wavelength,persistency,samples));
			return t;
		}
		
		public static idx3d_Texture WAVE(int w, int h, float persistency, float density, int samples, int scale)
		{
			initNoiseBuffer();
			idx3d_Texture t=new idx3d_Texture(w,h);
			int pos=0;
			float wavelength=(float)((w>h)?w:h)/density;
			
			for(int y=0;y<h;y++)
				for(int x=0;x<w;x++)
					t.pixel[pos++]=(int)((double)scale*(Math.sin(32*perlin2d(x,y,wavelength,persistency,samples))*0.5+0.5));
			return t;
		}
		
		public static idx3d_Texture GRAIN(int w, int h, float persistency, float density, int samples, int levels, int scale)
		// TIP: For wooden textures
		{
			initNoiseBuffer();
			idx3d_Texture t=new idx3d_Texture(w,h);
			int pos=0;
			float wavelength=(float)((w>h)?w:h)/density;
			float perlin;
			
			for(int y=0;y<h;y++)
				for(int x=0;x<w;x++)
				{
					perlin=(float)levels*perlin2d(x,y,wavelength,persistency,samples);
					t.pixel[pos++]=(int)((float)scale*(perlin-(float)(int)perlin));
				}
			return t;
		}
	
	// Perlin noise functions
	
		private static float perlin2d(float x, float y, float wavelength, float persistence, int samples)
		{
			float sum=0;
			float freq=1f/wavelength;
			float amp=persistence;
			float range=0;
			
			for (int i=0;i<samples;i++)
			{
				sum+=amp*interpolatedNoise(x*freq,y*freq,i);
				range+=amp;
				amp*=persistence;
				freq*=2;
			}
			return idx3d_Math.crop(sum/persistence*0.5f+0.5f,0,1);
		}
		
	// Helper methods
		
		private static float interpolatedNoise(float x, float y, int octave)
		{
			int intx=(int)x;
			int inty=(int)y;
			float fracx=x-(float)intx;
			float fracy=y-(float)inty;
			
			float i1=idx3d_Math.interpolate(noise(intx,inty,octave),noise(intx+1,inty,octave),fracx);
			float i2=idx3d_Math.interpolate(noise(intx,inty+1,octave),noise(intx+1,inty+1,octave),fracx);
			
			return idx3d_Math.interpolate(i1,i2,fracy);
		}
		
		private static float smoothNoise(int x, int y, int o)
		{
			return	(noise(x-1, y-1,o)+noise(x+1, y-1,o)+noise(x-1, y+1,o)+noise(x+1, y+1,o))/16
				+(noise(x-1, y,o)+noise(x+1, y,o)+noise(x, y-1,o)+noise(x, y+1,o))/8
				+noise(x,y,o)/4;
		}
		
		private static float noise(int x, int y, int octave)
		{
			return noiseBuffer[octave&3][(x+y*57)&8191];
		}
		
		private static float noise(int seed, int octave)
		{
			int id=octave&3;
    			int n =(seed<<13)^seed;
			
			if (id==0) return (float)(1f- ((n * (n * n * 15731 + 789221) + 1376312589)&0x7FFFFFFF)*0.000000000931322574615478515625f);
			if (id==1) return (float)(1f- ((n * (n * n * 12497 + 604727) + 1345679039)&0x7FFFFFFF)*0.000000000931322574615478515625f);
			if (id==2) return (float)(1f- ((n * (n * n * 19087 + 659047) + 1345679627)&0x7FFFFFFF)*0.000000000931322574615478515625f);
			return (float)(1f- ((n * (n * n * 16267 + 694541) + 1345679501)&0x7FFFFFFF)*0.000000000931322574615478515625f);
		}
		
		private static void initNoiseBuffer()
		{
			if (noiseBufferInitialized) return;
			noiseBuffer=new float[4][8192];
			for (int octave=0;octave<4;octave++)
				for (int i=0;i<8192;i++)
					noiseBuffer[octave][i]=noise(i,octave);
			noiseBufferInitialized=true;
		}
				
}