package com.htssoft.sploosh.affectors;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.presentation.FluidTracerAffector;
import com.htssoft.sploosh.presentation.FluidTracerInitializer;
import com.jme3.effect.shapes.EmitterShape;
import com.jme3.math.Transform;

public class BurstAffector implements FluidTracerAffector, FluidTracerInitializer {
	protected float force = 1f;
		
	public BurstAffector(float force){
		this.force = force;
	}
	
	@Override
	public void initTracer(FluidTracer tracer, EmitterShape shape, Transform worldTransform) {
		shape.getRandomPointAndNormal(tracer.position, tracer.inertia);
		worldTransform.transformVector(tracer.position, tracer.position);
		worldTransform.getRotation().multLocal(tracer.inertia);
		tracer.inertia.normalizeLocal().multLocal(force);
		
	}

	@Override
	public void affectTracer(FluidTracer tracer, float tpf) {
		// TODO Auto-generated method stub

	}

}
