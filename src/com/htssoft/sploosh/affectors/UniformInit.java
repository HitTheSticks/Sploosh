package com.htssoft.sploosh.affectors;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.presentation.FluidTracerInitializer;
import com.jme3.effect.shapes.EmitterShape;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public class UniformInit implements FluidTracerInitializer {
	protected Vector3f initVel = new Vector3f();
		
	public UniformInit(Vector3f initVel){
		this.initVel.set(initVel);
	}
	
	@Override
	public void initTracer(FluidTracer tracer, EmitterShape shape, Transform worldTransform) {
		shape.getRandomPointAndNormal(tracer.position, tracer.inertia);
		worldTransform.transformVector(tracer.position, tracer.position);
		tracer.inertia.set(initVel);
		worldTransform.getRotation().multLocal(tracer.inertia);
		
	}
}
