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

public class idx3d_Light extends idx3d_CoreObject
// defines a light in the scene
{
	// F I E L D S

		public idx3d_Vector v;               //Light direction
		public idx3d_Vector v2;             //projected Light direction
		public int diffuse=0;
		public int specular=0;
		public int highlightSheen=0;
		public int highlightSpread=0;
	
		idx3d_Matrix matrix2;


	// C O N S T R U C T O R S
	
		private idx3d_Light()
		// Default constructor not accessible
		{
		}		

		public idx3d_Light(idx3d_Vector direction)
		{
			v=direction.getClone();
			v.normalize();
		}

		public idx3d_Light(idx3d_Vector direction,int diffuse)
		{
			v=direction.getClone();
			v.normalize();
			this.diffuse=diffuse;
		}
		
		public idx3d_Light(idx3d_Vector direction, int color, int highlightSheen, int highlightSpread)
		{
			v=direction.getClone();
			v.normalize();
			this.diffuse=color;
			this.specular=color;
			this.highlightSheen=highlightSheen;
			this.highlightSpread=highlightSpread;
		}
		
		public idx3d_Light(idx3d_Vector direction, int diffuse, int specular, int highlightSheen, int highlightSpread)
		{
			v=direction.getClone();
			v.normalize();
			this.diffuse=diffuse;
			this.specular=specular;
			this.highlightSheen=highlightSheen;
			this.highlightSpread=highlightSpread;
		}


	// P U B L I C   M E T H O D S

		public void project(idx3d_Matrix m)
		{
			matrix2=matrix.getClone();
			matrix2.transform(m);
			v2=v.transform(matrix2);
		}
}