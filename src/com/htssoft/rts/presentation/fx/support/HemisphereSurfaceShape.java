package com.htssoft.rts.presentation.fx.support;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class HemisphereSurfaceShape extends SphereSurfaceShape {
	protected boolean inverted = false;
	
	
	public HemisphereSurfaceShape(float r){
		super(r);
	}
	
	public void invert(boolean invert){
		inverted = invert;
	}

	@Override
	public void getRandomPoint(Vector3f store) {
		float z = FastMath.nextRandomFloat() * (inverted ? -1f : 1f); 
		store.set(randomComponent(), randomComponent(), z);
		store.normalizeLocal().multLocal(radius);
		store.addLocal(center);
	}
}
