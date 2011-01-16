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

public class idx3d_Camera
{
	// F I E L D S

		public idx3d_Matrix matrix=new idx3d_Matrix();
		public idx3d_Matrix normalmatrix=new idx3d_Matrix();
	
		boolean needsRebuild=true;   // Flag indicating changes on matrix

		// Camera settings
		public idx3d_Vector pos=new idx3d_Vector(0f,0f,0f);
		public idx3d_Vector lookat=new idx3d_Vector(0f,0f,0f);
		public float roll=0f;
		
		// Made these variables public.  JKB
		public float fovfact;             // Field of View factor
		public int screenwidth;
		public int screenheight;
		public int screenscale;
		
	// C O N S T R U C T O R S

		public idx3d_Camera()
		{
			setFov(90f);
		}

		public idx3d_Camera(float fov)
		{
			setFov(fov);
		}

	// P U B L I C   M E T H O D S
	
		// Made this method public.  JKB
		public idx3d_Matrix getMatrix()
		{
			rebuildMatrices();
			return matrix;
		}
		
		// Made this method public.  JKB
		public idx3d_Matrix getNormalMatrix()
		{
			rebuildMatrices();
			return normalmatrix;
		}
		
		void rebuildMatrices()
		{
			if (!needsRebuild) return;
			needsRebuild=false;
		
			idx3d_Vector forward,up,right;
			
			forward=idx3d_Vector.sub(lookat,pos);
			up=new idx3d_Vector(0f,1f,0f);
			right=idx3d_Vector.getNormal(up,forward);
			up=idx3d_Vector.getNormal(forward,right);
			
			forward.normalize();
			up.normalize();
			right.normalize();
			
			normalmatrix=new idx3d_Matrix(right,up,forward);
			normalmatrix.rotate(0,0,roll);
			matrix=normalmatrix.getClone();
			matrix.shift(pos.x,pos.y,pos.z);
			
			normalmatrix=normalmatrix.inverse();
			matrix=matrix.inverse();
		}
		
		public void setFov(float fov)
		{
			fovfact=(float)Math.tan(idx3d_Math.deg2rad(fov)/2);
		}
		
		public void roll(float angle)
		{
			roll+=angle;
			needsRebuild=true;
		}

		public void setPos(float px, float py, float pz)
		{
			pos=new idx3d_Vector(px,py,pz);
			needsRebuild=true;
		}

		public void setPos(idx3d_Vector p)
		{
			pos=p;
			needsRebuild=true;
		}
	
		public void lookAt(float px, float py, float pz)
		{
			lookat=new idx3d_Vector(px,py,pz);
			needsRebuild=true;
		}

		public void lookAt(idx3d_Vector p)
		{
			lookat=p;
			needsRebuild=true;
		}

		public void setScreensize(int w, int h)
		{
			screenwidth=w;
			screenheight=h;
			screenscale=(w<h)?w:h;
		}
		
	// MATRIX MODIFIERS
	
		public final void shift(float dx, float dy, float dz)
		{
			pos=pos.transform(idx3d_Matrix.shiftMatrix(dx,dy,dz));
			lookat=lookat.transform(idx3d_Matrix.shiftMatrix(dx,dy,dz));
			needsRebuild=true;
			
		}
		
		public final void shift(idx3d_Vector v)
		{
			shift(v.x,v.y,v.z);

		}
	
		public final void rotate(float dx, float dy, float dz)
		{
			pos=pos.transform(idx3d_Matrix.rotateMatrix(dx,dy,dz));
			needsRebuild=true;
		}
		
		public final void rotate(idx3d_Vector v)
		{
			rotate(v.x,v.y,v.z);
		}
		
		public static idx3d_Camera FRONT()
		{
			idx3d_Camera cam=new idx3d_Camera();
			cam.setPos(0,0,-2f);
			return cam;
		}
		
		public static idx3d_Camera LEFT()
		{
			idx3d_Camera cam=new idx3d_Camera();
			cam.setPos(2f,0,0);
			return cam;
		}
		
		public static idx3d_Camera RIGHT()
		{
			idx3d_Camera cam=new idx3d_Camera();
			cam.setPos(-2f,0,0);
			return cam;
		}
		
		public static idx3d_Camera TOP()
		{
			idx3d_Camera cam=new idx3d_Camera();
			cam.setPos(0,-2f,0);
			return cam;
		}
		
			
}