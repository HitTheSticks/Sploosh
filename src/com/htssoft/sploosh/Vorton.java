package com.htssoft.sploosh;

import com.jme3.math.Vector3f;

/**
 * A vorton is a package of vorticity used to simulate a fluid.
 * 
 * For more information, see <a href="http://software.intel.com/en-us/articles/fluid-simulation-for-video-games-part-3/">Fluid
 * Simulation for Video Games</a> (Dr. Michael J. Gourlay).
 * */
public abstract class Vorton {
	
	/**
	 * Get a reference to the current position.
	 * 
	 * For a {@link VortonSpace.BufferedVorton}, this is the back buffer position.
	 * */
	abstract public Vector3f getPosition();
	
	/**
	 * Get the value of the current position.
	 * */
	abstract public void getPosition(Vector3f store);
	
	/**
	 * Set the position. For a {@link VortonSpace.BufferedVorton}, this will
	 * update the front buffer. 
	 * */
	abstract public void setPosition(Vector3f value);
	
	/**
	 * Get the value of the current vorticity.
	 * */
	abstract public void getVort(Vector3f store);
	
	/**
	 * Get a reference to the current vorticity.
	 * 
	 * For a {@link VortonSpace.BufferedVorton}, this is the back buffer vorticity.
	 * */
	abstract public Vector3f getVort();
	
	/**
	 * Set the vorticity. For a {@link VortonSpace.BufferedVorton}, this will
	 * update the front buffer.
	 * */
	abstract public void setVort(Vector3f value);
	
	public String toString(){
		return String.format("P:%s,V:%s", getPosition().toString(), getVort().toString());
	}
}
