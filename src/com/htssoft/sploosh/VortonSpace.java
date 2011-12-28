package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.htssoft.sploosh.space.OTree;
import com.htssoft.sploosh.space.OTree.OTreeNode;
import com.htssoft.sploosh.space.UniformGrid;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class VortonSpace {
	public static final float VORTON_RADIUS = 0.1f;
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
	
	protected AtomicReference<Vector3f[]> frontPos = new AtomicReference<Vector3f[]>();
	protected AtomicReference<Vector3f[]> backPos = new AtomicReference<Vector3f[]>();
	protected AtomicReference<Vector3f[]> frontVort = new AtomicReference<Vector3f[]>();
	protected AtomicReference<Vector3f[]> backVort = new AtomicReference<Vector3f[]>();
		
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
	protected float currentTPF = 0f;
	protected Thread updateThread;
	protected Object updateThreadMonitor = new Object();
	
	/**
	 * Create a new vorton simulation with the given
	 * number of vortons.
	 * */
	public VortonSpace(int nVortons, float viscosity, int gridResolution){
		this.viscosity = viscosity;
		this.gridResolution = gridResolution;
		vortons = new ArrayList<Vorton>(nVortons);
		for (int i = 0; i < nVortons; i++){
			vortons.add(new BufferedVorton(i));
		}
		
		Vector3f[] positions = new Vector3f[nVortons];
		Vector3f[] backPositions = new Vector3f[nVortons];
		Vector3f[] vorticities = new Vector3f[nVortons];
		Vector3f[] backVorticities = new Vector3f[nVortons];
		
		for (int i = 0; i < nVortons; i++){
			positions[i] = new Vector3f();
			backPositions[i] = new Vector3f();
			vorticities[i] = new Vector3f();
			backVorticities[i] = new Vector3f();
		}
		
		frontPos.set(positions);
		backPos.set(backPositions);
		frontVort.set(vorticities);
		backVort.set(backVorticities);
	}
	
	protected void swapBuffers(){
		Vector3f[] t = frontPos.get();
		frontPos.set(backPos.get());
		backPos.set(t);
		
		t = frontVort.get();
		frontVort.set(backVort.get());
		backVort.set(t);
	}
	
	public void initializeThreads(int workThreads){
//		updateThread = new Thread(new Runnable() {
//			@Override
//			public void run() {
//				while (!Thread.interrupted()){
//					try {
//						synchronized (updateThreadMonitor) {
//							if (timeAccumulator < DT){
//								updateThreadMonitor.wait();
//							}
//						}
//					} catch (InterruptedException ex) {
//						break;
//					}
//					simulateStep();
//				}
//			}
//		}, "FluidUpdate");
//		updateThread.setDaemon(true);
//		updateThread.start();
		for (int i = 0; i < workThreads; i++){
			StretchThread st = new StretchThread();
			Thread t = new Thread(st, "Stretch/Tilt");
			t.setDaemon(true);
			threads.add(t);
			t.start();
			
			AdvectThread at = new AdvectThread();
			t = new Thread(at, "VortonAdvect");
			t.setDaemon(true);
			threads.add(t);
			t.start();
			
			DiffuseThread d = new DiffuseThread();
			t = new Thread(d, "Diffuse");
			t.setDaemon(true);
			threads.add(t);
			t.start();
			
			TracerThread tt = new TracerThread();
			t = new Thread(tt, "TracerAdvect");
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
			v.getPosition().set(FastMath.nextRandomFloat() * s, FastMath.nextRandomFloat() * s, FastMath.nextRandomFloat() * s);
			v.getVort().set(FastMath.nextRandomFloat() * 0.5f, FastMath.nextRandomFloat() * 0.5f, FastMath.nextRandomFloat() * 0.5f);
			//v.vorticity.set(0.01f, 0.01f, 0.01f);
		}
		swapBuffers(); //swap new values to back buffer for first run
	}
	
	public void distributeVortons(Vector3f min, Vector3f max){
		float particlesPerSide = (float) Math.cbrt(vortons.size());
		int nParticles = (int) particlesPerSide + 1;
		float xStep = (max.x - min.x) / particlesPerSide;
		float yStep = (max.y - min.y) / particlesPerSide;
		float zStep = (max.z - min.z) / particlesPerSide;
		
		Vector3f temp = new Vector3f();
		
		int index = 0;
		outer:
		for (int i = 0; i < nParticles; i++){
			float y = yStep * i + min.y;
			for (int j = 0; j < nParticles; j++){
				float x = xStep * j + min.x;
				for (int k = 0; k < nParticles; k++){
					float z = zStep * k + min.z;
					if (index >= vortons.size()){
						break outer;
					}
					BufferedVorton bv = (BufferedVorton) vortons.get(index);
					temp.set(x, y, z);
					bv.initializeAll(temp, Vector3f.ZERO);
					++index;
				}
			}
		}
		swapBuffers(); //swap new values to back buffer for first run
	}
	
	public void injectVortexRing(float radius, float thickness, float strength, Vector3f direction, Vector3f center){
		Vector3f fromCenter = new Vector3f();
		float tween;
		Vector3f ptOnLine = new Vector3f();
		Vector3f rho = new Vector3f();
		float rhoL;
		float distAlongDir;
		float radCore;
		
		Vector3f temp = new Vector3f();
		
		Vector3f rhoHat = new Vector3f();
		Vector3f phiHat = new Vector3f();
		
		for (Vorton vI : vortons){
			BufferedVorton v = (BufferedVorton) vI;
			fromCenter.set(v.getPosition()).subtractLocal(center);
			
			tween = fromCenter.dot(direction);
			
			temp.set(direction).multLocal(tween);
			ptOnLine.set(center).addLocal(temp);
			
			rho.set(v.getPosition()).subtractLocal(ptOnLine);
			rhoL = rho.length();
			distAlongDir = direction.dot(fromCenter);
			
			radCore = FastMath.sqr(rhoL - radius) + FastMath.sqr(distAlongDir);
			radCore = FastMath.sqrt(radCore);
			
			
			if (radCore < thickness){
				float vortProfile = radCore < thickness ? 
						0.5f * (FastMath.cos(FastMath.PI * radCore / thickness) + 1f) 
						: 0f;
				float vortPhi = vortProfile;
				rhoHat.set(rho);
				rhoHat.normalizeLocal();
				direction.cross(rhoHat, phiHat);
				
				temp.set(phiHat).multLocal(vortPhi * strength);
				
				temp.addLocal(v.getVort());
				v.setVort(temp);
				System.out.println(temp);
			}
			else {
				v.setVort(Vector3f.ZERO);
			}
			v.setPosition(v.getPosition()); //copy to front buffer
		}
	}
	
	public void injectJetRing(float radius, float thickness, float strength, Vector3f direction, Vector3f center){
		float radiusOuter = radius + thickness;
		Vector3f fromCenter = new Vector3f();
		float tween;
		Vector3f ptOnLine = new Vector3f();
		Vector3f rho = new Vector3f();
		float rhoL;
		float distAlongDir;
		
		Vector3f temp = new Vector3f();
		
		Vector3f rhoHat = new Vector3f();
		Vector3f phiHat = new Vector3f();
		
		for (Vorton vI : vortons){
			BufferedVorton v = (BufferedVorton) vI;
						
			fromCenter.set(v.getPosition()).subtractLocal(center);
			tween = fromCenter.dot(direction);
			
			temp.set(direction).multLocal(tween);
			ptOnLine.set(center).addLocal(temp);
			rho.set(v.getPosition()).subtractLocal(ptOnLine);
			
			rhoL = rho.length();
			distAlongDir = direction.dot(fromCenter);
			
			if (rhoL < radiusOuter && rhoL > radius){
				float streamwiseProfile = FastMath.abs(distAlongDir) < radius ?
						0.5f * (FastMath.cos(FastMath.PI * distAlongDir / radius) + 1f) 
						: 0f;
				float radialProfile = FastMath.sin(FastMath.PI * (rhoL - radius) / thickness);
				float vortPhi = streamwiseProfile * radialProfile * FastMath.PI / thickness;
				
				rhoHat.set(rho);
				rhoHat.normalizeLocal();
				
				direction.cross(rhoHat, phiHat);
				phiHat.multLocal(vortPhi * strength);
				
				System.out.println(phiHat);
				
				v.setVort(phiHat);
			}
			v.setPosition(v.getPosition()); //copy to front buffer
		}
	}
	
	
//	public void stepSimulation(float dt){
//		addSimTime(dt);
//		if (timeAccumulator >= DT){
//			synchronized (updateThreadMonitor) {
//				updateThreadMonitor.notifyAll();
//			}
//		}
//	}
	
	/**
	 * Step the simulation forward by dt.
	 * */
	public void stepSimulation(float dt){
		timeAccumulator += dt;
		if (timeAccumulator > DT){
			swapBuffers();
			buildVortonTree();
			
			stretchAndTilt();
			diffuseVorticity();
			advectVortons();
			timeAccumulator -= DT;
		}
	}
	
	protected synchronized void addSimTime(float time){
		timeAccumulator += time;
	}
	
	protected synchronized void subtractSimTime(float time){
		timeAccumulator -= time;
	}
	
	protected void simulateStep(){
		while (timeAccumulator >= DT){
			swapBuffers();
			buildVortonTree();

			stretchAndTilt();
			diffuseVorticity();
			advectVortons();
			subtractSimTime(DT);
		}
	}
	
	public void advectTracers(List<Vector3f> tracerPositions, float tpf){
		if (vortonTree == null){
			buildVortonTree();
		}
		this.currentTPF = tpf;
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
		temp2.set(position).subtractLocal(v.getPosition());
		float dist2 = temp2.lengthSquared() + AVOID_SINGULARITY;
		float oneOverDist = 1f / temp2.length();
		float distLaw;
		if (dist2 < VORTON_RADIUS_SQ){
			distLaw = oneOverDist / VORTON_RADIUS_SQ;
		}
		else {
			 distLaw = oneOverDist / dist2;
		}
		
		temp1.set(v.getVort()).multLocal(FOUR_THIRDS_PI * VORTON_RADIUS_CUBE).crossLocal(temp2).multLocal(distLaw);
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
		
		vars.vec[1].subtractLocal(vars.vec[0]).divideLocal(JACOBIAN_D); // d/dx
		vars.vec[3].subtractLocal(vars.vec[2]).divideLocal(JACOBIAN_D); // d/dy
		vars.vec[5].subtractLocal(vars.vec[4]).divideLocal(JACOBIAN_D); // d/dz
		
		vars.mat.setColumn(0, vars.vec[1]);
		vars.mat.setColumn(1, vars.vec[3]);
		vars.mat.setColumn(2, vars.vec[5]);
	}
	
	protected void advectVorton(Vorton v, List<Vorton> influences, ThreadVars vars){
		computeVelocityFromVortons(v.getPosition(), influences, vars.temp0, vars.temp1, vars.temp2);
		
		vars.temp0.multLocal(DT);
		
		v.getPosition(vars.temp1);
		vars.temp1.addLocal(vars.temp0);
		v.setPosition(vars.temp1);
	}
	
	protected void advectTracer(Vector3f tracer, List<Vorton> influences, ThreadVars vars){
		computeVelocityFromVortons(tracer, influences, vars.temp0, vars.temp1, vars.temp2);
		float tpf = currentTPF;
		while (tpf > DT){
			tracer.addLocal(vars.temp0.multLocal(DT));
			tpf -= DT;
		}
	}
	
	public void traceVortons(List<Vector3f> tracers){
		Iterator<Vector3f> tIt = tracers.iterator();
		Iterator<Vorton> vIt = vortons.iterator();
		
		while (tIt.hasNext() && vIt.hasNext()){
			Vector3f trace = tIt.next();
			Vorton v = vIt.next();
			v.getPosition(trace);
		}
	}
	
	protected void diffuseGroupOfVortons(List<Vorton> vortons, ThreadVars vars){
		for (Vorton v : vortons){
			vars.temp1.zero();
			for (Vorton w : vortons){
				if (w == v){
					continue;
				}
				
				vars.temp0.set(w.getVort()).subtractLocal(v.getVort()).multLocal(viscosity);
				vars.temp1.addLocal(vars.temp0);
			}
			v.getVort(vars.temp2);
			vars.temp2.addLocal(vars.temp1.multLocal(DT));
			vars.temp2.multLocal(1f - (viscosity * DT * DT));
			
			v.setVort(vars.temp2);
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
				vortonTree.getRoot().getInfluentialVortons(vorton.getPosition(), localVortons);

				getJacobian(localVortons, vorton.getPosition(), vars);
				
				vars.temp0.set(vorton.getVort()); //get vorticity
				vars.mat.multLocal(vars.temp0); //stretch/tilt
				vars.temp0.multLocal(DT); //time
				vorton.getVort(vars.temp1); //old vorticity
				vars.temp1.addLocal(vars.temp0); //add new vorticity
				vorton.setVort(vars.temp1); //update
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
				vortonTree.getRoot().getInfluentialVortons(vorton.getPosition(), localVortons);
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
	
	public class BufferedVorton extends Vorton {
		protected final int index;
		
		public BufferedVorton(int index){
			this.index = index;
		}
		
		public void initializeAll(Vector3f position, Vector3f vort){
			backPos.get()[index].set(position);
			frontPos.get()[index].set(position);
			
			backVort.get()[index].set(vort);
			frontVort.get()[index].set(vort);
		}
		
		public Vector3f getPosition(){
			return backPos.get()[index];
		}
		
		public void getPosition(Vector3f store){
			store.set(backPos.get()[index]);
		}
		
		public void setPosition(Vector3f value){
			frontPos.get()[index].set(value);
		}
		
		public void getVort(Vector3f store){
			store.set(backVort.get()[index]);
		}
		
		public Vector3f getVort(){
			return backVort.get()[index];
		}
		
		public void setVort(Vector3f value){
			frontVort.get()[index].set(value);
		}
		
		public boolean equals(Object o){
			if (!(o instanceof BufferedVorton)){
				return false;
			}
			
			BufferedVorton v = (BufferedVorton) o;
			return this.index == v.index;
		}
	}
}
