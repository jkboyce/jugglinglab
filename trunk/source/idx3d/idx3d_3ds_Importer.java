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

import java.io.*;
import java.net.*;

public class idx3d_3ds_Importer
// Imports a scene from a 3ds (3d Studio Max) Ressource
{
	// F I E L D S

		private int currentJunkId;
		private int nextJunkOffset;

		private idx3d_Scene scene;
		private String currentObjectName=null;
		private idx3d_Object currentObject=null;
		private boolean endOfStream=false;


	// C O N S T R U C T O R S

		public idx3d_3ds_Importer()
		{
		}


	// P U B L I C   M E T H O D S

		public void importFromURL(URL url, idx3d_Scene targetscene) throws IOException
		{
			importFromStream(url.openStream(),targetscene);
		}


		public void importFromStream(InputStream inStream, idx3d_Scene targetscene)
		{
			System.out.println(">> Importing scene from 3ds stream ...");
			scene=targetscene;
			BufferedInputStream in=new BufferedInputStream(inStream);
			try
			{
				readJunkHeader(in);
				if (currentJunkId!=0x4D4D) {System.out.println("Error: This is no valid 3ds file."); return; }
				while (!endOfStream) readNextJunk(in);
			}
			catch (Throwable ignored){}
		}
		

	// P R I V A T E   M E T H O D S

		private String readString(InputStream in) throws IOException
		{
			String result=new String();
			byte inByte;
			while ((inByte=(byte)in.read())!=0) result+=(char)inByte;
			return result;
		}

		private int readInt(InputStream in) throws IOException
		{
			return in.read()|(in.read()<<8)|(in.read()<<16)|(in.read()<<24);
		}

		private int readShort(InputStream in) throws IOException
		{
			return (in.read()|(in.read()<<8));
		}

		private float readFloat(InputStream in) throws IOException
		{
			return Float.intBitsToFloat(readInt(in));
		}


		private void readJunkHeader(InputStream in) throws IOException
		{
			currentJunkId=readShort(in);
			nextJunkOffset=readInt(in);
			endOfStream=currentJunkId<0;
		}

		private void readNextJunk(InputStream in) throws IOException
		{
			readJunkHeader(in);
			
			if (currentJunkId==0x3D3D) return; // Mesh block
			if (currentJunkId==0x4000) // Object block
			{
				currentObjectName=readString(in);
				System.out.println(">> Importing object: "+currentObjectName);
				return;
			}
			if (currentJunkId==0x4100)  // Triangular polygon object
			{
				currentObject=new idx3d_Object();
				scene.addObject(currentObjectName,currentObject);
				return;
			}
			if (currentJunkId==0x4110) // Vertex list
			{
				readVertexList(in);
				return;
			}
			if (currentJunkId==0x4120) // Point list
			{
				readPointList(in);
				return;
			}
			if (currentJunkId==0x4140) // Mapping coordinates
			{
				readMappingCoordinates(in);
				return;
			}

			skipJunk(in);
		}

		private void skipJunk(InputStream in) throws IOException, OutOfMemoryError
		{
			for (int i=0; (i<nextJunkOffset-6)&&(!endOfStream);i++)
				endOfStream=in.read()<0;
		}

		private void readVertexList(InputStream in) throws IOException
		{
			float x,y,z;
			int vertices=readShort(in);
			for (int i=0; i<vertices; i++)
			{
				x=readFloat(in);
				y=readFloat(in);
				z=readFloat(in);
				currentObject.addVertex(x,-y,z);
			}
		}

		private void readPointList(InputStream in) throws IOException
		{
			int v1,v2,v3;
			int triangles=readShort(in);
			for (int i=0; i<triangles; i++)
			{
				v1=readShort(in);
				v2=readShort(in);
				v3=readShort(in);
				readShort(in);
				currentObject.addTriangle(
					currentObject.vertex(v1),
					currentObject.vertex(v2),
					currentObject.vertex(v3));
			}
		}

		private void readMappingCoordinates(InputStream in) throws IOException
		{
			int vertices=readShort(in);
			for (int i=0; i<vertices; i++)
			{
				currentObject.vertex(i).u=readFloat(in);
				currentObject.vertex(i).v=readFloat(in);
			}
		}
}