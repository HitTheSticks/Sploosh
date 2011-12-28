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
		VortonSpace fluid = new VortonSpace(21952, 0.2f, 5);
		fluid.distributeVortons(new Vector3f(-1f, -0.2f, -1f), new Vector3f(1f, 1f, 1f));
		fluid.injectJetRing(0.1f, 1f, 10f, Vector3f.UNIT_Y, Vector3f.ZERO);
		//fluid.injectVortexRing(0.1f, 1f, 10f, Vector3f.UNIT_Y, Vector3f.ZERO);
		fluid.initializeThreads(4);
		
		FluidView fv = new FluidView(20000, fluid);
		fv.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		fv.distributeTracers(Vector3f.ZERO, 0.5f);
		Material mat = assetManager.loadMaterial("Common/Materials/RedColor.j3m");
		mat.getAdditionalRenderState().setPointSprite(true);
		fv.setMaterial(mat);
		
		FluidVortonView fvv = new FluidVortonView(21952, fluid);
		fvv.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		mat = mat.clone();
		mat.setColor("Color", ColorRGBA.Cyan);
		mat.getAdditionalRenderState().setPointSprite(true);
		fvv.setMaterial(mat);
		
		rootNode.attachChild(fv);
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
