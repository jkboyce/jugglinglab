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

public final class idx3d_Math
// Singleton class for accelerated mathematical operations
{
	private static float sinus[];
	private static float cosinus[];
	private static boolean trig=false;
	public static float pi=3.1415926535f;
	private static float rad2scale=4096f/3.14159265f/2f;
	private static float pad=256*3.14159265f;
	
	private static int[] fastRandoms;
	private static int fastRndPointer=0;
	private static boolean fastRndInit=false;
	
	// A L L O W  NO  I N S T A N C E S
	
		private idx3d_Math() {}
	
	// T R I G O N O M E T R Y
	
		public static final float deg2rad(float deg)
		{
			return deg*0.0174532925194f;
		}
		
		public static final float rad2deg(float rad)
		{
			return rad*57.295779514719f;
		}
		
		public static final float sin(float angle)
		{
			if(!trig) buildTrig();
			return sinus[(int)((angle+pad)*rad2scale)&0xFFF];
		}
	
		public static final float cos(float angle)
		{
			if(!trig) buildTrig();
			return cosinus[(int)((angle+pad)*rad2scale)&0xFFF];
		}
	
		private static void buildTrig()
		{
			System.out.println(">> Building idx3d_Math LUT");
			sinus=new float[4096];
			cosinus=new float[4096];
		
			for (int i=0;i<4096;i++)
			{
				sinus[i]=(float)Math.sin((float)i/rad2scale);
				cosinus[i]=(float)Math.cos((float)i/rad2scale);
			}
			trig=true;
		}
		
		public static final float pythagoras(float a, float b)
		{
			return (float)Math.sqrt(a*a+b*b);
		}
		
		public static final int pythagoras(int a, int b)
		{
			return (int)Math.sqrt(a*a+b*b);
		}
		

	// R A N G E  T O O L S
	
		public static final int crop(int num, int min, int max)
		{
			return (num<min)?min:(num>max)?max:num;
		}
		
		public static final float crop(float num, float min, float max)
		{
			return (num<min)?min:(num>max)?max:num;
		}
		
		public static final boolean inrange(int num, int min, int max)
		{
			return ((num>=min)&&(num<max));
		}

	// B U F F E R   O P E R A T I O N S
	
		public static final void clearBuffer(int[] buffer, int value)
		{
			int size=buffer.length-1;
			int cleared=1;
			int index=1;
			buffer[0]=value;

			while (cleared<size)
			{
				System.arraycopy(buffer,0,buffer,index,cleared);
				size-=cleared;
				index+=cleared;
				cleared<<=1;
			}
			System.arraycopy(buffer,0,buffer,index,size);
		}
		
		public static final void cropBuffer(int[] buffer, int min, int max)
		{
			for (int i=buffer.length-1;i>=0;i--) buffer[i]=crop(buffer[i],min,max);
		}
		
		public static final void copyBuffer(int[] source, int[] target)
		{
			System.arraycopy(source,0,target,0,crop(source.length,0,target.length));
		}
		
		

	// R A N D O M  N U M B E R S
	
		public static final float random()
		{
			return (float)(Math.random()*2-1);
		}
	
		public static final float random(float min, float max)
		{
			return (float)(Math.random()*(max-min)+min);
		}
		
		public static final float randomWithDelta(float averidge, float delta)
		{
			return averidge+random()*delta;
		}
				
		public static final int fastRnd(int bits)
		{
			if (bits<1) return 0;
			fastRndPointer=(fastRndPointer+1)&31;
			if (!fastRndInit)
			{
				fastRandoms=new int[32];
				for (int i=0;i<32;i++) fastRandoms[i]=(int)random(0,0xFFFFFF);
				fastRndInit=true;
			}		
			return fastRandoms[fastRndPointer]&(1<<(bits-1));
		}
		
		public static final int fastRndBit()
		{
			return fastRnd(1);
		}
		
		public static final float interpolate(float a, float b, float d)
		{
			float f=(1-cos(d*pi))*0.5f;
			return a+f*(b-a);
		}
			
}