package com.htssoft.sploosh;

import com.jme3.math.Vector3f;

public class Vorton {
	public final Vector3f vorticity = new Vector3f();
	public final Vector3f position = new Vector3f();
	
	public String toString(){
		return String.format("P:%s,V:%s", position.toString(), vorticity.toString());
	}
}
