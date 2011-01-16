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

import java.util.Vector;
import java.util.Enumeration;

public class idx3d_Vertex
// defines a triangle vertex
{
	// F I E L D S
		
		public idx3d_Object parent;
		
		public idx3d_Vector pos=new idx3d_Vector();   //(x,y,z) Coordinate of vertex
		public idx3d_Vector pos2;  //Transformed vertex coordinate
		public idx3d_Vector n=new idx3d_Vector();   //Normal Vector at vertex
		public idx3d_Vector n2;  //Transformed normal vector (camera space)

		public int x;  //Projected x coordinate
		public int y;  //Projected y coordinate
		public int z;  //Projected z coordinate for z-Buffer

		public float u=0; // Texture x-coordinate (relative)
		public float v=0; // Texture y-coordinate (relative)

		public int nx=0; // Normal x-coordinate for envmapping
		public int ny=0; // Normal y-coordinate for envmapping
		public int tx=0; // Texture x-coordinate (absolute)
		public int ty=0; // Texture y-coordinate (absolute)


		public boolean visible=true;  //visibility tag for clipping
		int clipcode=0;
		public int id; // Vertex index
		
		private float fact;
		private Vector neighbor=new Vector(); //Neighbor triangles of vertex


	// C O N S T R U C T O R S

		public idx3d_Vertex()
		{
			pos=new idx3d_Vector(0f,0f,0f);
		}

		public idx3d_Vertex(float xpos, float ypos, float zpos)
		{
			pos=new idx3d_Vector(xpos,ypos,zpos);
		}
		
		public idx3d_Vertex(float xpos, float ypos, float zpos, float u, float v)
		{
			pos=new idx3d_Vector(xpos,ypos,zpos);
			this.u=u;
			this.v=v;
		}

		public idx3d_Vertex(idx3d_Vector ppos)
		{
			pos=ppos.getClone();
		}
		
		public idx3d_Vertex(idx3d_Vector ppos, float u, float v)
		{
			pos=ppos.getClone();
			this.u=u;
			this.v=v;
		}

	// P U B L I C   M E T H O D S

		void project(idx3d_Matrix vertexProjection,idx3d_Matrix normalProjection, idx3d_Camera camera)
		// Projects this vertex into camera space
		{
			
			pos2=pos.transform(vertexProjection);
			n2=n.transform(normalProjection);

			fact=camera.screenscale/camera.fovfact/((pos2.z>0.1)?pos2.z:0.1f);
			x=(int)(pos2.x*fact+(camera.screenwidth>>1));
			y=(int)(-pos2.y*fact+(camera.screenheight>>1));
			z=(int)(65536f*pos2.z);
			nx=(int)(n2.x*127+127);
			ny=(int)(n2.y*127+127);
			if (parent.material==null) return;
			if (parent.material.texture==null) return;
			tx=(int)((float)parent.material.texture.width*u);
			ty=(int)((float)parent.material.texture.height*v);
		}
		
		public void setUV(float u, float v)
		{
			this.u=u;
			this.v=v;
		}
		
		void clipFrustrum(int w, int h)
		{
			// View plane clipping
			clipcode=0;
			if (x<0) clipcode|=1;
			if (x>=w) clipcode|=2;
			if (y<0) clipcode|=4;
			if (y>=h) clipcode|=8;
			if (pos2.z<0) clipcode|=16;
			visible=(clipcode==0);
		}

		void registerNeighbor(idx3d_Triangle triangle)
		// registers a neighbor triangle
		{
			if (!neighbor.contains(triangle)) neighbor.addElement(triangle);
		}
		
		void resetNeighbors()
		// resets the neighbors
		{
			neighbor.removeAllElements();
		}

		public void regenerateNormal()
		// recalculates the vertex normal
		{
			float nx=0;
			float ny=0;
			float nz=0;
			Enumeration enumer=neighbor.elements();
			idx3d_Triangle tri;
			idx3d_Vector wn;
			while (enumer.hasMoreElements())
			{
				tri=(idx3d_Triangle)enumer.nextElement();
				wn=tri.getWeightedNormal();
				nx+=wn.x;
				ny+=wn.y;
				nz+=wn.z;
			}
			n=new idx3d_Vector(nx,ny,nz).normalize();
		}
		
		public void scaleTextureCoordinates(float fx, float fy)
		{
			u*=fx;
			v*=fy;
		}
			
		public idx3d_Vertex getClone()
		{
			idx3d_Vertex newVertex=new idx3d_Vertex();
			newVertex.pos=pos.getClone();
			newVertex.n=n.getClone();
			newVertex.u=u;
			newVertex.v=v;
			return newVertex;
		}

		public String toString()
		{
			return new String("<vertex  x="+pos.x+" y="+pos.y+" z="+pos.z+" u="+u+" v="+v+">\r\n");
		}
		
		public boolean equals(idx3d_Vertex v)
		{
			return ((pos.x==v.pos.x)&&(pos.y==v.pos.y)&&(pos.z==v.pos.z));
		}
		
		public boolean equals(idx3d_Vertex v,float tolerance)
		{
			return Math.abs(idx3d_Vector.sub(pos,v.pos).length())<tolerance;
		}
		


}
