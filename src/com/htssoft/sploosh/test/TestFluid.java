package com.htssoft.sploosh.test;

import com.htssoft.sploosh.OTreeStorage;
import com.htssoft.sploosh.VortonSpace;
import com.htssoft.sploosh.presentation.FluidView;
import com.htssoft.sploosh.presentation.FluidVortonView;
import com.htssoft.sploosh.space.OTree;
import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

public class TestFluid extends SimpleApplication {
	public static final String PAUSE_SIM = "TESTFLUID_pause_sim";
	public static final String TOGGLE_VORTON = "TESTFLUID_toggle_vorton";
	public static final String DUMP_TREE = "TESTFLUID_dump_tree";
	
	FluidVortonView fvv;
	FluidView fv;
	
	VortonSpace fluid;
	
	Material treeDebugMaterial;
	
	@Override
	public void simpleInitApp() {
		//mouseInput.setCursorVisible(true);
		flyCam.setMoveSpeed(1f);
		flyCam.setDragToRotate(true);
				
		fluid = new VortonSpace(64, 0.01f, 4);
		fluid.distributeVortons(new Vector3f(-0.05f, -0.05f, 0f), new Vector3f(0.05f, 0.05f, 2f));
		fluid.randomizeVortons(50);
		//fluid.injectRadial(400, 4, Vector3f.ZERO);
		//fluid.injectJetRing(0.1f, 1f, 1f, 5f, Vector3f.UNIT_Z, Vector3f.ZERO);
		fluid.initializeThreads(4);
		
		fv = new FluidView(6000, fluid);
		fv.setReynoldsRatio(0.99f);
		fv.distributeTracers(new Vector3f(0, 0, 0.25f), 0.5f, 1f);
		Material mat = assetManager.loadMaterial("Common/Materials/RedColor.j3m");
		mat.getAdditionalRenderState().setPointSprite(true);
		fv.setMaterial(mat);
		fv.enableSim(true);
		
		fvv = new FluidVortonView(fluid);
		mat = mat.clone();
		mat.setColor("Color", ColorRGBA.Cyan);
		mat.getAdditionalRenderState().setPointSprite(true);
		fvv.setMaterial(mat);
		
		rootNode.attachChild(fv);
		rootNode.attachChild(fvv);
		
		treeDebugMaterial = mat.clone();
		treeDebugMaterial.setColor("Color", ColorRGBA.White);
		treeDebugMaterial.getAdditionalRenderState().setWireframe(true);
		treeDebugMaterial.getAdditionalRenderState().setDepthTest(false);
		treeDebugMaterial.setTransparent(true);
		
		if (!rootNode.hasChild(fv)){
			fvv.setDriver(true);
		}
		//flyCam.setEnabled(false);
		
		inputManager.addMapping(DUMP_TREE, new KeyTrigger(KeyInput.KEY_T));
		inputManager.addMapping(TOGGLE_VORTON, new KeyTrigger(KeyInput.KEY_Y));
		inputManager.addMapping(PAUSE_SIM, new KeyTrigger(KeyInput.KEY_U));
		inputManager.addListener(new DebugActionListener(), DUMP_TREE, PAUSE_SIM, TOGGLE_VORTON);
	}
	
	protected void dumpTree(){
		System.out.println("Dumping tree.");
		OTree copy = fluid.getLastTreeForDebug().deepCopy();
		OTreeStorage.dump(copy, "./dump.otree");
	}

	protected class DebugActionListener implements ActionListener {

		@Override
		public void onAction(String name, boolean isPressed, float tpf) {
			if (!isPressed){
				return;
			}
			if (name.equals(PAUSE_SIM)){
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
			else if (name.equals(DUMP_TREE)){
				dumpTree();
			}
		}
		
	}
	
	public static void main(String[] args){
		TestFluid tf = new TestFluid();
		tf.setPauseOnLostFocus(false);
		tf.start();
	}
}
