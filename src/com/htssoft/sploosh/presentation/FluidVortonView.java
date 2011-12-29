package com.htssoft.sploosh.presentation;

import java.io.IOException;

import com.htssoft.sploosh.VortonSpace;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;

public class FluidVortonView extends Geometry {
	protected FluidTracerMesh tracerMesh;
	protected VortonSpace fluid;
	protected int nTracers;
	
	protected boolean isDriver = false;
	protected OTreeDebug treeDebug;
	protected boolean enableTreeDebug;
	
	public FluidVortonView(int nTracers, VortonSpace fluid){
		this.fluid = fluid;
		this.nTracers = nTracers;
		this.setShadowMode(ShadowMode.Off);
		this.setQueueBucket(Bucket.Transparent);
		//this.setIgnoreTransform(true);
		
		this.setMesh(tracerMesh = new FluidTracerMesh(nTracers));
		this.addControl(new FluidControl());
		this.setCullHint(CullHint.Never);
	}
	
	public void debugTree(Material mat){
		treeDebug = new OTreeDebug(mat);
		if (this.getParent() != null){
			this.getParent().attachChild(treeDebug.getNode());	
		}
	}
	
	public void toggleTree(){
		enableTreeDebug = !enableTreeDebug;
	}
	
	public void setShowTree(boolean tree){
		enableTreeDebug = tree;
	}
	
	public void setDriver(boolean isDriving){
		isDriver = isDriving;
	}
	
	public void setScale(Vector3f scale){
		tracerMesh.setScale(scale);
	}

	public void updateFromControl(float tpf){
		if (isDriver){
			fluid.stepSimulation(tpf);
		}
		if (treeDebug != null && enableTreeDebug){
			if (fluid.getLastTreeForDebug() == null){
				fluid.buildVortonTree();
			}
			treeDebug.updateTree(fluid.getLastTreeForDebug(), tracerMesh.getScale());
		}
		fluid.traceVortons(tracerMesh.getBuffer());
		tracerMesh.updateBuffers();
		this.setBoundRefresh();
	}
	
	public void renderFromControl(){
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
