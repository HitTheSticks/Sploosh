package com.htssoft.sploosh.presentation;

import java.util.List;

import com.htssoft.sploosh.space.OTree;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 * This builds a visual representation of the octree from a
 * {@link VortonSpace}. But, honestly, it's awful. Don't use it
 * unless you absolutely have to.
 * */
public class OTreeDebug {
	protected final static ColorRGBA[] levelColors = {
		new ColorRGBA(1, 1, 1, 1),
	};
	protected Node n;
	protected Material mat;
	protected Vector3f tempMin = new Vector3f();
	protected Vector3f tempMax = new Vector3f();
	protected OTree lastOtree;
	
	public OTreeDebug(Material mat){
		n = new Node();
		n.setQueueBucket(Bucket.Transparent);
		this.mat = mat;
	}
	
	public void updateTree(OTree tree, Vector3f scale){
		if (tree == null || tree == lastOtree){
			return;
		}
		lastOtree = tree;
		List<OTree.OTreeNode> nodes = tree.preOrderTraversal();
		n.detachAllChildren();
		
		for (OTree.OTreeNode onode : nodes){
			tempMin.set(onode.cellMin).multLocal(scale);
			tempMax.set(onode.cellMax).multLocal(scale);
			Box b = new Box(tempMin, tempMax);
			//System.out.println("Box at: " + onode.cellMin + "," + onode.cellMax);
			Geometry g = new Geometry();
			g.setMesh(b);
			g.setMaterial(mat);
			n.attachChild(g);
		}
	}
	
	public Node getNode(){
		return n;
	}
}
