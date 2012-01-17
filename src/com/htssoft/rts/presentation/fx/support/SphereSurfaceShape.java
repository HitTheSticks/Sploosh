package com.htssoft.rts.presentation.fx.support;

import java.io.IOException;

import com.jme3.effect.shapes.EmitterShape;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class SphereSurfaceShape implements EmitterShape {
	protected float radius;
	protected Vector3f center = new Vector3f(0f, 0f, 0f);
	
	public SphereSurfaceShape(float r){
		radius = r;
	}
	
	public void setCenter(Vector3f center){
		this.center.set(center);
	}
	
	public void setCenter(float x, float y, float z){
		this.center.set(x, y, z);
	}

	@Override
	public void getRandomPoint(Vector3f store) {
		store.set(randomComponent(), randomComponent(), randomComponent());
		store.normalizeLocal().multLocal(radius);
		store.addLocal(center);
	}
	
	protected float randomComponent(){
		return FastMath.nextRandomFloat() * (FastMath.nextRandomFloat() < 0.5f ? -1 : 1);
	}

	@Override
	public void getRandomPointAndNormal(Vector3f store, Vector3f normal) {
		getRandomPoint(store);
		normal.set(store).subtractLocal(center).normalizeLocal();
	}
	
	@Override
	public void write(JmeExporter ex) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void read(JmeImporter im) throws IOException {
		throw new UnsupportedOperationException();
	}


	@Override
	public EmitterShape deepClone() {
		throw new UnsupportedOperationException();
	}

	public void setRadius(float r){
		radius = r;
	}
	
	public float getRadius(){
		return radius;
	}
	
	@Override
	public boolean isReady() {
		return true;
	}

}
