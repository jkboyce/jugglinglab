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

import java.util.Hashtable;
import java.util.Enumeration;
import java.awt.Image;

public final class idx3d_Scene extends idx3d_CoreObject
{
	//Release Information
	
		public final static String version="3.1.001";
		public final static String release="29.05.2000";
		
	// F I E L D S		

		public idx3d_RenderPipeline renderPipeline;
		public int width,height;

		public idx3d_Environment environment=new idx3d_Environment();
		public idx3d_Camera defaultCamera=idx3d_Camera.FRONT();
		
		public idx3d_Object object[];
		public idx3d_Light light[];
		public int objects=0;
		public int lights=0;

		private boolean objectsNeedRebuild=true;
		private boolean lightsNeedRebuild=true;
		
		protected boolean preparedForRendering=false;
		
		public idx3d_Vector normalizedOffset=new idx3d_Vector(0f,0f,0f);
		public float normalizedScale=1f;
		private static boolean instancesRunning=false;
		
	// D A T A   S T R U C T U R E S
		
		public Hashtable objectData=new Hashtable();
		public Hashtable lightData=new Hashtable();
		public Hashtable materialData=new Hashtable();
		public Hashtable cameraData=new Hashtable();


	// C O N S T R U C T O R S
	
		private idx3d_Scene()
		{
		}
	
		public idx3d_Scene(int w, int h)
		{
			showInfo(); width=w; height=h;
			renderPipeline= new idx3d_RenderPipeline(this,w,h);
		}

		
		public void showInfo()
		{
			if (instancesRunning) return;
			System.out.println();
			System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			System.out.println(" idx3d Kernel "+version+" [Build "+release+"]");
			System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			System.out.println(" (c)1999 by Peter Walser, all rights reserved.");
			System.out.println(" http://www2.active.ch/~proxima/idx3d");
			System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			instancesRunning=true;
		}
		

	// D A T A   M A N A G E M E N T
	
		public void removeAllObjects()
		{
			objectData=new Hashtable();
			objectsNeedRebuild=true;
			rebuild();
		}

		public void rebuild()
		{
			if (objectsNeedRebuild)
			{
				objectsNeedRebuild=false;
				objects=objectData.size();
				object=new idx3d_Object[objects];
				Enumeration enumer=objectData.elements();
				for (int i=objects-1;i>=0;i--)
				{
					object[i]=(idx3d_Object)enumer.nextElement();
					object[i].id=i;
					object[i].rebuild();
				}
				
			}
			
			if (lightsNeedRebuild)
			{
				lightsNeedRebuild=false;
				lights=lightData.size();
				light=new idx3d_Light[lights];				
				Enumeration enumer=lightData.elements();
				for (int i=lights-1;i>=0;i--) light[i]=(idx3d_Light)enumer.nextElement();

			}
		}

	// A C C E S S O R S

		public idx3d_Object object(String key)   { return (idx3d_Object)  objectData.get(key);}
		public idx3d_Light light(String key)     { return (idx3d_Light)   lightData.get(key);}
		public idx3d_Material material(String key) { return (idx3d_Material) materialData.get(key);}
		public idx3d_Camera camera(String key) { return (idx3d_Camera) cameraData.get(key);}
		
	// O B J E C T   M A N A G E M E N T

		public void addObject(String key, idx3d_Object obj){ obj.name=key; objectData.put(key,obj); obj.parent=this; objectsNeedRebuild=true;}
		public void removeObject(String key) { objectData.remove(key); objectsNeedRebuild=true; preparedForRendering=false;}

		public void addLight(String key, idx3d_Light l) { lightData.put(key,l); lightsNeedRebuild=true;}
		public void removeLight(String key) { lightData.remove(key); lightsNeedRebuild=true; preparedForRendering=false;}

		public void addMaterial(String key, idx3d_Material m) {materialData.put(key,m);}
		public void removeMaterial(String key) { materialData.remove(key); }
		
		public void addCamera(String key, idx3d_Camera c) {cameraData.put(key,c);}
		public void removeCamera(String key) { cameraData.remove(key); }


	// R E N D E R I N G
	
		void prepareForRendering()
		{
			if (preparedForRendering) return;
			preparedForRendering=true;

			System.out.println(">> Preparing structures for realtime rendering ...   ");
			rebuild();
			renderPipeline.buildLightMap();
			printSceneInfo();
		}
		
