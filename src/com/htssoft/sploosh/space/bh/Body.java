package com.htssoft.sploosh.space.bh;

import com.jme3.math.Vector3f;

/**
 * A body in a Barnes-Hutt tree.
 * */
public interface Body {
	/**
	 * Get the body's location.
	 * */
	public void getPosition(Vector3f out);
	
	/**
	 * Get the body's location.
	 * */
	public Vector3f getPosition();
}
