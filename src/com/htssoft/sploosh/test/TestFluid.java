package com.htssoft.sploosh.test;

import com.htssoft.sploosh.VortonSpace;
import com.htssoft.sploosh.presentation.FluidView;
import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;

public class TestFluid extends SimpleApplication {

	@Override
	public void simpleInitApp() {
		mouseInput.setCursorVisible(true);
		VortonSpace fluid = new VortonSpace(6000, 0.1f, 5);
		fluid.randomizeVortons();
		fluid.initializeThreads(4);
		FluidView fv = new FluidView(1000, fluid);
		fv.distributeTracers(Vector3f.ZERO, 0.1f);
		Material mat = assetManager.loadMaterial("Common/Materials/RedColor.j3m");
		mat.getAdditionalRenderState().setPointSprite(true);
		fv.setMaterial(mat);
		rootNode.attachChild(fv);
		flyCam.setEnabled(false);
	}

	
	public static void main(String[] args){
		TestFluid tf = new TestFluid();
		
		tf.start();
	}
}