		public void printSceneInfo()
		{
			System.out.println(">> | Objects   : "+objects);
			System.out.println(">> | Vertices  : "+countVertices());
			System.out.println(">> | Triangles : "+countTriangles());
		}
				
	
		public final void render(idx3d_Camera cam)
		{
			renderPipeline.render(cam);
		}
		
		public final void render()
		{
			renderPipeline.render(this.defaultCamera);
		}
		
		public final Image getImage()
		{
			return renderPipeline.screen.getImage();
		}
		
		public final void setAntialias(boolean antialias)
		{
			renderPipeline.setAntialias(antialias);
		}
		
		public final boolean antialias()
		{
			return renderPipeline.screen.antialias;
		}
		
		public float getFPS()
		{
			return renderPipeline.getFPS();
		}
		
		public void useIdBuffer(boolean useIdBuffer)
		// Enables / Disables idBuffering
		{
			renderPipeline.useIdBuffer(useIdBuffer);
		}
		
		public idx3d_Triangle identifyTriangleAt(int xpos, int ypos)
		{
			if (!renderPipeline.useIdBuffer) return null;
			if (xpos<0 || xpos>=width) return null;
			if (ypos<0 || ypos>=height) return null;

			int pos=xpos+renderPipeline.screen.w*ypos;
			if(renderPipeline.screen.antialias) pos*=2;
			int idCode=renderPipeline.idBuffer[pos];
			if (idCode<0) return null;
			return object[idCode>>16].triangle[idCode&0xFFFF];
		}
		
		public idx3d_Object identifyObjectAt(int xpos, int ypos)
		{
			idx3d_Triangle tri=identifyTriangleAt(xpos,ypos);
			if (tri==null) return null;
			return tri.parent;
		}

	// P U B L I C   M E T H O D S
	
		public java.awt.Dimension size()
		{
			return new java.awt.Dimension(width,height);
		}
	
		public void resize(int w, int h)
		{
			if ((width==w)&&(height==h)) return;
			width=w;
			height=h;
			renderPipeline.resize(w,h);
		}
	
		public void setBackgroundColor(int bgcolor)
		{
			environment.bgcolor=bgcolor;
		}
		
		public void setBackground(idx3d_Texture t)
		{
			environment.setBackground(t);
		}

		public void setAmbient(int ambientcolor)
		{
			environment.ambient=ambientcolor;
		}

		public int countVertices()
		{
			int counter=0;
			for (int i=0;i<objects;i++) counter+=object[i].vertices;
			return counter;
		}
		
		public int countTriangles()
		{
			int counter=0;
			for (int i=0;i<objects;i++) counter+=object[i].triangles;
			return counter;
		}
				
		public String toString()
		{
			StringBuffer buffer=new StringBuffer();
			buffer.append("<scene>\r\n");
			for (int i=0;i<objects;i++) buffer.append(object[i].toString());
			return buffer.toString();
		}
		
		public void normalize()
		// useful if you can't find your objects on the screen ;)
		{
			objectsNeedRebuild=true;				
			rebuild();
			
			idx3d_Vector min, max, tempmax, tempmin;
			if (objects==0) return;
			
			matrix=new idx3d_Matrix();
			normalmatrix=new idx3d_Matrix();
			
			max=object[0].max();
			min=object[0].min();

			for (int i=0; i<objects; i++)
			{
				tempmax=object[i].max();
				tempmin=object[i].min();
				if (tempmax.x>max.x) max.x=tempmax.x;
				if (tempmax.y>max.y) max.y=tempmax.y;
				if (tempmax.z>max.z) max.z=tempmax.z;
				if (tempmin.x<min.x) min.x=tempmin.x;
				if (tempmin.y<min.y) min.y=tempmin.y;
				if (tempmin.z<min.z) min.z=tempmin.z;
			}
			float xdist=max.x-min.x;
			float ydist=max.y-min.y;
			float zdist=max.z-min.z;
			float xmed=(max.x+min.x)/2;
			float ymed=(max.y+min.y)/2;
			float zmed=(max.z+min.z)/2;

			float diameter=(xdist>ydist)?xdist:ydist;
			diameter=(zdist>diameter)?zdist:diameter;
			
			normalizedOffset=new idx3d_Vector(xmed,ymed,zmed);
			normalizedScale=2/diameter;

			shift(normalizedOffset.reverse());
			scale(normalizedScale);
			
		}

	// P R I V A T E   M E T H O D S
}
