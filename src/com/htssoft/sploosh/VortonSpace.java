package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.htssoft.sploosh.space.OTree;
import com.htssoft.sploosh.space.OTree.OTreeNode;
import com.htssoft.sploosh.space.UniformGrid;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class VortonSpace {
	public static final float VORTON_RADIUS = 0.01f;
	public static final float VORTON_RADIUS_SQ = VORTON_RADIUS * VORTON_RADIUS;
	public static final float VORTON_RADIUS_CUBE = VORTON_RADIUS * VORTON_RADIUS * VORTON_RADIUS;
	public static final float AVOID_SINGULARITY = FastMath.pow(Float.MIN_VALUE, 1f / 3f);
	public static final float ONE_OVER_4_PI = 1f / (4f * FastMath.PI);
	public static final float FOUR_THIRDS_PI = (4f / 3f) * FastMath.PI;
	public static final float JACOBIAN_D = 0.001f;
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
	protected LinkedBlockingQueue<Vorton> advectWork = new LinkedBlockingQueue<Vorton>();
	protected LinkedBlockingQueue<Vector3f> tracerWork = new LinkedBlockingQueue<Vector3f>();
	protected UniformGrid<Vector3f> velocityGrid;
	protected int gridResolution;
	protected AtomicInteger outstandingWorkItems = new AtomicInteger();
	protected float timeAccumulator = 0f;
	protected ArrayList<Thread> threads = new ArrayList<Thread>();
	protected float viscosity = 0.5f;
	
	/**
	 * Create a new vorton simulation with the given
	 * number of vortons.
	 * */
	public VortonSpace(int nVortons, float viscosity, int gridResolution){
		this.viscosity = viscosity;
		this.gridResolution = gridResolution;
		vortons = new ArrayList<Vorton>(nVortons);
		for (int i = 0; i < nVortons; i++){
			vortons.add(new Vorton());
		}
	}
	
	public void initializeThreads(int workThreads){
		for (int i = 0; i < workThreads; i++){
			StretchThread st = new StretchThread();
			Thread t = new Thread(st);
			t.setDaemon(true);
			threads.add(t);
			t.start();
			
			AdvectThread at = new AdvectThread();
			t = new Thread(at);
			t.setDaemon(true);
			threads.add(t);
			t.start();
			
			DiffuseThread d = new DiffuseThread();
			t = new Thread(d);
			t.setDaemon(true);
			threads.add(t);
			t.start();
			
			TracerThread tt = new TracerThread();
			t = new Thread(tt);
			t.setDaemon(true);
			threads.add(t);
			t.start();
		}
	}
	
	public void finalize(){
		for (Thread t : threads){
			t.interrupt();
		}
	}
	
	public void randomizeVortons(){
		for (Vorton v : vortons){
			float s = FastMath.nextRandomFloat() * 1f;
			v.position.set(FastMath.nextRandomFloat() * s, FastMath.nextRandomFloat() * s, FastMath.nextRandomFloat() * s);
			v.vorticity.set(FastMath.nextRandomFloat() * 0.5f, FastMath.nextRandomFloat() * 0.5f, FastMath.nextRandomFloat() * 0.5f);
			//v.vorticity.set(0.01f, 0.01f, 0.01f);
		}
	}
	
	public void distributeVortons(Vector3f min, Vector3f max){
		float particlesPerSide = (float) Math.cbrt(vortons.size());
		int nParticles = (int) particlesPerSide + 1;
		float xStep = (max.x - min.x) / particlesPerSide;
		float yStep = (max.y - min.y) / particlesPerSide;
		float zStep = (max.z - min.z) / particlesPerSide;
		
		int index = 0;
		outer:
		for (int i = 0; i < nParticles; i++){
			float x = xStep * i + min.x;
			for (int j = 0; j < nParticles; j++){
				float y = yStep * j + min.y;
				for (int k = 0; k < nParticles; k++){
					float z = zStep * k + min.z;
					if (index >= vortons.size()){
						break outer;
					}
					vortons.get(index).position.set(x, y, z);
					vortons.get(index).vorticity.set(0, 12000f, 12000f);
					++index;
				}
			}
		}
	}
	
	/**
	 * Step the simulation forward by dt.
	 * */
	public void stepSimulation(float dt){
		timeAccumulator += dt;
		if (timeAccumulator > DT){ 
			buildVortonTree();
			
			stretchAndTilt();
			diffuseVorticity();
			advectVortons();
			timeAccumulator -= DT;
		}
	}
	
	public void advectTracers(List<Vector3f> tracerPositions){
		if (vortonTree == null){
			return;
		}
		outstandingWorkItems.set(tracerPositions.size());
		tracerWork.addAll(tracerPositions);
		long ms = System.currentTimeMillis();
		synchronized (outstandingWorkItems){
			try {
				while (outstandingWorkItems.get() != 0){
					outstandingWorkItems.wait();
				}
				//System.out.println("Tracers took (ms): " + (System.currentTimeMillis() - ms));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Build the OTree of vortons.
	 * */
	protected void buildVortonTree(){
		vortonTree = new OTree();
		long ms = System.currentTimeMillis();
		for (Vorton v : vortons){
			vortonTree.getRoot().insert(v);
		}
		vortonTree.getRoot().updateDerivedQuantities();
		//System.out.println("Tree build took (ms) : " + (System.currentTimeMillis() - ms) + 
				//" Bounds: " + vortonTree.getRoot().getMin() + ", " + vortonTree.getRoot().getMax());
	}
	
	protected void buildVelocityGrid(){
		velocityGrid = new UniformGrid<Vector3f>(vortonTree.getRoot().getMin(), vortonTree.getRoot().getMax(), 
				gridResolution, 
				Vector3f.ZERO);
	}
	
	protected void stretchAndTilt(){
		outstandingWorkItems.set(vortons.size());
		stretchWork.addAll(vortons);
		long ms = System.currentTimeMillis();
		synchronized (outstandingWorkItems){
			try {
				while (outstandingWorkItems.get() != 0){
					outstandingWorkItems.wait();
				}
				//System.out.println("Stretch and tilt took (ms): " + (System.currentTimeMillis() - ms));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	
	protected void advectVortons(){
		outstandingWorkItems.set(vortons.size());
		advectWork.addAll(vortons);
		long ms = System.currentTimeMillis();
		synchronized (outstandingWorkItems){
			try {
				while (outstandingWorkItems.get() != 0){
					outstandingWorkItems.wait();
				}
				//System.out.println("Advection took (ms): " + (System.currentTimeMillis() - ms));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	protected void diffuseVorticity(){
		ArrayList<OTreeNode> groups = new ArrayList<OTree.OTreeNode>();
		vortonTree.getRoot().getLeaves(groups);
		
		outstandingWorkItems.set(groups.size());
		
		for (OTreeNode node : groups){
			DiffuseWorkItem item = new DiffuseWorkItem();
			node.getItems(item.vortons);
			diffuseWork.add(item);
		}
		
		long ms = System.currentTimeMillis();
		synchronized (outstandingWorkItems){
			try {
				while (outstandingWorkItems.get() != 0){
					outstandingWorkItems.wait();
				}
				//System.out.println("Diffusion took (ms): " + (System.currentTimeMillis() - ms));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
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
		float dist2 = temp2.lengthSquared() + AVOID_SINGULARITY;
		float oneOverDist = 1f / temp2.length();
		float distLaw;
		if (dist2 < VORTON_RADIUS_SQ){
			distLaw = oneOverDist / VORTON_RADIUS_SQ;
		}
		else {
			 distLaw = oneOverDist / dist2;
		}
		
		temp1.set(v.vorticity).multLocal(FOUR_THIRDS_PI * VORTON_RADIUS_CUBE).crossLocal(temp2).multLocal(distLaw);
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
		
		vars.vec[1].subtractLocal(vars.vec[0]).multLocal(JACOBIAN_D); // d/dx
		vars.vec[3].subtractLocal(vars.vec[2]).multLocal(JACOBIAN_D); // d/dy
		vars.vec[5].subtractLocal(vars.vec[4]).multLocal(JACOBIAN_D); // d/dz
		
		vars.mat.setColumn(0, vars.vec[1]);
		vars.mat.setColumn(1, vars.vec[3]);
		vars.mat.setColumn(2, vars.vec[5]);
	}
	
	protected void advectVorton(Vorton v, List<Vorton> influences, ThreadVars vars){
		computeVelocityFromVortons(v.position, influences, vars.temp0, vars.temp1, vars.temp2);
		
		v.position.addLocal(vars.temp0.multLocal(DT));
	}
	
	protected void advectTracer(Vector3f tracer, List<Vorton> influences, ThreadVars vars){
		computeVelocityFromVortons(tracer, influences, vars.temp0, vars.temp1, vars.temp2);
		tracer.addLocal(vars.temp0.multLocal(DT));
	}
	
	protected void diffuseGroupOfVortons(List<Vorton> vortons, ThreadVars vars){
		for (Vorton v : vortons){
			vars.temp1.zero();
			for (Vorton w : vortons){
				if (w == v){
					continue;
				}
				
				vars.temp0.set(w.vorticity).subtractLocal(v.vorticity).multLocal(viscosity);
				vars.temp1.addLocal(vars.temp0);
			}
			v.vorticity.addLocal(vars.temp1.multLocal(DT));
		}
	}

	/**
	 * Work item for diffusion.
	 * */
	protected class DiffuseWorkItem {
		public ArrayList<Vorton> vortons = new ArrayList<Vorton>();
	}
	
	protected class StretchThread implements Runnable {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>();
		
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
				
				localVortons.clear();
				vortonTree.getRoot().getInfluentialVortons(vorton.position, localVortons);

				getJacobian(localVortons, vorton.position, vars);
				vars.temp0.set(vorton.vorticity);
				vars.mat.multLocal(vars.temp0);
				vars.temp0.multLocal(DT);
				vorton.vorticity.addLocal(vars.temp0);
				int newval = outstandingWorkItems.decrementAndGet();
				if (newval == 0){
					synchronized (outstandingWorkItems) {
						outstandingWorkItems.notifyAll();
					}
				}
			}
		}
	}
	
	protected class AdvectThread implements Runnable {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>();
		public void run(){
			mainloop:
			while (!Thread.interrupted()){
				Vorton vorton;
				try {
					vorton = advectWork.take();
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					break mainloop;
				}
				
				localVortons.clear();
				vortonTree.getRoot().getInfluentialVortons(vorton.position, localVortons);
				localVortons.remove(vorton);
				advectVorton(vorton, localVortons, vars);
				
				int newval = outstandingWorkItems.decrementAndGet();
				if (newval == 0){
					synchronized (outstandingWorkItems) {
						outstandingWorkItems.notifyAll();
					}
				}
			}
		}
	}

	protected class DiffuseThread implements Runnable {
		ThreadVars vars = new ThreadVars();

		public void run(){
			mainloop:
				while (!Thread.interrupted()){
					DiffuseWorkItem item;
					try {
						item = diffuseWork.take();
					} catch (InterruptedException ex) {
						ex.printStackTrace();
						break mainloop;
					}

					diffuseGroupOfVortons(item.vortons, vars);

					int newval = outstandingWorkItems.decrementAndGet();
					if (newval == 0){
						synchronized (outstandingWorkItems) {
							outstandingWorkItems.notifyAll();
						}
					}
				}
		}
	}
	
	protected class TracerThread implements Runnable {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>();
		public void run(){
			mainloop:
			while (!Thread.interrupted()){
				Vector3f tracer;
				try {
					tracer = tracerWork.take();
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					break mainloop;
				}
				
				localVortons.clear();
				vortonTree.getRoot().getInfluentialVortons(tracer, localVortons);
				advectTracer(tracer, localVortons, vars);
				
				int newval = outstandingWorkItems.decrementAndGet();
				if (newval == 0){
					synchronized (outstandingWorkItems) {
						outstandingWorkItems.notifyAll();
					}
				}
			}
		}
	}
}
