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

public class idx3d_3ds_Exporter
// Exports a scene to a 3ds (3d Studio Max) Ressource
{
	// F I E L D S

	// C O N S T R U C T O R S

		private idx3d_3ds_Exporter()
		{
		}


	// P U B L I C   M E T H O D S

		public static void exportToStream(OutputStream outStream, idx3d_Scene source)
		{
			System.out.println(">> Exporting scene to 3ds stream ...");
			BufferedOutputStream out=new BufferedOutputStream(outStream);
			try{ 
				exportScene(source,out);
				outStream.close();
			}
			catch (Throwable ignored){System.out.println(ignored+"");}
		}



	// P R I V A T E   M E T H O D S

		private static void writeString(String outString, OutputStream out) throws IOException
		{
			byte[] data=new byte[(int)(outString.length())];
			outString.getBytes(0,(int)outString.length(),data,0);
			out.write(data);
			out.write(0);
		}

		private static void writeInt(int outInt, OutputStream out) throws IOException
		{
			out.write(outInt&255);
			out.write((outInt>>8)&255);
			out.write((outInt>>16)&255);
			out.write((outInt>>24)&255);
		}

		private static void writeShort(int outShort, OutputStream out) throws IOException
		{
			out.write(outShort&255);
			out.write((outShort>>8)&255);
		}

		private static void writeFloat(float outFloat, OutputStream out) throws IOException
		{
			writeInt(Float.floatToIntBits(outFloat),out);
		}


	// J U N K   E X P O R T

		private static void exportScene(idx3d_Scene scene, OutputStream out) throws IOException
		{
			scene.rebuild();
			int runlength=0;
			for (int i=0;i<scene.objects;i++)
			{
				runlength+=scene.object[i].name.length()+1;
				runlength+=36+20*scene.object[i].vertices+8*scene.object[i].triangles;
			}
			writeShort(0x4D4D,out);
			writeInt(12+runlength,out);
			writeShort(0x3D3D,out);
			writeInt(6+runlength,out);

			for (int i=0;i<scene.objects;i++) exportObject(scene.object[i], out);
		}

		private static void exportObject(idx3d_Object obj, OutputStream out) throws IOException
		{
			int vJunkSize=2+12*obj.vertices; 
			int tJunkSize=2+8*obj.triangles;
			int mcJunkSize=2+8*obj.vertices;
			
			writeShort(0x4000,out);
			writeInt(30+vJunkSize+tJunkSize+mcJunkSize+obj.name.length()+1,out);
			writeString(obj.name,out);
			writeShort(0x4100,out);
			writeInt(24+vJunkSize+tJunkSize+mcJunkSize,out);

			writeShort(0x4110,out);
			writeInt(6+vJunkSize,out);
			writeShort(obj.vertices,out);
			for (int i=0;i<obj.vertices;i++) exportVertex(obj.vertex[i], out);

			writeShort(0x4120,out);
			writeInt(6+tJunkSize,out);
			writeShort(obj.triangles,out);
			for (int i=0;i<obj.triangles;i++) exportTriangle(obj.triangle[i], out);

			writeShort(0x4140,out);	
			writeInt(6+mcJunkSize,out);
			writeShort(obj.vertices,out);		
			for (int i=0;i<obj.vertices;i++) exportMappingCoordinates(obj.vertex[i], out);
		}

		private static void exportVertex(idx3d_Vertex v, OutputStream out) throws IOException
		{
			writeFloat(v.pos.x,out);
			writeFloat(-v.pos.y,out);
			writeFloat(v.pos.z,out);
		}

		private static void exportTriangle(idx3d_Triangle t, OutputStream out) throws IOException
		{
			writeShort(t.p1.id,out);
			writeShort(t.p2.id,out);
			writeShort(t.p3.id,out);
			writeShort(0,out);
		}

		private static void exportMappingCoordinates(idx3d_Vertex v, OutputStream out) throws IOException
		{
			writeFloat(v.u,out);
			writeFloat(v.v,out);
		}
			
}