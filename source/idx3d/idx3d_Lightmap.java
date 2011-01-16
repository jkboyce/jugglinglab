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

public final class idx3d_Lightmap
// Lightmap for faster rendering, assuming static light sources
{
	int[] diffuse=new int[65536];
	int[] specular=new int[65536];
	private float[] sphere=new float[65536];
	private idx3d_Light[] light;
	private int lights;
	private int ambient;
	private int temp,overflow,color,pos,r,g,b;
	
	public idx3d_Lightmap(idx3d_Scene scene)
	{
		scene.rebuild();
		light=scene.light;
		lights=scene.lights;
		ambient=scene.environment.ambient;
		buildSphereMap();
		rebuildLightmap();
	}
	
	private void buildSphereMap()
	{
		float fnx,fny,fnz;
		int pos;
		for (int ny=-128;ny<128;ny++)
		{
			fny=(float)ny/128;
			for (int nx=-128;nx<128;nx++)
			{
				pos=nx+128+((ny+128)<<8);
				fnx=(float)nx/128;
				fnz=(float)(1-Math.sqrt(fnx*fnx+fny*fny));
				sphere[pos]=(fnz>0)?fnz:0;
			}
		}
	}
		
	
	public void rebuildLightmap()
	{
		System.out.println(">> Rebuilding Light Map  ...  ["+lights+" light sources]");
		idx3d_Vector l;
		float fnx,fny,angle,phongfact,sheen, spread;
		int diffuse,specular,cos,dr,dg,db,sr,sg,sb;
		for (int ny=-128;ny<128;ny++)
		{
			fny=(float)ny/128;
			for (int nx=-128;nx<128;nx++)
			{
				pos=nx+128+((ny+128)<<8);
				fnx=(float)nx/128;
				sr=sg=sb=0;
				dr=idx3d_Color.getRed(ambient);
				dg=idx3d_Color.getGreen(ambient);
				db=idx3d_Color.getBlue(ambient);
				for (int i=0;i<lights;i++)
				{		
					l=light[i].v;
					diffuse=light[i].diffuse;
					specular=light[i].specular;
					sheen=(float)light[i].highlightSheen/255f;
					spread=(float)light[i].highlightSpread/4096;
					spread=(spread<0.01f)?0.01f:spread;
					cos=(int)(255*idx3d_Vector.angle(light[i].v,new idx3d_Vector(fnx,fny,sphere[pos])));
					cos=(cos>0)?cos:0;
					dr+=(idx3d_Color.getRed(diffuse)*cos)>>8;
					dg+=(idx3d_Color.getGreen(diffuse)*cos)>>8;
					db+=(idx3d_Color.getBlue(diffuse)*cos)>>8;
					phongfact=sheen*(float)Math.pow((float)cos/255f,1/spread);
					sr+=(int)((float)idx3d_Color.getRed(specular)*phongfact);
					sg+=(int)((float)idx3d_Color.getGreen(specular)*phongfact);
					sb+=(int)((float)idx3d_Color.getBlue(specular)*phongfact);
				}
				this.diffuse[pos]=idx3d_Color.getCropColor(dr,dg,db);
				this.specular[pos]=idx3d_Color.getCropColor(sr,sg,sb);
			}
		}
	}
}
	