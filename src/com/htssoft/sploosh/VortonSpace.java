package com.htssoft.sploosh;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.htssoft.sploosh.presentation.FluidTracer;
import com.htssoft.sploosh.space.OTree;
import com.htssoft.sploosh.space.OTreeNode;
import com.htssoft.sploosh.threading.Kernel;
import com.htssoft.sploosh.threading.StaticThreadGroup;
import com.htssoft.sploosh.threading.WorkRange;
import com.jme3.math.FastMath;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public class VortonSpace implements TracerAdvecter {
	
	protected static final StaticThreadGroup<WorkRange> tracerThreads = 
		new StaticThreadGroup<WorkRange>("TracerThreads", TracerKernel.class);
	
	protected static final StaticThreadGroup<WorkRange> advectThreads = 
		new StaticThreadGroup<WorkRange>("AdvectThreads", AdvectKernel.class);
	
	protected static final StaticThreadGroup<DiffuseWorkItem> diffuseThreads = 
		new StaticThreadGroup<DiffuseWorkItem>("DiffuseThreads", DiffuseKernel.class);
	
	public static final float VORTON_RADIUS = 0.1f;
	public static final float VORTON_RADIUS_SQ = VORTON_RADIUS * VORTON_RADIUS;
	public static final float VORTON_RADIUS_CUBE = VORTON_RADIUS * VORTON_RADIUS * VORTON_RADIUS;
	public static final float AVOID_SINGULARITY = 0.00001f;
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
	
	
	protected Vorton[] vortons;
	
	protected AtomicReference<Vector3f[]> frontPos = new AtomicReference<Vector3f[]>();
	protected AtomicReference<Vector3f[]> backPos = new AtomicReference<Vector3f[]>();
	protected AtomicReference<Vector3f[]> frontVort = new AtomicReference<Vector3f[]>();
	protected AtomicReference<Vector3f[]> backVort = new AtomicReference<Vector3f[]>();
	
	protected OTree vortonTree;
	protected int gridResolution;
	
	protected float timeAccumulator = 0f;
	protected float viscosity = 0.5f;
	protected float currentTPF = 0f;
	protected boolean debugPrintln = false;
	protected Transform inputTransform = Transform.IDENTITY.clone();
	protected boolean hasDriver = false;
	
	
	/**
	 * Create a new vorton simulation with the given
	 * number of vortons.
	 * 
	 * @param nVortons the number of vortons to simulate. For best (symmetrical) results, this
	 * number should be a perfect cube.
	 * 
	 * @param viscosity this is a vague approximation of kinematic viscosity. Water is 1f.
	 * 
	 * @param gridResolution how many levels of recursion to descend when building the octree. 4-6 are good numbers.
	 * */
	public VortonSpace(int nVortons, float viscosity, int gridResolution){
		this.viscosity = viscosity;
		this.gridResolution = gridResolution;
		vortons = new Vorton[nVortons];
		for (int i = 0; i < nVortons; i++){
			vortons[i] = new BufferedVorton(i);
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
	
	public void setHasDriver(boolean hasDriver){
		this.hasDriver = hasDriver;
	}
	
	public boolean hasDriver(){
		return hasDriver;
	}
	
	/**
	 * Set the input transform. This will be used to transform
	 * all vector quantity inputs.
	 * */
	public void setInputTransform(Transform xform){
		this.inputTransform.set(xform);
	}
	
	/**
	 * Get the number of vortons in the fluid simulation.
	 * */
	public int getNVortons(){
		return vortons.length;
	}
	
	/**
	 * Swap back and front position/vorticity buffers.
	 * */
	protected void swapBuffers(){
		Vector3f[] t = frontPos.get();
		frontPos.set(backPos.get());
		backPos.set(t);
		
		t = frontVort.get();
		frontVort.set(backVort.get());
		backVort.set(t);
	}
	
	/**
	 * Deprecated. The number of threads launched is now
	 * automatically determined by the number of cores available.
	 * */
	@Deprecated
	public void initializeThreads(int notUsed){
		initializeThreads();
	}
	
	/**
	 * Initialize worker threads. This *must* be done before
	 * the simulation is started.
	 * 
	 * The number of threads started will be equal to the 
	 * number of cores returned by ThreadingUtils.
	 * */
	@Deprecated
	public void initializeThreads(){

	}
	
	/**
	 * Interrupt all worker threads. After this is called,
	 * another call to {@link initializeThreads} is required
	 * for the simulation to run.
	 * */
	@Deprecated
	public void stopThreads(){
	}
	
	public void randomizeVortons(){
		randomizeVortons(1);
	}
	
	/**
	 * Randomize all vortons' positions and vorticities.
	 * 
	 * Honestly, this is useless unless you just want chaos.
	 * */
	public void randomizeVortons(float amp){
		
		Vector3f tVort = new Vector3f();
		for (Vorton vI : vortons){
			BufferedVorton v = (BufferedVorton) vI;
			
			tVort.set(FastMath.nextRandomFloat() * 0.5f, FastMath.nextRandomFloat() * 0.5f, FastMath.nextRandomFloat() * 0.5f);
			tVort.multLocal(amp);
			v.accumulateVorticity(tVort);
		}
	}
	
	public void distributeVortons(Vector3f min, Vector3f max){
		distributeVortons(min, max, 0f);
	}
	
	/**
	 * Distribute vortons evenly over a grid.
	 * 
	 * @param min the lower bound of the grid's bounding box.
	 * @param max the upper bound of the grid's bounding box.
	 * */
	public void distributeVortons(Vector3f min, Vector3f max, float jitter){

		float particlesPerSide = (float) Math.cbrt(vortons.length);
		int nParticles = (int) particlesPerSide;
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
					if (index >= vortons.length){
						break outer;
					}
					BufferedVorton bv = (BufferedVorton) vortons[index];
					temp.set(jitter(x, jitter), jitter(y, jitter), jitter(z, jitter));
					inputTransform.transformVector(temp, temp);
					bv.initializeAll(temp, Vector3f.ZERO);
					++index;
				}
			}
		}
		swapBuffers(); //swap new values to back buffer for first run
	}
	
	protected float jitter(float coord, float jitter){
		if (jitter <= 0f){
			return coord;
		}
		
		return coord + (FastMath.nextRandomFloat() * jitter * (FastMath.nextRandomFloat() < 0.5f ? -1 : 1));
	}
	
	/**
	 * Inject a not-very-good vortex ring.
	 * 
	 * @param radius the interior radius of the ring.
	 * @param thickness the width of the ring
	 * @param strength by default, this method generates vorticities from (0,1). These vorticities are multiplied by strength. Think of
	 * it as an amplitude parameter.
	 * @param directionIn the direction the vortex ring should travel. Transformed by inputTransform.
	 * @param centerIn the center of the vortex ring. Transformed by inputTransform.
	 * */
	public void injectVortexRing(float radius, float thickness, float strength, Vector3f directionIn, Vector3f centerIn){
		Vector3f direction = new Vector3f(directionIn);
		Vector3f center = new Vector3f(centerIn);
		inputTransform.getRotation().mult(direction, direction);
		inputTransform.transformVector(center, center);
		
		direction.normalizeLocal();
		
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
				v.accumulateVorticity(temp);
			}
			v.setPosition(v.getPosition()); //copy to front buffer
		}
	}
	
	/**
	 * Inject a pretty good vortex ring.
	 * 
	 * @param radius the interior radius of the ring.
	 * @param thickness the width of the ring
	 * @param height the height of the vortex ring.
	 * @param strength by default, this method generates vorticities from (0,1). These vorticities are multiplied by strength. Think of
	 * it as an amplitude parameter.
	 * @param directionIn the direction the vortex ring should travel. Transformed by inputTransform.
	 * @param centerIn the center of the vortex ring. Transformed by inputTransform.
	 * */
	public void injectJetRing(float radius, float thickness, float height, float strength, Vector3f directionIn, Vector3f centerIn){
		Vector3f direction = new Vector3f(directionIn);
		Vector3f center = new Vector3f(centerIn);
		
		inputTransform.getRotation().mult(direction, direction);
		inputTransform.transformVector(center, center);
		
		direction.normalizeLocal();
		
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
				float streamwiseProfile = FastMath.abs(distAlongDir) < height ?
											0.5f * (FastMath.cos(FastMath.PI * distAlongDir / radius) + 1f) 
											: 0f;
				
				float radialProfile = FastMath.sin(FastMath.PI * (rhoL - radius) / thickness);
				float vortPhi = streamwiseProfile * radialProfile * FastMath.PI / thickness;
				
				rhoHat.set(rho);
				rhoHat.normalizeLocal();
				
				direction.cross(rhoHat, phiHat);
				phiHat.multLocal(vortPhi * strength);
				
				v.accumulateVorticity(phiHat);
			}
			v.setPosition(v.getPosition()); //copy to front buffer
		}
	}
	
	/**
	 * Set a uniform vorticity over the whole field.
	 * */
	public void uniformVorticity(Vector3f vort){
		for (Vorton vI : vortons){
			BufferedVorton v = (BufferedVorton) vI;
			v.accumulateVorticity(vort);
			v.setPosition(v.getPosition());
		}
	}
	
	/**
	 * This is the one you want.
	 * */
	public void injectRadial(float strength, float scale, Vector3f center){
		float x, y;
		Vector3f vort = new Vector3f();
		Vector3f pos = new Vector3f();
		
		for (Vorton vI : vortons){
			BufferedVorton v = (BufferedVorton) vI;
			
			pos.set(v.getPosition());
			inputTransform.transformInverseVector(pos, pos);
			
			x = pos.x;
			y = pos.y;
			x *= scale;
			y *= scale;
			float norm = norm2(x, y);
			if (norm >= 0.001f){
				vort.x = vort.y = 0f;
				vort.z = radialCurlZ(x, y) * strength;
				
				inputTransform.getRotation().multLocal(vort);
				
				v.accumulateVorticity(vort);
			}
			v.setPosition(v.getPosition());
		}
	}
	
	private float radialCurlZ(float x, float y){
		float normQd = norm2Sq(x, y);
		normQd *= 2;
		return -(2 * x * FastMath.sign(y) / normQd) + (2 * y * FastMath.sign(x) / normQd);    
	}
	
	private float norm2Sq(float x, float y){
		return (x * x) + (y * y);
	}
	
	private float norm2(float x, float y){
		return FastMath.sqrt((x * x) + (y * y));
	}
	
	public void injectRadial(float strength, Vector3f center){
		Vector3f fromCenter = new Vector3f();
		Vector3f vort = new Vector3f();
		
		for (Vorton vI : vortons){
			BufferedVorton v = (BufferedVorton) vI;
			
			fromCenter.set(v.getPosition()).subtractLocal(center);
			
			if (fromCenter.length() <= 0.001f){
				continue;
			}
			
			vort.set(0, 0, 0);
			vort.z = injectRadialDiff(fromCenter.x, fromCenter.x, fromCenter.y) - 
					 injectRadialDiff(fromCenter.y, fromCenter.x, fromCenter.y);
			vort.multLocal(strength);
			
			v.accumulateVorticity(vort);
			v.setPosition(v.getPosition());
		}
	}
	
	private float injectRadialDiff(float q, float x, float y){
		return (q * q) / FastMath.pow((x * x + y * y), 1.5f);
	}
	
	/**
	 * Injects horizontal radial.
	 * */
	public void injectRadial(float height, float radius, float strength, float zFudge, int samples, Vector3f center){
		float degreesPerSample = 360f / (float) samples;
		float degAccum = 0f;
		
		Vector3f dir = new Vector3f();
		Vector3f pos = new Vector3f();
		for (int i = 0; i < samples; i++){
			pos.set(radius * FastMath.cos(FastMath.DEG_TO_RAD * degAccum), 
					radius * FastMath.sin(FastMath.DEG_TO_RAD * degAccum), 
					0);
			dir.set(pos).normalizeLocal();
			pos.addLocal(center);
			
			dir.z += zFudge;
			injectJetRing(0.01f, height, 0.2f, strength, dir, pos);
			
			degAccum += degreesPerSample;
		}
	}
	
	/**
	 * Step the simulation forward by dt.
	 * @param dt how much time to add to the simulation.
	 * */
	public void stepSimulation(float dt){
		timeAccumulator += dt; //let's be honest, this lags.
		if (timeAccumulator > DT){ //if you put a "while" instead of an "if", simulation is better, but performance is unacceptable.
			swapBuffers();
			buildVortonTree();
			
			//stretchAndTilt();
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
	
	/**
	 * Update the positions of the given list of tracer positions.
	 * @param tracerPositions A list of tracer positions to sample. These Vector3fs themselves are updated
	 * with the new positions.
	 * @param tpf how much time to simulate for particle advection. In reality, particles are advanced by min(tpf, DT).
	 * */
	public void advectTracers(FluidTracer[] tracers, float tpf){
		if (vortonTree == null){
			buildVortonTree();
		}
		this.currentTPF = tpf;
		
		List<WorkRange> ranges = WorkRange.divideWork(tracers.length, tracers, this, tracerThreads.nThreads());
		
		long ms = System.currentTimeMillis();
		tracerThreads.submitWork(ranges, this);
		if (debugPrintln)
			System.out.println("Tracers took (ms): " + (System.currentTimeMillis() - ms));
	}

	/**
	 * Build the OTree of vortons.
	 * 
	 * This is public because you may want the tree for debugging purposes.
	 * */
	public void buildVortonTree(){
		Vector3f min = new Vector3f(Vector3f.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Vector3f.NEGATIVE_INFINITY);
		
		for (Vorton v : vortons){
			min.minLocal(v.getPosition());
			max.maxLocal(v.getPosition());
		}
		if (vortonTree == null){
			vortonTree = new OTree(min, max);
			vortonTree.splitTo(gridResolution);
		}
		else {
			vortonTree.rebuild(min, max, gridResolution);
		}
		long ms = System.currentTimeMillis();
		for (Vorton v : vortons){
			vortonTree.insert(v);
		}
		vortonTree.getRoot().updateDerivedQuantities();
		if (debugPrintln){
			System.out.println("Tree build took (ms) : " + (System.currentTimeMillis() - ms) + 
					" Bounds: " + vortonTree.getRoot().getMin() + ", " + vortonTree.getRoot().getMax());
		}
	}

	protected void stretchAndTilt(){
//		stretchWork.addAll(vortons);
//		outstandingWorkItems = new CountDownLatch()
//		long ms = System.currentTimeMillis();
//		synchronized (outstandingWorkItems){
//			try {
//				while (outstandingWorkItems.get() != 0){
//					outstandingWorkItems.wait();
//				}
//				if (debugPrintln)
//					System.out.println("Stretch and tilt took (ms): " + (System.currentTimeMillis() - ms));
//			} catch (InterruptedException ex) {
//				ex.printStackTrace();
//			}
//		}
	}
	
	
	protected void advectVortons(){
		List<WorkRange> ranges = WorkRange.divideWork(vortons.length, vortons, this, advectThreads.nThreads());
		
		long ms = System.currentTimeMillis();
		advectThreads.submitWork(ranges, this);
		if (debugPrintln)
			System.out.println("Advection took (ms): " + (System.currentTimeMillis() - ms));
		
	}
	
	protected void diffuseVorticity(){
		ArrayList<OTreeNode> groups = new ArrayList<OTreeNode>();
		vortonTree.getRoot().getLeaves(groups);
		
		ArrayList<DiffuseWorkItem> work = new ArrayList<DiffuseWorkItem>(groups.size());
		
		for (int i = 0; i < groups.size(); i++){
			OTreeNode node = groups.get(i);
			DiffuseWorkItem item = new DiffuseWorkItem(this);
			node.getItems(item.vortons);
			work.add(item);
		}
		
		long ms = System.currentTimeMillis();
			diffuseThreads.submitWork(work, this);
		if (debugPrintln)
			System.out.println("Diffusion took (ms): " + (System.currentTimeMillis() - ms));
		
	}
	
	
	/**
	 * Honestly, this is super slow and kinda broken.
	 * */
	protected void getJacobian(List<Vorton> influences, Vector3f position, ThreadVars vars){
		for (int i = 0; i < jacobianOffsets.length; i++){
			position.add(jacobianOffsets[i], vars.vec[i]);
		}
		
		for (int i = 0; i < jacobianOffsets.length; i++){
			TracerMath.computeVelocityFromVortons(vars.vec[i], influences, vars.temp2, vars.temp0, vars.temp1);
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
		TracerMath.computeVelocityFromVortons(v.getPosition(), influences, vars.temp0, vars.temp1, vars.temp2);
		
		vars.temp0.multLocal(DT);
		
		v.getPosition(vars.temp1);
		vars.temp1.addLocal(vars.temp0);
		v.setPosition(vars.temp1);
	}
	
	/**
	 * Updates the list of vectors with the positions of all vortons in the system.
	 * This is primarily for debugging purposes, although the motion of the vortons
	 * themselves is perhaps attractive enough for use.
	 * */
	public void traceVortons(List<Vector3f> tracers){
		Iterator<Vector3f> tIt = tracers.iterator();
		
		for (int i = 0; i < vortons.length && tIt.hasNext(); i++){
			Vector3f trace = tIt.next();
			vortons[i].getPosition(trace);
		}
	}
	
	protected void diffuseGroupOfVortons(List<Vorton> vortons, ThreadVars vars){
		for (int i = 0; i < vortons.size(); i++){
			Vorton v = vortons.get(i);
			vars.temp1.zero();
			for (int j = 0; j < vortons.size(); j++){
				if (i == j){
					continue;
				}
				Vorton w = vortons.get(i);
				
				vars.temp0.set(w.getVort()).subtractLocal(v.getVort()).multLocal(viscosity);
				vars.temp1.addLocal(vars.temp0);
			}
			v.getVort(vars.temp2);
			vars.temp2.addLocal(vars.temp1.multLocal(DT));
			vars.temp2.multLocal(1f - (viscosity * DT));
			
			v.setVort(vars.temp2);
		}
	}
	

	@Override
	public void updateTransform(Transform trans) {
		//nop
	}
	
	/**
	 * Get the most recent vorton octree built.
	 * 
	 * @return the most recent vorton octree built, or null if no tree has yet been built.
	 * */
	public OTree getLastTreeForDebug(){
		return vortonTree;
	}

	/**
	 * Work item for diffusion.
	 * */
	protected class DiffuseWorkItem {
		public final VortonSpace owner;
		public ArrayList<Vorton> vortons = new ArrayList<Vorton>();
		public DiffuseWorkItem(VortonSpace owner){
			this.owner = owner;
		}
			
	}
	
//	/**
//	 * Thread responsible for stretching/tilting.
//	 * */
//	protected class StretchThread implements Runnable {
//		ThreadVars vars = new ThreadVars();
//		ArrayList<Vorton> localVortons = new ArrayList<Vorton>(1000);
//		
//		public void run(){
//			mainloop:
//			while (!Thread.interrupted()){
//				Vorton vorton;
//				try {
//					vorton = stretchWork.take();
//				} catch (InterruptedException ex) {
//					break mainloop;
//				}
//				
//				localVortons.clear();
//				vortonTree.getInfluentialVortons(vorton.getPosition(), VORTON_RADIUS, localVortons);
//
//				getJacobian(localVortons, vorton.getPosition(), vars);
//				
//				vars.temp0.set(vorton.getVort()); //get vorticity
//				vars.mat.multLocal(vars.temp0); //stretch/tilt
//				vars.temp0.multLocal(DT); //time
//				vorton.getVort(vars.temp1); //old vorticity
//				vars.temp1.addLocal(vars.temp0); //add new vorticity
//				vorton.setVort(vars.temp1); //update
//				outstandingWorkItems.countDown();
//			}
//		}
//	}
	
	/**
	 * Thread responsible for vorton advection.
	 * */
	protected static class AdvectKernel extends Kernel<WorkRange> {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>(512);
		
		public AdvectKernel(){}
		
		public void process(WorkRange range){
			VortonSpace vs = (VortonSpace) range.parent;
			Vorton[] vortons = (Vorton[]) range.workingSet;
			for (int i = range.first; i <= range.last; i++){
				Vorton vorton = vortons[i];
				if (!localVortons.isEmpty()){
					localVortons.clear();
				}
				vs.vortonTree.getInfluentialVortons(vorton.getPosition(), VORTON_RADIUS, localVortons);
				localVortons.remove(vorton);
				vs.advectVorton(vorton, localVortons, vars);
				localVortons.clear();
			}
		}
	}

	/**
	 * Thread responsible for vorticity diffusion.
	 * */
	protected static class DiffuseKernel extends Kernel<DiffuseWorkItem> {
		ThreadVars vars = new ThreadVars();

		public DiffuseKernel(){}
		
		public void process(DiffuseWorkItem item){
			item.owner.diffuseGroupOfVortons(item.vortons, vars);
		}
	}
	
	/**
	 * Thread responsible for update tracer locations.
	 * */
	protected static class TracerKernel extends Kernel<WorkRange> {
		ThreadVars vars = new ThreadVars();
		ArrayList<Vorton> localVortons = new ArrayList<Vorton>(512);
		
		public TracerKernel(){}
		
		public void process(WorkRange range){
			VortonSpace vs = (VortonSpace) range.parent;
			FluidTracer[] workingSet = (FluidTracer[]) range.workingSet;
			for (int i = range.first; i <= range.last; i++){
				FluidTracer tracer = workingSet[i];
				if (!localVortons.isEmpty()){
					localVortons.clear();
				}
				vs.vortonTree.getInfluentialVortons(tracer.position, tracer.radius, localVortons);
				TracerMath.advectTracer(tracer, localVortons, vars, vs.currentTPF);
				localVortons.clear();
			}
		}
	}
	
	/**
	 * A vorton that links back to VortonSpace front/back buffers.
	 * */
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
		
		public void accumulateVorticity(Vector3f vortContrib){
			frontVort.get()[index].addLocal(vortContrib);
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
