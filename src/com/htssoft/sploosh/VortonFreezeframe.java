package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.space.OTree;

public class VortonFreezeframe {
	OTree vortonTree;
	protected Thread[] threads;
	protected FluidTracer[] currentWorkingTracers;
	protected LinkedBlockingQueue<WorkRange> workRanges = new LinkedBlockingQueue<VortonFreezeframe.WorkRange>();
	protected AtomicInteger outstandingWorkItems = new AtomicInteger(0);
	protected boolean debugPrintln = true;
	protected float currentTPF;
	
	public VortonFreezeframe(OTree vortons){
		vortonTree = vortons;
	}
	
	public void spawnThreads(int nThreads){
		threads = new Thread[nThreads];
		for (int i = 0 ; i < threads.length; i++){
			threads[i] = new Thread(); //TODO set runnable
		}
	}
	
	public void advectTracers(FluidTracer[] tracers){
		outstandingWorkItems.set(tracers.length);
		
		int blockSize = tracers.length / threads.length;
		
		int remainingCounter = tracers.length;
		int start = 0;
		
		while (remainingCounter > 0){
			workRanges.add(new WorkRange(start, start + blockSize - 1));
			start += blockSize;
			remainingCounter -= blockSize;
		}
		
		long ms = System.currentTimeMillis();
		synchronized (outstandingWorkItems){
			try {
				while (outstandingWorkItems.get() != 0){
					outstandingWorkItems.wait();
				}
				if (debugPrintln)
					System.out.println("Diffusion took (ms): " + (System.currentTimeMillis() - ms));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Thread responsible for update tracer locations.
	 * */
	protected class TracerThread implements Runnable {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>(1000);
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
					localVortons.clear();
					vortonTree.getRoot().getInfluentialVortons(tracer.position, localVortons);
					TracerMath.advectTracer(tracer, localVortons, vars, currentTPF);					
				}
				
				int newval = outstandingWorkItems.decrementAndGet();
				if (newval == 0){
					synchronized (outstandingWorkItems) {
						outstandingWorkItems.notifyAll();
					}
				}
			}
		}
	}

	
	protected class WorkRange {
		final int first;
		final int last;
		
		WorkRange(int first, int last){
			this.first = first;
			if (last > currentWorkingTracers.length - 1){
				this.last = currentWorkingTracers.length - 1;
			}
			else {
				this.last = last;
			}
		}
	}
}
