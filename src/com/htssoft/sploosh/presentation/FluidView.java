package com.htssoft.sploosh.presentation;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

import com.htssoft.sploosh.TracerAdvecter;
import com.htssoft.sploosh.affectors.NullInit;
import com.htssoft.sploosh.threading.Kernel;
import com.htssoft.sploosh.threading.StaticThreadGroup;
import com.htssoft.sploosh.threading.WorkRange;
import com.jme3.effect.shapes.EmitterShape;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.math.FastMath;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;

/**
 * This is a Geometry designed to show a view of a VortonSpace fluid simulation.
 * 
 * By default, it will also manage stepping the simulation forward.
 * */
public class FluidView extends Geometry {
	protected static final StaticThreadGroup<WorkRange> affectorThreads = 
		new StaticThreadGroup<WorkRange>("AffectorThreads", AffectorKernel.class);
	protected static final NullInit DEFAULT_INIT = new NullInit();
	
	protected FluidTracerMesh tracerMesh;
	protected TracerAdvecter fluid;
	protected int nTracers;
	protected boolean enableSim = true;
	protected float reynoldsRatio = 1f;
	protected FluidTracerAffector affector;
	protected FluidTracerInitializer init = DEFAULT_INIT;
	protected EmitterShape emitterShape;
	protected boolean burstMode = true;
	protected BitSet freeIndexes;
	protected float streamPerSec = 0f;
	protected float streamAccum = 0f;
	protected float particleLife = 0f;
	protected float particleRadius = 0.1f;
	protected float particleDrag = 0.8f;
	protected float currentTPF;
	
	public FluidView(int nTracers, TracerAdvecter fluid){
		this.fluid = fluid;
		this.nTracers = nTracers;
		init();
	}
	
	protected void init(){
		this.setShadowMode(ShadowMode.Off);
		this.setQueueBucket(Bucket.Transparent);
		this.setIgnoreTransform(true);
		
		this.setMesh(tracerMesh = new FluidTracerMesh(nTracers));
		this.addControl(new FluidControl());
		this.setCullHint(CullHint.Never);
		
		freeIndexes = new BitSet(this.nTracers);
		freeIndexes.set(0, nTracers);
		
		if (fluid.hasDriver()){
			enableSim = false;
			System.out.println("Not driving.");
		}
		else {
			fluid.setHasDriver(true);
			System.out.println("Driving.");
		}
		
	}
	
	public void setReynoldsRatio(float r){
		reynoldsRatio = FastMath.clamp(r, 0f, 1f);
	}
	
	public void enableSim(boolean enable){
		this.enableSim = enable;
	}
	
	public void toggleSim(){
		this.enableSim = !this.enableSim;
	}
	
	
	public void setTracerRadius(float r){
		particleRadius = r;
	}
	
	public void setTracerDrag(float drag){
		particleDrag = drag;
	}
	
	public void setStreamPerSec(float perSec){
		if (perSec > 0f){
			burstMode = false;
			streamPerSec = perSec;
		}
		else {
			burstMode = true;
		}
	}
		
	public void distributeTracers(Vector3f center, float radius, float lifetime){
		Transform trans = this.getWorldTransform();
		
		List<FluidTracer> tracers = tracerMesh.getBuffer();
		for (FluidTracer t : tracers){
			t.lifetime = lifetime;
			t.position.set(randomComponent(), randomComponent(), randomComponent());
			t.position.normalizeLocal();
			t.position.multLocal(radius * FastMath.nextRandomFloat());
			t.position.addLocal(center);
			t.radius = particleRadius;
			t.reynoldsRatio = reynoldsRatio;
			trans.transformVector(t.position, t.position);
		}
	}
	
	/**
	 * Set the emitter shape.
	 * */
	public void setShape(EmitterShape shape){
		this.emitterShape = shape;
	}
	
	/**
	 * Sets the tracer affector.
	 * */
	public void setAffector(FluidTracerAffector affector){
		this.affector = affector;
	}
	
