// JLMatrix.java
//
// Copyright 2011 by Jack Boyce (jboyce@users.sourceforge.net) and others

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


public class JLMatrix {

	public double m00 = 1.0, m01 = 0.0, m02 = 0.0, m03 = 0.0;
	public double m10 = 0.0, m11 = 1.0, m12 = 0.0, m13 = 0.0;
	public double m20 = 0.0, m21 = 0.0, m22 = 1.0, m23 = 0.0;
	public double m30 = 0.0, m31 = 0.0, m32 = 0.0, m33 = 1.0;

	public JLMatrix() {
	}
	
	public JLMatrix(JLVector right, JLVector up, JLVector forward) {
		m00 = right.x;
		m10 = right.y;
		m20 = right.z;
		m01 = up.x;
		m11 = up.y;
		m21 = up.z;
		m02 = forward.x;
		m12 = forward.y;
		m22 = forward.z;
	}
		
	public static JLMatrix shiftMatrix(double dx, double dy, double dz) {
		JLMatrix m = new JLMatrix();
		m.m03 = dx;
		m.m13 = dy;
		m.m23 = dz;
		return m;
	}

	public static JLMatrix scaleMatrix(double dx, double dy, double dz) {
		JLMatrix m = new JLMatrix();
		m.m00 = dx;
		m.m11 = dy;
		m.m22 = dz;
		return m;
	}

	public static JLMatrix scaleMatrix(double d) {
		return JLMatrix.scaleMatrix(d,d,d);
	}

	public static JLMatrix rotateMatrix(double dx, double dy, double dz) {
		JLMatrix out = new JLMatrix();
		double SIN;
		double COS;

		if (dx != 0.0) {
			JLMatrix m = new JLMatrix();
			SIN = Math.sin(dx);
			COS = Math.cos(dx);
			m.m11 = COS;
			m.m12 = SIN;
			m.m21 = -SIN;
			m.m22 = COS;
			out.transform(m);
		}
		if (dy != 0) {
			JLMatrix m = new JLMatrix();
			SIN = Math.sin(dy);
			COS = Math.cos(dy);
			m.m00 = COS;
			m.m02 = SIN;
			m.m20 = -SIN;
			m.m22 = COS;
			out.transform(m);
		}
		if (dz != 0) {
			JLMatrix m = new JLMatrix();
			SIN = Math.sin(dz);
			COS = Math.cos(dz);
			m.m00 = COS;
			m.m01 = SIN;
			m.m10 = -SIN;
			m.m11 = COS;
			out.transform(m);
		}
		return out;
	}


	public void shift(double dx, double dy, double dz) {
		transform(shiftMatrix(dx,dy,dz));
	}

	public void scale(double dx, double dy, double dz) {
		transform(scaleMatrix(dx,dy,dz));
	}

	public void scale(double d) {
		transform(scaleMatrix(d));
	}

	public void rotate(double dx, double dy, double dz) {
		transform(rotateMatrix(dx,dy,dz));
	}
		
	public void transform(JLMatrix n) {
		JLMatrix m = this.getClone();

		m00 = n.m00*m.m00 + n.m01*m.m10 + n.m02*m.m20;
		m01 = n.m00*m.m01 + n.m01*m.m11 + n.m02*m.m21;
		m02 = n.m00*m.m02 + n.m01*m.m12 + n.m02*m.m22;
		m03 = n.m00*m.m03 + n.m01*m.m13 + n.m02*m.m23 + n.m03;
		m10 = n.m10*m.m00 + n.m11*m.m10 + n.m12*m.m20;
		m11 = n.m10*m.m01 + n.m11*m.m11 + n.m12*m.m21;
		m12 = n.m10*m.m02 + n.m11*m.m12 + n.m12*m.m22;
		m13 = n.m10*m.m03 + n.m11*m.m13 + n.m12*m.m23 + n.m13;
		m20 = n.m20*m.m00 + n.m21*m.m10 + n.m22*m.m20;
		m21 = n.m20*m.m01 + n.m21*m.m11 + n.m22*m.m21;
		m22 = n.m20*m.m02 + n.m21*m.m12 + n.m22*m.m22;
		m23 = n.m20*m.m03 + n.m21*m.m13 + n.m22*m.m23 + n.m23;			
	}
		
	public JLMatrix getClone() {
		JLMatrix m = new JLMatrix();
		m.m00=m00;  m.m01=m01;  m.m02=m02;  m.m03=m03;
		m.m10=m10;  m.m11=m11;  m.m12=m12;  m.m13=m13;
		m.m20=m20;  m.m21=m21;  m.m22=m22;  m.m23=m23;
		m.m30=m30;  m.m31=m31;  m.m32=m32;  m.m33=m33;
		return m;
	}

	public JLMatrix inverse() {
		JLMatrix m = new JLMatrix();
		
		double q1 = m12;  double q6 = m10*m01;  double q7 = m10*m21;  double q8 = m02;  
		double q13 = m20*m01;  double q14 = m20*m11;  double q21 = m02*m21;  double q22 = m03*m21;  
		double q25 = m01*m12;  double q26 = m01*m13;  double q27 = m02*m11;  double q28 = m03*m11;  
		double q29 = m10*m22;  double q30 = m10*m23;  double q31 = m20*m12;  double q32 = m20*m13;  
		double q35 = m00*m22;  double q36 = m00*m23;  double q37 = m20*m02;  double q38 = m20*m03;  
		double q41 = m00*m12;  double q42 = m00*m13;  double q43 = m10*m02;  double q44 = m10*m03;  
		double q45 = m00*m11;  double q48 = m00*m21;  
		double q49 = q45*m22-q48*q1-q6*m22+q7*q8;
		double q50 = q13*q1-q14*q8;
		double q51 = 1.0/(q49+q50);
			
		m.m00 = (m11*m22*m33-m11*m23*m32-m21*m12*m33+m21*m13*m32+m31*m12*m23-m31*m13*m22)*q51;
		m.m01 = -(m01*m22*m33-m01*m23*m32-q21*m33+q22*m32)*q51;
		m.m02 = (q25*m33-q26*m32-q27*m33+q28*m32)*q51;
		m.m03 = -(q25*m23-q26*m22-q27*m23+q28*m22+q21*m13-q22*m12)*q51;
		m.m10 = -(q29*m33-q30*m32-q31*m33+q32*m32)*q51;
		m.m11 = (q35*m33-q36*m32-q37*m33+q38*m32)*q51;
		m.m12 = -(q41*m33-q42*m32-q43*m33+q44*m32)*q51;
		m.m13 = (q41*m23-q42*m22-q43*m23+q44*m22+q37*m13-q38*m12)*q51;
		m.m20 = (q7*m33-q30*m31-q14*m33+q32*m31)*q51;
		m.m21 = -(q48*m33-q36*m31-q13*m33+q38*m31)*q51;
		m.m22 = (q45*m33-q42*m31-q6*m33+q44*m31)*q51;
		m.m23 = -(q45*m23-q42*m21-q6*m23+q44*m21+q13*m13-q38*m11)*q51;

		return m;
	}
}