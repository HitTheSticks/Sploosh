package com.htssoft.sploosh;

public class ThreadingUtils {
	
	/**
	 * Find out how many cores are available to the JVM.
	 * */
	public static int getCoreCount(){
		return Runtime.getRuntime().availableProcessors();
	}
}
