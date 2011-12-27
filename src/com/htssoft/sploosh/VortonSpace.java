package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import com.htssoft.sploosh.space.OTree;
import com.htssoft.sploosh.space.UniformGrid;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class VortonSpace {
	public static final float VORTON_RADIUS = 0.001f;
	public static final float VORTON_RADIUS_CUBE = VORTON_RADIUS * VORTON_RADIUS * VORTON_RADIUS;
	public static final float ONE_OVER_4_PI = 1f / (4f * FastMath.PI);
	public static final float JACOBIAN_D = 0.01f;
	public static final float DT = 1f / 60f;
	public static final Vector3f[] jacobianOffsets = {
		new Vector3f(-JACOBIAN_D, 0, 0),
		new Vector3f(JACOBIAN_D, 0, 0),
		new Vector3f(0, -JACOBIAN_D, 0),
		new Vector3f(0, JACOBIAN_D, 0),
		new Vector3f(0, 0, -JACOBIAN_D),
		new Vector3f(0, 0, JACOBIAN_D)
	};
	protected ArrayList<Vorton> vortons;
	protected OTree vortonTree;
	protected LinkedBlockingQueue<Vorton> stretchWork = new LinkedBlockingQueue<Vorton>();
	protected LinkedBlockingQueue<DiffuseWorkItem> diffuseWork = new LinkedBlockingQueue<DiffuseWorkItem>();
	protected LinkedBlockingQueue<AdvectWorkItem> advectWork = new LinkedBlockingQueue<AdvectWorkItem>();
	//protected UniformGrid<Vector3f> velocityGrid;
	//protected int gridResolution;
	
	/**
	 * Create a new vorton simulation with the given
	 * number of vortons.
	 * */
	public VortonSpace(int nVortons, int gridResolution){
		//this.gridResolution = gridResolution;
		vortons = new ArrayList<Vorton>(nVortons);
		for (int i = 0; i < nVortons; i++){
			vortons.add(new Vorton());
		}
	}
	
	/**
	 * Step the simulation forward by dt.
	 * */
	public void stepSimulation(float dt){
		while (dt > DT){ 
			buildVortonTree();
//			velocityGrid = new UniformGrid<Vector3f>(vortonTree.getRoot().getMin(), vortonTree.getRoot().getMax(), 
//					gridResolution, 
//					Vector3f.ZERO);
			stretchAndTilt();
			dt -= DT;
		}
	}
	
	protected void buildVortonTree(){
		vortonTree = new OTree();
		for (Vorton v : vortons){
			vortonTree.getRoot().insert(v);
		}
	}
	
	protected void stretchAndTilt(){
		stretchWork.addAll(vortons);
	}
	
	protected void computeVelocityFromVortons(Vector3f position, List<Vorton> influences, 
											  Vector3f store, Vector3f temp1, Vector3f temp2){
		store.zero();
		for (Vorton v : influences){
			computeVelocityContribution(position, v, store, temp1, temp2);
		}
		store.multLocal(ONE_OVER_4_PI);
	}
	
	protected void computeVelocityContribution(Vector3f position, Vorton v, Vector3f accum, Vector3f temp1, Vector3f temp2){
		temp2.set(position).subtractLocal(v.position);
		float r = temp2.length();
		if (r < VORTON_RADIUS){
			r = VORTON_RADIUS_CUBE;
		}
		else {
			r = r * r * r;
		}
		temp1.set(v.vorticity).crossLocal(temp2).divideLocal(r);
		accum.addLocal(temp1);
	}
	
	protected void getJacobian(List<Vorton> influences, Vector3f position, ThreadVars vars){
		for (int i = 0; i < jacobianOffsets.length; i++){
			position.add(jacobianOffsets[i], vars.vec[i]);
		}
		
		for (int i = 0; i < jacobianOffsets.length; i++){
			computeVelocityFromVortons(vars.vec[i], influences, vars.temp2, vars.temp0, vars.temp1);
			vars.vec[i].set(vars.temp2);
		}
		
		
	}

	/**
	 * Work item for diffusion.
	 * */
	protected class DiffuseWorkItem {
		
	}
	
	/**
	 * Work item for advection of vortons.
	 * */
	protected class AdvectWorkItem {
		
	}
	
	protected class StretchThread implements Runnable {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> vortons = new ArrayList<Vorton>();
		
		public void run(){
			mainloop:
			while (!Thread.interrupted()){
				Vorton vorton;
				try {
					vorton = stretchWork.take();
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					break mainloop;
				}
				
				vortons.clear();
				vortonTree.getRoot().getInfluentialVortons(vorton.position, vortons);
				vortons.remove(vorton);
				getJacobian(vortons, vorton.position, vars);
				vars.mat.multLocal(DT);
				vars.mat.multLocal(vorton.vorticity);
			}
		}
	}
}
