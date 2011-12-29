package com.htssoft.sploosh;

import com.jme3.math.Vector3f;

public class SimpleVorton extends Vorton {
	protected Vector3f position = new Vector3f();
	protected Vector3f vort = new Vector3f();
	
	@Override
	public Vector3f getPosition() {
		return position;
	}

	@Override
	public void getPosition(Vector3f store) {
		store.set(position);
	}

	@Override
	public void setPosition(Vector3f value) {
		position.set(value);
	}

	@Override
	public void getVort(Vector3f store) {
		store.set(vort);
	}

	@Override
	public Vector3f getVort() {
		return vort;
	}

	@Override
	public void setVort(Vector3f value) {
		vort.set(value);
	}
}
