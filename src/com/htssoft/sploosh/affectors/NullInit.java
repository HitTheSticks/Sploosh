package com.htssoft.sploosh.affectors;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.presentation.FluidTracerInitializer;
import com.jme3.effect.shapes.EmitterShape;
import com.jme3.math.Transform;

public class NullInit implements FluidTracerInitializer {

	@Override
	public void initTracer(FluidTracer tracer, EmitterShape shape, Transform worldTransform) {
		shape.getRandomPointAndNormal(tracer.position, tracer.inertia);
		worldTransform.transformVector(tracer.position, tracer.position);
		tracer.inertia.zero();
	}

}
