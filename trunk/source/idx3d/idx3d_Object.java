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

public class idx3d_Object extends idx3d_CoreObject
{
	// F I E L D S
	
		public Object userData=null;	// Can be freely used
		public String user=null; 	// Can be freely used

		public Vector vertexData=new Vector();
		public Vector triangleData=new Vector();
		
		public int id;  // This object's index
		public String name="";  // This object's name
		public boolean visible=true; // Visibility tag
		
		public idx3d_Scene parent=null;
		private boolean dirty=true;  // Flag for dirty handling
		
		idx3d_Vertex[] vertex;
		idx3d_Triangle[] triangle;
		
		public int vertices=0;
		public int triangles=0;
		
		public idx3d_Material material=null; 

	// C O N S T R U C T O R S

		public idx3d_Object()
		{
		}

	// D A T A  S T R U C T U R E S
	
		public idx3d_Vertex vertex(int id)
		{
			return (idx3d_Vertex) vertexData.elementAt(id);
		}
		
		public idx3d_Triangle triangle(int id)
		{
			return (idx3d_Triangle) triangleData.elementAt(id);
		}		

		public void addVertex(idx3d_Vertex newVertex)
		{
			newVertex.parent=this;
			vertexData.addElement(newVertex);
			dirty=true;
		}

		public void addTriangle(idx3d_Triangle newTriangle)
		{
			newTriangle.parent=this;
			triangleData.addElement(newTriangle);
			dirty=true;
		}
		
		public void addTriangle(int v1, int v2, int v3)
		{
			addTriangle(vertex(v1),vertex(v2),vertex(v3));
		}
		
		public void removeVertex(idx3d_Vertex v)
		{
			vertexData.removeElement(v);
		}
		
		public void removeTriangle(idx3d_Triangle t)
		{
			triangleData.removeElement(t);
		}
		
		public void removeVertexAt(int pos)
		{
			vertexData.removeElementAt(pos);
		}
		
		public void removeTriangleAt(int pos)
		{
			triangleData.removeElementAt(pos);
		}
		
		
		public void setMaterial(idx3d_Material m)
		{
			material=m;
		}
		
		public void rebuild()
		{
			if (!dirty) return;
			dirty=false;
			Enumeration enumer;
			
			// Generate faster structure for vertices
			vertices=vertexData.size();
			vertex=new idx3d_Vertex[vertices];
			enumer=vertexData.elements();
			for (int i=vertices-1;i>=0;i--) vertex[i]=(idx3d_Vertex)enumer.nextElement();
			
			// Generate faster structure for triangles
			triangles=triangleData.size();
			triangle=new idx3d_Triangle[triangles];
			enumer=triangleData.elements();
			for (int i=triangles-1;i>=0;i--)
			{
				triangle[i]=(idx3d_Triangle)enumer.nextElement();
				triangle[i].id=i;
			}
			
			for (int i=vertices-1;i>=0;i--)
			{
				vertex[i].id=i;
				vertex[i].resetNeighbors();
			}
			
			idx3d_Triangle tri;
			for (int i=triangles-1;i>=0;i--)
			{
				tri=triangle[i];
				tri.p1.registerNeighbor(tri);
				tri.p2.registerNeighbor(tri);
				tri.p3.registerNeighbor(tri);
			}

			regenerate();
		}

		public void addVertex(float x, float y, float z)
		{
			addVertex(new idx3d_Vertex(x,y,z));
		}
		
		
		public void addVertex(float x, float y, float z, float u, float v)
		{
			idx3d_Vertex vert=new idx3d_Vertex(x,y,z);
			vert.setUV(u,v);
			addVertex(vert);
		}

		public void addTriangle(idx3d_Vertex a, idx3d_Vertex b, idx3d_Vertex c)
		{
			addTriangle(new idx3d_Triangle(a,b,c));
		}

		public void regenerate()
		// Regenerates the vertex normals
		{
			for (int i=0;i<triangles;i++) triangle[i].regenerateNormal();
			for (int i=0;i<vertices;i++) vertex[i].regenerateNormal();
		}
				
		public String toString()
		{
			StringBuffer buffer=new StringBuffer();
			buffer.append("<object id="+name+">\r\n");
			for (int i=0;i<vertices;i++) buffer.append(vertex[i].toString());
			return buffer.toString();
		}
		
		public void scaleTextureCoordinates(float fu, float fv)
		{
			rebuild();
			for (int i=0;i<vertices;i++) vertex[i].scaleTextureCoordinates(fu,fv);
		}
		
