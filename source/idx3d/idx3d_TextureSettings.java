package idx3d;

import idx3d.idx3d_Texture;

public class idx3d_TextureSettings
{
	public idx3d_Texture texture;
	public int width;
	public int height;
	public int type;
	public float persistency;
	public float density;
	public int samples;
	public int numColors;
	public int[] colors;
	
	public idx3d_TextureSettings(idx3d_Texture tex, int w, int h, int t, float p, float d, int s, int[] c)
	{
		texture=tex;
		width=w;
		height=h;
		type=t;
		persistency=p;
		density=d;
		samples=s;
		colors=c;
	}
}

		