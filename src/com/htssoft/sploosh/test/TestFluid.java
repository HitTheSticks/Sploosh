package com.htssoft.sploosh.test;

import com.htssoft.rts.presentation.fx.support.HemisphereSurfaceShape;
import com.htssoft.sploosh.OTreeStorage;
import com.htssoft.sploosh.VortonSpace;
import com.htssoft.sploosh.affectors.BurstAffector;
import com.htssoft.sploosh.presentation.FluidView;
import com.htssoft.sploosh.presentation.FluidVortonView;
import com.htssoft.sploosh.space.OTree;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
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
	public static final String STEP_SIM = "TESTFLUID_step_sim";
	
	FluidVortonView fvv;
	FluidView fv;
	
	VortonSpace fluid;
	
	Material treeDebugMaterial;
	
	@Override
	public void simpleInitApp() {
		//getAssetManager().registerLocator("./assets", FileLocator.class);
		//mouseInput.setCursorVisible(true);
		flyCam.setMoveSpeed(1f);
		flyCam.setDragToRotate(true);
		
				
		fluid = new VortonSpace(1000, 0.01f, 4);
		fluid.distributeVortons(new Vector3f(-0.55f, -0.55f, 0.0f), new Vector3f(0.55f, 0.55f, 0.5f));
		//fluid.randomizeVortons(0.25f);
		//fluid.injectRadial(4, 4, Vector3f.ZERO);
		Vector3f c = new Vector3f(0f, 0f, 1f);
		fluid.injectJetRing(0.1f, 0.25f, 1f, 10f, Vector3f.UNIT_Z, c);
		
		//fluid.uniformVorticity(new Vector3f(0f, 0f, -12f));
		
		HemisphereSurfaceShape hss = new HemisphereSurfaceShape(0.15f);
		//UniformInit init = new UniformInit(Vector3f.UNIT_Z.multLocal(10f));
		BurstAffector init = new BurstAffector(4f);
		
		fv = new FluidView(6000, fluid);
		rootNode.attachChild(fv);
		fv.setShape(hss);
		fv.setInit(init);
		//fv.setAffector(new GravitationalAffector(Vector3f.ZERO, 2f));
		fv.setTracerDrag(0.001f);
		fv.setReynoldsRatio(0.8f);
		fv.setParticleLife(1.5f);
		fv.setStreamPerSec(1000f);
		//fv.distributeTracers(new Vector3f(0, 0, 0.0f), 0.05f, 10f);
		Material mat = assetManager.loadMaterial("fx/materials/instants/fire_fluid.j3m");
		mat.getAdditionalRenderState().setPointSprite(true);
		fv.setMaterial(mat);
		fv.enableSim(false);
		fluid.stepSimulation(VortonSpace.DT + 0.01f);
		
		fvv = new FluidVortonView(fluid);
		mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", ColorRGBA.Cyan);
		mat.getAdditionalRenderState().setPointSprite(true);
		mat.getAdditionalRenderState().setDepthTest(false);
		fvv.setMaterial(mat);
		
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
		
		inputManager.addMapping(DUMP_TREE, new KeyTrigger(KeyInput.KEY_T));
		inputManager.addMapping(TOGGLE_VORTON, new KeyTrigger(KeyInput.KEY_Y));
		inputManager.addMapping(PAUSE_SIM, new KeyTrigger(KeyInput.KEY_U));
		inputManager.addMapping(STEP_SIM, new KeyTrigger(KeyInput.KEY_I));
		inputManager.addListener(new DebugActionListener(), DUMP_TREE, PAUSE_SIM, TOGGLE_VORTON, STEP_SIM);
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
			else if (name.equals(STEP_SIM)){
				fluid.stepSimulation(VortonSpace.DT + 0.01f);
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
