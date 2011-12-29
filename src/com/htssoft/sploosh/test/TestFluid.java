package com.htssoft.sploosh.test;

import com.htssoft.sploosh.VortonSpace;
import com.htssoft.sploosh.presentation.FluidView;
import com.htssoft.sploosh.presentation.FluidVortonView;
import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

public class TestFluid extends SimpleApplication {

	@Override
	public void simpleInitApp() {
		//mouseInput.setCursorVisible(true);
		VortonSpace fluid = new VortonSpace(1000, 0.2f, 5);
		fluid.distributeVortons(new Vector3f(-1f, -0.2f, -1f), new Vector3f(1f, 1f, 1f));
		fluid.injectJetRing(0.1f, 1f, 100f, Vector3f.UNIT_Y, Vector3f.ZERO);
		//fluid.injectVortexRing(0.1f, 1f, 10f, Vector3f.UNIT_Y, Vector3f.ZERO);
		fluid.initializeThreads(4);
		
		FluidView fv = new FluidView(10000, fluid);
		fv.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		fv.distributeTracers(Vector3f.ZERO, 0.5f);
		Material mat = assetManager.loadMaterial("Common/Materials/RedColor.j3m");
		mat.getAdditionalRenderState().setPointSprite(true);
		fv.setMaterial(mat);
		
		FluidVortonView fvv = new FluidVortonView(6859, fluid);
		fvv.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		mat = mat.clone();
		mat.setColor("Color", ColorRGBA.Cyan);
		mat.getAdditionalRenderState().setPointSprite(true);
		fvv.setMaterial(mat);
		
		VortonSpace fluid2 = new VortonSpace(1000, 0.2f, 5);
		fluid2.distributeVortons(new Vector3f(-1f, -0.2f, -1f), new Vector3f(1f, 1f, 1f));
		fluid2.injectJetRing(0.1f, 1f, 100f, Vector3f.UNIT_Y, Vector3f.ZERO);
		fluid2.initializeThreads(4);
		
		FluidView fv2 = new FluidView(10000, fluid2);
		fv2.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		fv2.distributeTracers(Vector3f.ZERO, 0.5f);
		mat = mat.clone();
		mat.setColor("Color", ColorRGBA.Yellow);
		mat.getAdditionalRenderState().setPointSprite(true);
		fv2.setMaterial(mat);
		fv2.setLocalTranslation(-1f, 0, 0);
		
		rootNode.attachChild(fv);
		rootNode.attachChild(fv2);
		//rootNode.attachChild(fvv);
		
		if (!rootNode.hasChild(fv)){
			fvv.setDriver(true);
		}
		//flyCam.setEnabled(false);
		flyCam.setDragToRotate(true);
	}

	
	public static void main(String[] args){
		TestFluid tf = new TestFluid();
		tf.setPauseOnLostFocus(false);
		tf.start();
	}
}
