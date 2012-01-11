package com.htssoft.sploosh.presentation;

import com.jme3.math.Vector3f;

public class FluidTracer {
	/**
	 * Position of the fluid tracer at the current time.
	 * */
	public final Vector3f position = new Vector3f();
	
	/**
	 * Instantaneous velocity of the tracer at the current time.
	 * */
	public final Vector3f velocity = new Vector3f();
	
	/**
	 * This is an intertial component, which may be affected
	 * by outside forces.
	 * 
	 * The reynolds ratio below determines how much affect
	 * this component has versus vorticity advection.
	 * */
	public final Vector3f inertia = new Vector3f();
	
	/**
	 * How much contribution does the vorticity advection
	 * have versus the vorticity.
	 * */
	public float reynoldsRatio = 1f;
	
	/**
	 * Particle's physical radius.
	 * */
	public float radius = 0.1f;
	
	/**
	 * Target lifetime of this tracer in seconds.
	 * */
	public float lifetime = 0f;
	
	/**
	 * How many seconds has this tracer lived?
	 * */
	public float age;
	
	public void clear(){
		position.zero();
		velocity.zero();
		inertia.zero();
	}
}