		public void tilt(float fact)
		{
			rebuild();
			for (int i=0;i<vertices;i++)
				vertex[i].pos=idx3d_Vector.add(vertex[i].pos,idx3d_Vector.random(fact));
			regenerate();
		}
			
		/*public idx3d_Vector min()
		{
			if (vertices==0) return new idx3d_Vector(0f,0f,0f);
			float minX=vertex[0].pos.x;
			float minY=vertex[0].pos.y;
			float minZ=vertex[0].pos.z;
			for (int i=1; i<vertices; i++) 
			{
				if(vertex[i].pos.x<minX) minX=vertex[i].pos.x;
				if(vertex[i].pos.y<minY) minY=vertex[i].pos.y;
				if(vertex[i].pos.z<minZ) minZ=vertex[i].pos.z;
			}
			return new idx3d_Vector(minX,minY,minZ);
		}*/
		
		// These are Jason's replacements
		public idx3d_Vector min() {
			if (vertexData.size() == 0) return new idx3d_Vector(0f, 0f, 0f);
			float minX=vertex(0).pos.x;
			float minY=vertex(0).pos.y;
			float minZ=vertex(0).pos.z;
			for (int i=0; i< vertexData.size(); i++) 
			{
				if(vertex(i).pos.x<minX) minX=vertex(i).pos.x;
				if(vertex(i).pos.y<minY) minY=vertex(i).pos.y;
				if(vertex(i).pos.z<minZ) minZ=vertex(i).pos.z;
			}
			return new idx3d_Vector(minX,minY,minZ);
		}
		
		/*public idx3d_Vector max()
		{
			if (vertices==0) return new idx3d_Vector(0f,0f,0f);
			float maxX=vertex[0].pos.x;
			float maxY=vertex[0].pos.y;
			float maxZ=vertex[0].pos.z;
			for (int i=1; i<vertices; i++) 
			{
				if(vertex[i].pos.x>maxX) maxX=vertex[i].pos.x;
				if(vertex[i].pos.y>maxY) maxY=vertex[i].pos.y;
				if(vertex[i].pos.z>maxZ) maxZ=vertex[i].pos.z;
			}
			return new idx3d_Vector(maxX,maxY,maxZ);
		}*/
		
		public idx3d_Vector max()
		{
			if (vertexData.size() == 0) return new idx3d_Vector(0f,0f,0f);
			float maxX=vertex(0).pos.x;
			float maxY=vertex(0).pos.y;
			float maxZ=vertex(0).pos.z;
			for (int i=1; i< vertexData.size(); i++) 
			{
				if(vertex(i).pos.x>maxX) maxX=vertex(i).pos.x;
				if(vertex(i).pos.y>maxY) maxY=vertex(i).pos.y;
				if(vertex(i).pos.z>maxZ) maxZ=vertex(i).pos.z;
			}
			return new idx3d_Vector(maxX,maxY,maxZ);
		}
		
		public void detach()
		// Centers the object in its coordinate system
		// The offset from origin to object center will be transfered to the matrix,
		// so your object actually does not move.
		// Usefull if you want prepare objects for self rotation.
		{
			idx3d_Vector center=getCenter();
			
			for (int i=0;i<vertices;i++)
			{
				vertex[i].pos.x-=center.x;	
				vertex[i].pos.y-=center.y;	
				vertex[i].pos.z-=center.z;	
			}
			shift(center);
		}
		
		public idx3d_Vector getCenter()
		// Returns the center of this object
		{
			idx3d_Vector max=max();
			idx3d_Vector min=min();
			return new idx3d_Vector((max.x+min.x)/2,(max.y+min.y)/2,(max.z+min.z)/2);
		}
		
		public idx3d_Vector getDimension()
		// Returns the x,y,z - Dimension of this object
		{
			idx3d_Vector max=max();
			idx3d_Vector min=min();
			return new idx3d_Vector(max.x-min.x,max.y-min.y,max.z-min.z);
		}			
		
		public void matrixMeltdown()
		// Applies the transformations in the matrix to all vertices
		// and resets the matrix to untransformed.
		{
			rebuild();
			for (int i=vertices-1;i>=0;i--)
				vertex[i].pos=vertex[i].pos.transform(matrix);
			regenerate();
			matrix.reset();
			normalmatrix.reset();
		}
		
