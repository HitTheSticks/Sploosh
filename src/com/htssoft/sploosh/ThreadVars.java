package com.htssoft.sploosh;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

public class ThreadVars {
	public Vector3f temp0 = new Vector3f();
	public Vector3f temp1 = new Vector3f();
	public Vector3f temp2 = new Vector3f();
	
	public Vector3f[] vec = new Vector3f[6];
	{
		for (int i = 0; i < vec.length; i++){
			vec[i] = new Vector3f();
		}
	}
	
	public Matrix3f mat = new Matrix3f();
}
