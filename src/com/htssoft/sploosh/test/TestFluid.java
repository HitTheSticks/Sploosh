package com.htssoft.sploosh.test;

import com.esotericsoftware.kryo.Kryo;
import com.htssoft.sploosh.OTreeStorage;
import com.htssoft.sploosh.SimpleVorton;
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
	public static final String TOGGLE_TREE = "TESTFLUID_toggle_tree";
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
				
		fluid = new VortonSpace(1000, 0.2f, 4);
		fluid.distributeVortons(new Vector3f(-0.75f, -0.5f, -0.75f), new Vector3f(0.75f, 4f, 0.75f));
		fluid.injectJetRing(0.1f, 1f, 1f, 15f, Vector3f.UNIT_Y, Vector3f.ZERO);
		//fluid.injectVortexRing(0.1f, 1f, 10f, Vector3f.UNIT_Y, Vector3f.ZERO);
		fluid.initializeThreads(4);
		
		fv = new FluidView(6000, fluid);
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
		
		inputManager.addMapping(TOGGLE_VORTON, new KeyTrigger(KeyInput.KEY_Y));
		inputManager.addMapping(PAUSE_SIM, new KeyTrigger(KeyInput.KEY_U));
		inputManager.addMapping(DUMP_TREE, new KeyTrigger(KeyInput.KEY_T));
		inputManager.addListener(new DebugActionListener(), TOGGLE_TREE, PAUSE_SIM, TOGGLE_VORTON);
	}
	
	protected void dumpTree(){
		OTree copy = fluid.getLastTreeForDebug().deepCopy();
		OTreeStorage.dump(copy, "./dump.otree");
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
