package com.htssoft.sploosh;

import java.util.List;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class TracerMath {

	/**
	 * Given a list of vortons, compute the field velocity there.
	 * */
	protected static void computeVelocityFromVortons(Vector3f position, List<Vorton> influences, 
											  Vector3f store, Vector3f temp1, Vector3f temp2){
		store.zero();
		for (Vorton v : influences){
			computeVelocityContribution(position, v, store, temp1, temp2);
		}
		store.multLocal(VortonSpace.ONE_OVER_4_PI);
	}

	/**
	 * Given a vorton, find its influence on the field velocity.
	 * */
	protected static void computeVelocityContribution(Vector3f position, Vorton v, Vector3f accum, Vector3f temp1, Vector3f temp2){
		temp2.set(position).subtractLocal(v.getPosition());
		float dist2 = temp2.lengthSquared() + VortonSpace.AVOID_SINGULARITY;
		float oneOverDist = 1f / FastMath.sqrt(dist2);
		float distLaw;
		if (dist2 < VortonSpace.VORTON_RADIUS_SQ){
			distLaw = oneOverDist / VortonSpace.VORTON_RADIUS_SQ;
		}
		else {
			 distLaw = oneOverDist / dist2;
		}
		
		temp1.set(v.getVort()).multLocal(VortonSpace.FOUR_THIRDS_PI * VortonSpace.VORTON_RADIUS_CUBE).crossLocal(temp2).multLocal(distLaw);
		accum.addLocal(temp1);
	}

	public static void advectWorldTracer(FluidTracer tracer, Vector3f localPosition, List<Vorton> influences, ThreadVars vars, float currentTPF){
		Vector3f fieldVelocity = vars.vec[0];
		computeVelocityFromVortons(localPosition, influences, fieldVelocity, vars.vec[1], vars.vec[2]);
		if (fieldVelocity.length() < tracer.velocity.length()){
			tracer.velocity.interpolate(fieldVelocity, currentTPF);
		}
	}
	
	/**
	 * Move a tracer.
	 * */
	public static void advectTracer(FluidTracer tracer, List<Vorton> influences, ThreadVars vars, float currentTPF){
		computeVelocityFromVortons(tracer.position, influences, vars.temp0, vars.temp1, vars.temp2);
		float step = VortonSpace.DT < currentTPF ? VortonSpace.DT : currentTPF;
		tracer.velocity.set(vars.temp0);
		tracer.position.addLocal(vars.temp0.multLocal(step));
		tracer.age += step;
	}

}
