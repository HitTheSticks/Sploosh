package com.htssoft.sploosh.threading;

import java.util.ArrayList;
import java.util.List;

public class WorkRange {
	public final int first;
	public final int last;
	public final Object workingSet;
	public final Object parent;

	WorkRange(int first, int last, Object workingSet, Object parent){
		this.first = first;
		this.last = last;
		this.workingSet = workingSet;
		this.parent = parent;
	}
	
	public static List<WorkRange> divideWork(int nItems, Object workingSet, Object parent, int nThreads){
		List<WorkRange> ranges = new ArrayList<WorkRange>();
		
		int blockSize = nItems / nThreads;
		int maxIndex = nItems - 1;
		
		int remainingCounter = nItems;
		int start = 0;
		boolean remainderFlag = false;
		//I'm kinda tired, so yes, I'm sure there's something way more elegant.
		while (remainingCounter > 0){ 
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
			
			ranges.add(new WorkRange(start, end, workingSet, parent));
			start = end + 1;
			
			if (remainderFlag){
				break;
			}
		}
		
		return ranges;
	}
}

