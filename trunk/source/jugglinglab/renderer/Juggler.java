// Juggler.java
//
// Copyright 2003 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.renderer;

import idx3d.*;

import jugglinglab.util.*;
import jugglinglab.jml.*;


//  This class calculates the coordinates of the juggler elbows, shoulders, etc.

public class Juggler {
		// juggler dimensions, in cm
	public final static double shoulder_hw = 23.0;	// shoulder half-width (m)
	public final static double shoulder_h = 40.0;	// throw pos. to shoulder
	public final static double waist_hw = 17.0;		// waist half-width
	public final static double waist_h = -5.0;
	public final static double elbow_hw = 30.0;		// elbow "home"
	public final static double elbow_h = 6.0;
	public final static double elbow_slop = 12.0;
	public final static double hand_out = 5.0;		// outside width of hand
	public final static double hand_in = 5.0;
	public final static double head_hw = 10.0;		// head half-width
	public final static double head_h = 26.0;		// head height
	public final static double neck_h = 5.0;		// neck height
	public final static double shoulder_y = 0;
	public final static double pattern_y = 30;
	public final static double upper_length = 41;
	public final static double lower_length = 40;
	
	public final static double lower_gap_wrist = 1;
	public final static double lower_gap_elbow = 0;
	public final static double lower_hand_height = 0;
	public final static double upper_gap_elbow = 0;
	public final static double upper_gap_shoulder = 0;
	
	protected final static double lower_total = lower_length + lower_gap_wrist + lower_gap_elbow;
	protected final static double upper_total = upper_length + upper_gap_elbow + upper_gap_shoulder;

		// the remaining are used only for the 3d display 
	public final static double shoulder_radius = 6;
	public final static double elbow_radius = 4;
	public final static double wrist_radius = 2;
	
	