		public idx3d_Object getClone()
		{
			idx3d_Object obj=new idx3d_Object();
			rebuild();
			for(int i=0;i<vertices;i++) obj.addVertex(vertex[i].getClone());
			for(int i=0;i<triangles;i++) obj.addTriangle(triangle[i].getClone());
			obj.name=name+" [cloned]";
			obj.material=material;
			obj.matrix=matrix.getClone();
			obj.normalmatrix=normalmatrix.getClone();
			obj.rebuild();
			return obj;
		}
		
		public void removeDuplicateVertices()
		{
			rebuild();
			Vector edgesToCollapse=new Vector();
			for (int i=0;i<vertices;i++)
				for (int j=i+1;j<vertices;j++)
					if (vertex[i].equals(vertex[j],0.0001f))
						edgesToCollapse.addElement(new idx3d_Edge(vertex[i],vertex[j]));
			Enumeration enumer=edgesToCollapse.elements();
			while(enumer.hasMoreElements()) edgeCollapse((idx3d_Edge)enumer.nextElement());
		
			removeDegeneratedTriangles();
		}
		
		public void removeDegeneratedTriangles()
		{
			rebuild();
			for (int i=0;i<triangles;i++)
				if (triangle[i].degenerated()) removeTriangleAt(i);
			
			dirty=true;
			rebuild();			
		}
		
		public void meshSmooth()
		{				
			rebuild();
			idx3d_Triangle tri;
			float u,v;
			idx3d_Vertex a,b,c,d,e,f,temp;
			idx3d_Vector ab,bc,ca,nab,nbc,nca,center;
			float sab,sbc,sca,rab,rbc,rca;
			float uab,vab,ubc,vbc,uca,vca;
			float sqrt3=(float)Math.sqrt(3f);
			
			for (int i=0;i<triangles;i++)
			{
				tri=triangle(i);
				a=tri.p1;
				b=tri.p2;
				c=tri.p3;
				ab=idx3d_Vector.scale(0.5f,idx3d_Vector.add(b.pos,a.pos));
				bc=idx3d_Vector.scale(0.5f,idx3d_Vector.add(c.pos,b.pos));
				ca=idx3d_Vector.scale(0.5f,idx3d_Vector.add(a.pos,c.pos));
				rab=idx3d_Vector.sub(ab,a.pos).length();
				rbc=idx3d_Vector.sub(bc,b.pos).length();
				rca=idx3d_Vector.sub(ca,c.pos).length();
				
				nab=idx3d_Vector.scale(0.5f,idx3d_Vector.add(a.n,b.n));
				nbc=idx3d_Vector.scale(0.5f,idx3d_Vector.add(b.n,c.n));
				nca=idx3d_Vector.scale(0.5f,idx3d_Vector.add(c.n,a.n));
				uab=0.5f*(a.u+b.u);
				vab=0.5f*(a.v+b.v);
				ubc=0.5f*(b.u+c.u);
				vbc=0.5f*(b.v+c.v);
				uca=0.5f*(c.u+a.u);
				vca=0.5f*(c.v+a.v);
				sab=1f-nab.length();
				sbc=1f-nbc.length();
				sca=1f-nca.length();
				nab.normalize();
				nbc.normalize();
				nca.normalize();
				
				d=new idx3d_Vertex(idx3d_Vector.sub(ab,idx3d_Vector.scale(rab*sab,nab)),uab,vab);
				e=new idx3d_Vertex(idx3d_Vector.sub(bc,idx3d_Vector.scale(rbc*sbc,nbc)),ubc,vbc);
				f=new idx3d_Vertex(idx3d_Vector.sub(ca,idx3d_Vector.scale(rca*sca,nca)),uca,vca);
				
				addVertex(d);
				addVertex(e);
				addVertex(f);
				tri.p2=d;
				tri.p3=f;
				addTriangle(b,e,d);
				addTriangle(c,f,e);
				addTriangle(d,e,f);
			}
			removeDuplicateVertices();			
		}
		

	// P R I V A T E   M E T H O D S

		private void edgeCollapse(idx3d_Edge edge)
		// Collapses the edge [u,v] by replacing v by u
		{
			idx3d_Vertex u=edge.start();
			idx3d_Vertex v=edge.end();
			if (!vertexData.contains(u)) return;
			if (!vertexData.contains(v)) return;
			rebuild();
			idx3d_Triangle tri;
			for (int i=0; i<triangles; i++)
			{
				tri=triangle(i);
				if (tri.p1==v) tri.p1=u;
				if (tri.p2==v) tri.p2=u;
				if (tri.p3==v) tri.p3=u;
			}
			vertexData.removeElement(v);
		}
}
