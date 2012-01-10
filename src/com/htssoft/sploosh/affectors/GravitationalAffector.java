package com.htssoft.sploosh.affectors;

import java.util.ArrayList;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.presentation.FluidTracerAffector;
import com.jme3.math.Vector3f;
import com.jme3.util.TempVars;

public class GravitationalAffector implements FluidTracerAffector {
	protected Vector3f mainCenter = new Vector3f();
	protected ArrayList<Vector3f> centers;
	protected float strength;
	
	public GravitationalAffector(Vector3f center, float strength){
		mainCenter.set(center);
		this.strength = strength;
	}
	
	public void addCenter(Vector3f newC){
		if (centers == null){
			centers = new ArrayList<Vector3f>();
		}
		centers.add(new Vector3f(newC));
	}
	
	@Override
	public void affectTracer(FluidTracer tracer, float tpf) {
		
		contrib(tracer, tpf, mainCenter);
		
		if (centers != null){
			for (Vector3f c : centers){
				contrib(tracer, tpf, c);
			}
		}
	}
	
	protected void contrib(FluidTracer tracer, float tpf, Vector3f center){
		TempVars vars = TempVars.get();
		
		Vector3f force = vars.vect1;
		force.set(center).subtractLocal(tracer.position);
		
		force.normalizeLocal();
		force.multLocal(strength).multLocal(tpf);
		tracer.inertia.addLocal(force);
		
		vars.release();
	}

}
