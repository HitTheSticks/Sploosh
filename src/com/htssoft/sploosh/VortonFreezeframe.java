package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.space.OTree;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public class VortonFreezeframe implements TracerAdvecter {
	OTree vortonTree;
	protected Thread[] threads;
	protected FluidTracer[] currentWorkingTracers;
	protected LinkedBlockingQueue<WorkRange> workRanges = new LinkedBlockingQueue<VortonFreezeframe.WorkRange>();
	protected LinkedBlockingQueue<WorkRange> completedRanges = new LinkedBlockingQueue<VortonFreezeframe.WorkRange>();
	protected boolean debugPrintln = true;
	protected float currentTPF;
	protected Transform objectTransform = new Transform();

	
	public VortonFreezeframe(OTree vortons){
		vortonTree = vortons;
	}
	
	public void spawnThreads(int nThreads){
		threads = new Thread[nThreads];
		for (int i = 0 ; i < threads.length; i++){
			threads[i] = new Thread(); //TODO set runnable
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
		completedRanges.clear();
		currentWorkingTracers = tracers;
		currentTPF = tpf;
		
		//ideally, how big is a block?
		int blockSize = tracers.length / threads.length;
		int maxIndex = tracers.length - 1;
		
		int remainingCounter = tracers.length;
		int start = 0;
		int workItems = 0;
		boolean remainderFlag = false;
		//I'm kinda tired, so yes, I'm sure there's something way more elegant.
		while (remainingCounter > 0){ 
			workItems++;
			remainingCounter -= blockSize;
			int end = start + blockSize - 1;
			
			if (end > maxIndex){
				end = maxIndex;
			}
			
			/*
			 * I think this heuristic works for small numbers of threads
			 * (as is typical on most computers). At least none of the
			 * numbers I've run through here have given wildly stupid
			 * answers with, like, 4 or 8 or 1741 threads.
			 * */
			if (remainingCounter < blockSize){
				end += remainingCounter;
				remainderFlag = true;
			}
			
			workRanges.add(new WorkRange(start, end));
			start = end + 1;
			
			if (remainderFlag){
				break;
			}
		}
		
		while (workItems > 0){
			try {
				completedRanges.take();
				workItems--;
			} catch (InterruptedException e) {
				System.err.println("GL Thread interrupted?");
				e.printStackTrace();
				break;
			}
			completedRanges.clear();
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
					
					vortonTree.getInfluentialVortons(vars.temp0, localVortons);
					TracerMath.computeVelocityFromVortons(transformedPos, localVortons, workingVel, vars.temp0, vars.temp1);
					
					objectTransform.getRotation().multLocal(workingVel);
					
					TracerMath.moveTracer(tracer, workingVel, vars, currentTPF);
				}
				
				completedRanges.add(workRange);
			}
		}
	}

	/**
	 * A range of work for a worker thread.
	 * */
	protected class WorkRange {
		final int first;
		final int last;

		WorkRange(int first, int last){
			this.first = first;
			this.last = last;
		}
	}
}