	public void setInit(FluidTracerInitializer init){
		this.init = init;
	}
	
	public void setParticleLife(float lifetime){
		particleLife = lifetime;
	}
	
	public void initializeTracers(float lifetime){
		setParticleLife(lifetime);
		initializeTracers();
	}
	
	/**
	 * Initialize tracers from the current shape and affector.
	 * */
	public void initializeTracers(){
		if (init == null || emitterShape == null){
			throw new IllegalStateException("To initialize tracers this way you must have both a shape and an init set.");
		}
		Transform trans = this.getWorldTransform();
		
		
		FluidTracer[] tracers = tracerMesh.getBufferArray();
		for (int i = 0; i < tracers.length; i++){
			initTracer(tracers, i, trans);
		}
	}
	
	protected void initTracer(FluidTracer[] tracers, int index, Transform trans){
		freeIndexes.clear(index);
		FluidTracer t = tracers[index];
		t.age = 0f;
		t.lifetime = particleLife;
		t.reynoldsRatio = reynoldsRatio;
		t.radius = particleRadius;
		t.drag = particleDrag;
		init.initTracer(t, emitterShape, trans);
	}
	
	protected float randomComponent(){
		return FastMath.nextRandomFloat() * (FastMath.nextRandomFloat() < 0.5f ? -1 : 1);
	}
	
	
	protected void updateStream(float tpf){
		streamAccum += streamPerSec * tpf;
		
		Transform trans = getWorldTransform();
		FluidTracer[] tracers = tracerMesh.getBufferArray();
	
		int freeIndex = 0;
		for (freeIndex = freeIndexes.nextSetBit(freeIndex); freeIndex >= 0 && streamAccum >= 1f; freeIndex = freeIndexes.nextSetBit(freeIndex), streamAccum -= 1f){
			initTracer(tracers, freeIndex, trans);			
		}
	}
	
	protected void affectParticles(FluidTracer[] buffer){
		
		if (affector != null){		
			List<WorkRange> ranges = WorkRange.divideWork(buffer.length, buffer, this, affectorThreads.nThreads());

			affectorThreads.submitWork(ranges, this);
		}
		
		for (int i = 0; i < buffer.length; i++){
			if (buffer[i].age >= buffer[i].lifetime){
				freeIndexes.set(i);
			}
		}
	}
	
	public void updateFromControl(float tpf){
		currentTPF = tpf;
		fluid.updateTransform(getWorldTransform());
		
		if (!burstMode){
			updateStream(tpf);
		}
				
		if (enableSim){
			fluid.stepSimulation(tpf);
		}
		
		FluidTracer[] buffer = tracerMesh.getBufferArray();
		
		affectParticles(buffer);
		
		fluid.advectTracers(buffer, tpf);
		tracerMesh.updateBuffers();
		this.setBoundRefresh();
		
	}
	
	public void renderFromControl(){
	}
	
	protected static class AffectorKernel extends Kernel<WorkRange> {
		public AffectorKernel(){
		}
		
		@Override
		public void process(WorkRange workRange) {
			FluidView view = (FluidView) workRange.parent;
			FluidTracer[] buffer = (FluidTracer[]) workRange.workingSet;
			for (int i = workRange.first; i <= workRange.last; i++){
				if (buffer[i].age > buffer[i].lifetime){
					continue;
				}
				view.affector.affectTracer(buffer[i], view.currentTPF);
			}
		}

	}

	
	private class FluidControl implements Control {

		@Override
		public void write(JmeExporter ex) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void read(JmeImporter im) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Control cloneForSpatial(Spatial spatial) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSpatial(Spatial spatial) {
		}

		@Override
		public void setEnabled(boolean enabled) {
		}

		@Override
		public boolean isEnabled() {
			return true;
		}

		@Override
		public void update(float tpf) {
			updateFromControl(tpf);
		}

		@Override
		public void render(RenderManager rm, ViewPort vp) {
			renderFromControl();
		}
		
	}
}
