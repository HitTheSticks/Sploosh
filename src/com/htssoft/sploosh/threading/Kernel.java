package com.htssoft.sploosh.threading;

public abstract class Kernel<WORK_T> implements Runnable {
	protected StaticThreadGroup<WORK_T> group;
		
	public void plumb(StaticThreadGroup<WORK_T> group){
		this.group = group;
	}
		
	/**
	 * Process a work item.
	 * */
	public abstract void process(WORK_T work);
	
	/**
	 * Loops, taking work and passing it to processing.
	 * */
	public void run(){
		mainloop:
			while (!Thread.interrupted()){
				WORK_T workItem;
				try {
					workItem = group.getQueue().take();
				} catch (InterruptedException ex) {
					break mainloop;
				}
				process(workItem);
				group.reportFinished();
			}
	}
}
