package com.htssoft.sploosh.threading;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class StaticThreadGroup<WORK_T> {
	protected Thread[] threads;
	protected LinkedBlockingQueue<WORK_T> waitingWork = new LinkedBlockingQueue<WORK_T>();
	protected CountDownLatch waitLatch;
	protected String groupName;
	protected Class<? extends Kernel<WORK_T>> kernelClass;
	
	public StaticThreadGroup(String name, Class<? extends Kernel<WORK_T>> kernelClass){
		this.kernelClass = kernelClass;
		groupName = name;
		initThreads();
	}
	
	public int nThreads(){
		return threads.length;
	}
	
	/**
	 * Submit work to be done.
	 * */
	public void submitWork(Collection<WORK_T> workItems, Object submitter){
		if (workItems.isEmpty()){
			return;
		}
		waitLatch = new CountDownLatch(workItems.size());
		waitingWork.addAll(workItems);
		try {
			waitLatch.await();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}
	
	public LinkedBlockingQueue<WORK_T> getQueue(){
		return waitingWork;
	}
	
	public void reportFinished(){
		if (waitLatch != null){
			waitLatch.countDown();
		}
	}
	
	private Kernel<WORK_T> makeKernel(){
		try {
			Constructor<? extends Kernel<WORK_T>> c = kernelClass.getConstructor();
			Kernel<WORK_T> retval = c.newInstance();
			retval.plumb(this);
			return retval;
		} catch (SecurityException ex) {
			ex.printStackTrace();
		} catch (NoSuchMethodException ex) {
			ex.printStackTrace();
		} catch (IllegalArgumentException ex) {
			ex.printStackTrace();
		} catch (InstantiationException ex) {
			ex.printStackTrace();
		} catch (IllegalAccessException ex) {
			ex.printStackTrace();
		} catch (InvocationTargetException ex) {
			ex.printStackTrace();
		}
		
		return null;
	}
	
	protected void initThreads(){
		int nThreads = ThreadingUtils.getCoreCount();
		threads = new Thread[nThreads];
		for (int i = 0; i < nThreads; i++){
			threads[i] = new Thread(makeKernel(), groupName + i);
			threads[i].setDaemon(true);
			threads[i].start();
		}
	}
}
