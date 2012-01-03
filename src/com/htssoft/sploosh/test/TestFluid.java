package com.htssoft.sploosh.test;

import com.htssoft.sploosh.VortonSpace;
import com.htssoft.sploosh.presentation.FluidView;
import com.htssoft.sploosh.presentation.FluidVortonView;
import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

public class TestFluid extends SimpleApplication {
	public static final String TOGGLE_TREE = "TESTFLUID_toggle_tree";
	public static final String PAUSE_SIM = "TESTFLUID_pause_sim";
	public static final String TOGGLE_VORTON = "TESTFLUID_toggle_vorton";
	
	FluidVortonView fvv;
	FluidView fv;
	
	VortonSpace fluid;
	
	Material treeDebugMaterial;
	
	@Override
	public void simpleInitApp() {
		//mouseInput.setCursorVisible(true);
		flyCam.setMoveSpeed(1f);
		flyCam.setDragToRotate(true);
				
		fluid = new VortonSpace(1000, 0.2f, 4);
		fluid.distributeVortons(new Vector3f(-0.75f, -0.5f, -0.75f), new Vector3f(0.75f, 4f, 0.75f));
		fluid.injectJetRing(0.1f, 1f, 1f, 15f, Vector3f.UNIT_Y, Vector3f.ZERO);
		//fluid.injectVortexRing(0.1f, 1f, 10f, Vector3f.UNIT_Y, Vector3f.ZERO);
		fluid.initializeThreads(4);
		
		fv = new FluidView(1000, fluid);
		fv.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		fv.distributeTracers(new Vector3f(0f, 0.5f, 0f), 1f, 1f);
		Material mat = assetManager.loadMaterial("Common/Materials/RedColor.j3m");
		mat.getAdditionalRenderState().setPointSprite(true);
		fv.setMaterial(mat);
		fv.enableSim(true);
		
		fvv = new FluidVortonView(fluid);
		fvv.setScale(new Vector3f(0.5f, 0.5f, 0.5f));
		mat = mat.clone();
		mat.setColor("Color", ColorRGBA.Cyan);
		mat.getAdditionalRenderState().setPointSprite(true);
		fvv.setMaterial(mat);
		
		rootNode.attachChild(fv);
		//rootNode.attachChild(fvv);
		
		treeDebugMaterial = mat.clone();
		treeDebugMaterial.setColor("Color", ColorRGBA.White);
		treeDebugMaterial.getAdditionalRenderState().setWireframe(true);
		treeDebugMaterial.getAdditionalRenderState().setDepthTest(false);
		treeDebugMaterial.setTransparent(true);
		
		if (!rootNode.hasChild(fv)){
			fvv.setDriver(true);
		}
		//flyCam.setEnabled(false);
		
		inputManager.addMapping(TOGGLE_TREE, new KeyTrigger(KeyInput.KEY_T));
		inputManager.addMapping(TOGGLE_VORTON, new KeyTrigger(KeyInput.KEY_Y));
		inputManager.addMapping(PAUSE_SIM, new KeyTrigger(KeyInput.KEY_U));
		inputManager.addListener(new DebugActionListener(), TOGGLE_TREE, PAUSE_SIM, TOGGLE_VORTON);
	}

	protected class DebugActionListener implements ActionListener {

		@Override
		public void onAction(String name, boolean isPressed, float tpf) {
			if (!isPressed){
				return;
			}
			if (name.equals(TOGGLE_TREE)){
				if (!rootNode.hasChild(fvv)){
					rootNode.attachChild(fvv);
					fvv.debugTree(treeDebugMaterial);
					fvv.setShowTree(true);
				}
				else {
					fvv.toggleTree();
				}
			}
			else if (name.equals(PAUSE_SIM)){
				fv.toggleSim();
			}
			else if (name.equals(TOGGLE_VORTON)){
				if (!rootNode.hasChild(fvv)){
					rootNode.attachChild(fvv);
				}
				else {
					rootNode.detachChild(fvv);
				}
			}
		}
		
	}
	
	public static void main(String[] args){
		TestFluid tf = new TestFluid();
		tf.setPauseOnLostFocus(false);
		tf.start();
	}
}
