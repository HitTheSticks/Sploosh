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
	 * Target lifetime of this tracer in seconds.
	 * */
	public float lifetime;
	
	/**
	 * How many seconds has this tracer lived?
	 * */
	public float age;
}
