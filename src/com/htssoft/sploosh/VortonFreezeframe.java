package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.List;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.space.OTree;
import com.htssoft.sploosh.threading.Kernel;
import com.htssoft.sploosh.threading.StaticThreadGroup;
import com.htssoft.sploosh.threading.WorkRange;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public class VortonFreezeframe implements TracerAdvecter {
	protected final static StaticThreadGroup<WorkRange> advectionThreads = 
		new StaticThreadGroup<WorkRange>("FreezeframeAdvection", TracerKernel.class);
	OTree vortonTree;
	protected FluidTracer[] currentWorkingTracers;

	protected boolean debugPrintln = true;
	protected float currentTPF;
	protected Transform objectTransform = new Transform();
	protected ArrayList<Vorton> vortonList;

	
	public VortonFreezeframe(OTree vortons){
		vortonTree = vortons;
	}
	
	public int getNVortons(){
		return vortonTree.nVortons();
	}
	
	/**
	 * Deprecated. The number of threads spawned is now
	 * a function of the number of cores available.
	 * */
	@Deprecated
	public void spawnThreads(int notUsed){
		spawnThreads();
	}
	
	public void spawnThreads(){
		initializeThreads();
	}
	
	/**
	 * Initializes the threads.
	 * */
	@Deprecated
	public void initializeThreads(){
	}
	
	@Deprecated
	public void stopThreads(){
	}
	
	/**
	 * Update the transform to match animation in the scene.
	 * */
	public void updateTransform(Transform trans){
		objectTransform.set(trans);
	}
	
	public boolean hasDriver(){
		return true;
	}
	
	public void setHasDriver(boolean hasDriver){
		
	}
	
	public void stepSimulation(float tpf){
		
	}
	
	/**
	 * This advects tracers multithreadedly.
	 * 
	 * This is basically the same logic as advects tracers
	 * in the VortonSpace. However, since the vortons
	 * don't move, you can expect different motion.
	 * */
	public void advectTracers(FluidTracer[] tracers, float tpf){
		currentWorkingTracers = tracers;
		currentTPF = tpf;
		
		List<WorkRange> ranges = WorkRange.divideWork(tracers.length, tracers, this, advectionThreads.nThreads());
		advectionThreads.submitWork(ranges, this);
	}
	
	public void traceVortons(List<Vector3f> vortons){
		if (vortonList == null){
			vortonList = new ArrayList<Vorton>(getNVortons());
			vortonTree.getVortons(vortonList);
		}
		
		for (int i = 0; i < vortons.size() && i < vortonList.size(); i++){
			vortons.get(i).set(vortonList.get(i).getPosition());
		}
	}

	protected static class TracerKernel extends Kernel<WorkRange> {
		ThreadVars vars = new ThreadVars();
		Vector3f workingVel = new Vector3f(), transformedPos = new Vector3f();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>(100);
		
		public TracerKernel(){
			
		}
		
		public void process(WorkRange workRange){
			VortonFreezeframe vff = (VortonFreezeframe) workRange.parent;
			
			for (int i = workRange.first; i <= workRange.last; i++){
				FluidTracer tracer = vff.currentWorkingTracers[i];
				if (tracer.age > tracer.lifetime || tracer.age < 0f){
					continue;
				}
				localVortons.clear();
				
				vff.objectTransform.transformInverseVector(tracer.position, transformedPos);
				
				vff.vortonTree.getInfluentialVortons(transformedPos, tracer.radius, localVortons);
				TracerMath.computeVelocityFromVortons(transformedPos, localVortons, workingVel, vars.temp0, vars.temp1);
				
				vff.objectTransform.getRotation().multLocal(workingVel);
				
				TracerMath.moveTracer(tracer, workingVel, vars, vff.currentTPF);
			}
		}
	}
}
