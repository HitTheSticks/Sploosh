package com.htssoft.sploosh.presentation;

import com.jme3.effect.shapes.EmitterShape;
import com.jme3.math.Transform;

public interface FluidTracerInitializer {
	/**
	 * Initialize a new tracer.
	 * */
	public void initTracer(FluidTracer tracer, EmitterShape shape, Transform worldTransform);
	
}
