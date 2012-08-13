package com.htssoft.sploosh.threading;

public class ThreadingUtils {
	
	/**
	 * Find out how many cores are available to the JVM.
	 * */
	public static int getCoreCount(){
		int retval = Runtime.getRuntime().availableProcessors();
		return retval;
	}
}
