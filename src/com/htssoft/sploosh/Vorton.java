package com.htssoft.sploosh;

import com.jme3.math.Vector3f;

public abstract class Vorton {
	
	abstract public Vector3f getPosition();
	
	abstract public void getPosition(Vector3f store);
	
	abstract public void setPosition(Vector3f value);
	
	abstract public void getVort(Vector3f store);
	
	abstract public Vector3f getVort();
	
	abstract public void setVort(Vector3f value);
	
	public String toString(){
		return String.format("P:%s,V:%s", getPosition().toString(), getVort().toString());
	}
}
