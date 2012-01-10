package com.htssoft.sploosh;

import java.util.List;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public interface TracerAdvecter {
	public void advectTracers(FluidTracer[] tracers, float tpf);
	public boolean hasDriver();
	public void setHasDriver(boolean driver);
	public void stepSimulation(float tpf);
	public void updateTransform(Transform trans);
	public void traceVortons(List<Vector3f> vortons);
	public int getNVortons();
}
