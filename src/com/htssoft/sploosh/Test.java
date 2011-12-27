package com.htssoft.sploosh;

public class Test {
	public static void main(String[] args){
		VortonSpace space = new VortonSpace(6000, 0.5f, 10);
		space.randomizeVortons();
		space.initializeThreads(4);
		for (int i = 0; i < 50; i++){
			space.stepSimulation(0.02f);
		}
	}
}
