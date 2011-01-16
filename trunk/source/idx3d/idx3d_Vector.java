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

public class idx3d_Vector
// defines a 3d vector
{
	// F I E L D S
	
		public float x=0;      //Cartesian (default)
		public float y=0;      //Cartesian (default)
		public float z=0;      //Cartesian (default),Cylindric
		public float r=0;      //Cylindric
		public float theta=0;  //Cylindric


	// C O N S T R U C T O R S

		public idx3d_Vector ()
		{
		}

		public idx3d_Vector (float xpos, float ypos, float zpos)
		{
			x=xpos;
			y=ypos;
			z=zpos;
		}

	// P U B L I C   M E T H O D S

		public idx3d_Vector normalize()
		// Normalizes the vector
		{
			float dist=length();
			if (dist==0) return this;
			float invdist=1/dist;
			x*=invdist;
			y*=invdist;
			z*=invdist;
			return this;
		}
		
		public idx3d_Vector reverse()
		// Reverses the vector
		{	
			x=-x;
			y=-y;
			z=-z;
			return this;
		}
		
		public float length()
		// Lenght of this vector
		{	
			return (float)Math.sqrt(x*x+y*y+z*z);
		}

		public idx3d_Vector transform(idx3d_Matrix m)
		// Modifies the vector by matrix m 
		{
			float newx = x*m.m00 + y*m.m01 + z*m.m02+ m.m03;
			float newy = x*m.m10 + y*m.m11 + z*m.m12+ m.m13;
			float newz = x*m.m20 + y*m.m21 + z*m.m22+ m.m23;
			return new idx3d_Vector(newx,newy,newz);
		}

		public void buildCylindric()
		// Builds the cylindric coordinates out of the given cartesian coordinates
		{
			r=(float)Math.sqrt(x*x+y*y);
			theta=(float)Math.atan2(x,y);
		}

		public void buildCartesian()
		// Builds the cartesian coordinates out of the given cylindric coordinates
		{
			x=r*idx3d_Math.cos(theta);
			y=r*idx3d_Math.sin(theta);
		}

		public static idx3d_Vector getNormal(idx3d_Vector a, idx3d_Vector b)
		// returns the normal vector of the plane defined by the two vectors
		{
			return vectorProduct(a,b).normalize();
		}
		
		public static idx3d_Vector getNormal(idx3d_Vector a, idx3d_Vector b, idx3d_Vector c)
		// returns the normal vector of the plane defined by the two vectors
		{
			return vectorProduct(a,b,c).normalize();
		}
		
		public static idx3d_Vector vectorProduct(idx3d_Vector a, idx3d_Vector b)
		// returns a x b
		{
			return new idx3d_Vector(a.y*b.z-b.y*a.z,a.z*b.x-b.z*a.x,a.x*b.y-b.x*a.y);
		}
		
		public static idx3d_Vector vectorProduct(idx3d_Vector a, idx3d_Vector b, idx3d_Vector c)
		// returns (b-a) x (c-a)
		{
			return vectorProduct(sub(b,a),sub(c,a));
		}

		public static float angle(idx3d_Vector a, idx3d_Vector b)
		// returns the angle between 2 vectors
		{
			a.normalize();
			b.normalize();
			return (a.x*b.x+a.y*b.y+a.z*b.z);
		}
		
		public static idx3d_Vector add(idx3d_Vector a, idx3d_Vector b)
		// adds 2 vectors
		{
			return new idx3d_Vector(a.x+b.x,a.y+b.y,a.z+b.z);
		}
		
		public static idx3d_Vector sub(idx3d_Vector a, idx3d_Vector b)
		// substracts 2 vectors
		{
			return new idx3d_Vector(a.x-b.x,a.y-b.y,a.z-b.z);
		}
		
		public static idx3d_Vector scale(float f, idx3d_Vector a)
		// substracts 2 vectors
		{
			return new idx3d_Vector(f*a.x,f*a.y,f*a.z);
		}
		
		public static float len(idx3d_Vector a)
		// length of vector
		{
			return (float)Math.sqrt(a.x*a.x+a.y*a.y+a.z*a.z);
		}
		
		public static idx3d_Vector random(float fact)
		// returns a random vector
		{
			return new idx3d_Vector(fact*idx3d_Math.random(),fact*idx3d_Math.random(),fact*idx3d_Math.random());
		}
		
		public String toString()
		{
			return new String ("<vector x="+x+" y="+y+" z="+z+">\r\n");
		}

		public idx3d_Vector getClone()
		{
			return new idx3d_Vector(x,y,z);
		}

}