package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.space.OTree;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public class VortonFreezeframe implements TracerAdvecter {
	OTree vortonTree;
	protected Thread[] threads;
	protected FluidTracer[] currentWorkingTracers;
	protected LinkedBlockingQueue<WorkRange> workRanges = new LinkedBlockingQueue<WorkRange>();
	protected CountDownLatch workCompleted;
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
	
	public void spawnThreads(int nThreads){
		threads = new Thread[nThreads];
		for (int i = 0 ; i < threads.length; i++){
			threads[i] = new Thread(new TracerThread());
			threads[i].start();
		}
	}
	
	public void stopThreads(){
		for (Thread t : threads){
			t.interrupt();
		}
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
		
		List<WorkRange> ranges = WorkRange.divideWork(tracers.length, tracers, threads.length);
		workCompleted = new CountDownLatch(ranges.size());
		
		workRanges.addAll(ranges);
		
		try {
			workCompleted.await();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
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

	/**
	 * Thread responsible for update tracer locations.
	 * */
	protected class TracerThread implements Runnable {
		ThreadVars vars = new ThreadVars();
		Vector3f workingVel = new Vector3f(), transformedPos = new Vector3f();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>(vortonTree.nVortons());
		public void run(){
			mainloop:
			while (!Thread.interrupted()){
				WorkRange workRange;
				try {
					workRange = workRanges.take();
				} catch (InterruptedException ex) {
					break mainloop;
				}
				
				
				for (int i = workRange.first; i <= workRange.last; i++){
					FluidTracer tracer = currentWorkingTracers[i];
					if (tracer.age > tracer.lifetime || tracer.age < 0f){
						continue;
					}
					localVortons.clear();
					
					objectTransform.transformInverseVector(tracer.position, transformedPos);
					
					vortonTree.getInfluentialVortons(vars.temp0, tracer.radius, localVortons);
					TracerMath.computeVelocityFromVortons(transformedPos, localVortons, workingVel, vars.temp0, vars.temp1);
					
					objectTransform.getRotation().multLocal(workingVel);
					
					TracerMath.moveTracer(tracer, workingVel, vars, currentTPF);
				}
				
				workCompleted.countDown();
			}
		}
	}
}