	public static void findJugglerCoordinates(JMLPattern pat, double time, idx3d_Vector[][] result) throws JuggleExceptionInternal {
		for (int juggler = 1; juggler <= pat.getNumberOfJugglers(); juggler++) {
			idx3d_Vector lefthand, righthand;
			idx3d_Vector leftshoulder, rightshoulder;
			idx3d_Vector leftelbow, rightelbow;
			idx3d_Vector leftwaist, rightwaist;
			idx3d_Vector leftheadbottom, leftheadtop;
			idx3d_Vector rightheadbottom, rightheadtop;
			
			Coordinate coord0 = new Coordinate();
			Coordinate coord1 = new Coordinate();
			Coordinate coord2 = new Coordinate();
			pat.getHandCoordinate(juggler, HandLink.LEFT_HAND, time, coord0);
			pat.getHandCoordinate(juggler, HandLink.RIGHT_HAND, time, coord1);
			lefthand = new idx3d_Vector((float)coord0.x,
						(float)(coord0.z + lower_hand_height), (float)coord0.y);
			righthand = new idx3d_Vector((float)coord1.x,
						(float)(coord1.z + lower_hand_height), (float)coord1.y);
			
			pat.getJugglerPosition(juggler, time, coord2);
			double angle = JLMath.toRad(pat.getJugglerAngle(juggler, time));
			double s = Math.sin(angle);
			double c = Math.cos(angle);
			
			leftshoulder = new idx3d_Vector(
				(float)(coord2.x - shoulder_hw * c - shoulder_y * s),
				(float)(coord2.z + shoulder_h),
				(float)(coord2.y - shoulder_hw * s + shoulder_y * c));
			rightshoulder = new idx3d_Vector(
				(float)(coord2.x + shoulder_hw * c - shoulder_y * s),
				(float)(coord2.z + shoulder_h),
				(float)(coord2.y + shoulder_hw * s + shoulder_y * c));
			leftwaist = new idx3d_Vector(
				(float)(coord2.x - waist_hw * c - shoulder_y * s),
				(float)(coord2.z + waist_h),
				(float)(coord2.y - waist_hw * s + shoulder_y * c));
			rightwaist = new idx3d_Vector(
				(float)(coord2.x + waist_hw * c - shoulder_y * s),
				(float)(coord2.z + waist_h),
				(float)(coord2.y + waist_hw * s + shoulder_y * c));
			leftheadbottom = new idx3d_Vector(
				(float)(coord2.x - head_hw * c - shoulder_y * s),
				(float)(coord2.z + shoulder_h + neck_h),
				(float)(coord2.y - head_hw * s + shoulder_y * c));
			leftheadtop = new idx3d_Vector(
				(float)(coord2.x - head_hw * c - shoulder_y * s),
				(float)(coord2.z + shoulder_h + neck_h + head_h),
				(float)(coord2.y - head_hw * s + shoulder_y * c));
			rightheadbottom = new idx3d_Vector(
				(float)(coord2.x + head_hw * c - shoulder_y * s),
				(float)(coord2.z + shoulder_h + neck_h),
				(float)(coord2.y + head_hw * s + shoulder_y * c));
			rightheadtop = new idx3d_Vector(
				(float)(coord2.x + head_hw * c - shoulder_y * s),
				(float)(coord2.z + shoulder_h + neck_h + head_h),
				(float)(coord2.y + head_hw * s + shoulder_y * c));
			
			double L = lower_total;
			double U = upper_total;
			idx3d_Vector deltaL = idx3d_Vector.sub(lefthand, leftshoulder);
			double D = (double)(deltaL.length());
			if (D <= (L+U)) {
				// Calculate the coordinates of the elbows
				double Lr = Math.sqrt((4.0*U*U*L*L-(U*U+L*L-D*D)*(U*U+L*L-D*D))/(4.0*D*D));
				if (Double.isNaN(Lr))
					throw new JuggleExceptionInternal("NaN in renderer 1");
				
				double factor = Math.sqrt(U*U-Lr*Lr)/D;
				if (Double.isNaN(factor))
					throw new JuggleExceptionInternal("NaN in renderer 2");
				idx3d_Vector Lxsc = idx3d_Vector.scale((float)factor, deltaL);
				double Lalpha = Math.asin(deltaL.y / D);
				if (Double.isNaN(Lalpha))
					throw new JuggleExceptionInternal("NaN in renderer 3");
				factor = 1.0 + Lr*Math.tan(Lalpha)/(factor*D);
				leftelbow = new idx3d_Vector(
						leftshoulder.x + Lxsc.x * (float)factor,
						leftshoulder.y + Lxsc.y - (float)(Lr*Math.cos(Lalpha)),
						leftshoulder.z + Lxsc.z * (float)factor);
			} else {
				leftelbow = null;
			}
			
			idx3d_Vector deltaR = idx3d_Vector.sub(righthand, rightshoulder);
			D = (double)(deltaR.length());
			if (D <= (L+U)) {
				// Calculate the coordinates of the elbows
				double Rr = Math.sqrt((4.0*U*U*L*L-(U*U+L*L-D*D)*(U*U+L*L-D*D))/(4.0*D*D));
				if (Double.isNaN(Rr))
					throw new JuggleExceptionInternal("NaN in renderer 4");
				
				double factor = Math.sqrt(U*U-Rr*Rr)/D;
				if (Double.isNaN(factor))
					throw new JuggleExceptionInternal("NaN in renderer 5");
				idx3d_Vector Rxsc = idx3d_Vector.scale((float)factor, deltaR);
				double Ralpha = Math.asin(deltaR.y / D);
				if (Double.isNaN(Ralpha))
					throw new JuggleExceptionInternal("NaN in renderer 6");
				factor = 1.0 + Rr*Math.tan(Ralpha)/(factor*D);
				rightelbow = new idx3d_Vector(
						rightshoulder.x + Rxsc.x * (float)factor,
						rightshoulder.y + Rxsc.y - (float)(Rr*Math.cos(Ralpha)),
						rightshoulder.z + Rxsc.z * (float)factor);
			} else {
				rightelbow = null;
			}

			result[juggler-1][0] = lefthand;
			result[juggler-1][1] = righthand;
			result[juggler-1][2] = leftshoulder;
			result[juggler-1][3] = rightshoulder;
			result[juggler-1][4] = leftelbow;
			result[juggler-1][5] = rightelbow;
			result[juggler-1][6] = leftwaist;
			result[juggler-1][7] = rightwaist;
			result[juggler-1][8] = leftheadbottom;
			result[juggler-1][9] = leftheadtop;
			result[juggler-1][10] = rightheadbottom;
			result[juggler-1][11] = rightheadtop;
		}
	}

}
