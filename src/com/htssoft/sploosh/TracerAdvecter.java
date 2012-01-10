package com.htssoft.sploosh;

import com.htssoft.sploosh.presentation.FluidTracer;

public interface TracerAdvecter {
	public void advectTracers(FluidTracer[] tracers, float tpf);
	public boolean hasDriver();
	public void setHasDriver(boolean driver);
	public void stepSimulation(float tpf);
}
