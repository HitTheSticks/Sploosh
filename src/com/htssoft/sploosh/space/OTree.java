package com.htssoft.sploosh.space;

import java.util.ArrayList;
import java.util.List;

import com.htssoft.sploosh.SimpleVorton;
import com.htssoft.sploosh.Vorton;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
/**
 * This is a basic aggregating octree.
 * 
 * It is responsible for providing the "poles" in the multipole approximation of vorton
 * influence.
 * */
public class OTree {
	protected OTreeNode root;
	transient Vector3f tempVec = new Vector3f();
	transient Vector3f tempMin = new Vector3f();
	transient Vector3f tempMax = new Vector3f();
	
	/**
	 * Serialization only. Do not use.
	 * */
	public OTree(){
		
	}
	
	/**
	 * Create a new octree with the given bounding box.
	 * */
	public OTree(Vector3f min, Vector3f max){
		root = new OTreeNode(min, max, 0);
	}
	
	public int nVortons(){
		return root.vortonsPassedThroughHere;
	}
	
	/**
	 * Clone constructor.
	 * */
	protected OTree(OTree other){
		root = other.root.deepCopy();
	}
	
	public void rebuild(Vector3f min, Vector3f max, int level){
		root.reform(min, max);
		root.split(0, level);
	}
	
	/**
	 * Subdivide the root node to the given level of recursion.
	 * */
	public void splitTo(int level){
		root.split(0, level);
	}
	
	/**
	 * Get the root node.
	 * */
	public OTreeNode getRoot(){
		return root;
	}
	
	public void getVortons(List<Vorton> vortons){
		getRoot().getVortons(vortons);
	}
	
	/**
	 * Creates a deep copy of the current OTree.
	 * 
	 * This creates SimpleVorton copies of BufferedVortons
	 * from the live simulation.
	 * 
	 * It is designed for "freezing" a simuation. Conceivably,
	 * it could be used to can a sequence of Vorton states.
	 * */
	public OTree deepCopy(){
		return new OTree(this);
	}
	
	/**
	 * Insert into tree.
	 * */
	public void insert(Vorton v){
		root.insert(v);
	}
	
	/**
	 * Get the influential vortons for the given position.
	 * */
	public void getInfluentialVortons(Vector3f query, float searchRadius, List<Vorton> storage) {
		root.getInfluentialVortons(query, searchRadius, storage);
	}
		
	/**
	 * Get the pre-order traversal of this tree.
	 * */
	public List<OTreeNode> preOrderTraversal(){
		ArrayList<OTreeNode> retval = new ArrayList<OTreeNode>();
		root.preOrderTraverse(retval);
		return retval;
	}
	
	public static void printTab(int tabDepth){
		for (int i = 0; i < tabDepth; i++){
			System.out.print(" ");
		}
	}
	
	/**
	 * This is here for performance testing.
	 * */
	private static void doTestRun(){
		OTree jt = new OTree(new Vector3f(-1, -1, -1), new Vector3f(1, 1, 1));
		jt.splitTo(4);
		ArrayList<Vorton> toInsert = new ArrayList<Vorton>();
		
		for (int i = 0; i < 6000; i++){
			SimpleVorton v = new SimpleVorton();
			v.getPosition().set(FastMath.nextRandomFloat(), FastMath.nextRandomFloat(), FastMath.nextRandomFloat());
			v.getVort().set(FastMath.nextRandomFloat(), FastMath.nextRandomFloat(), FastMath.nextRandomFloat());
			toInsert.add(v);
		}
		
		long nanos = System.currentTimeMillis();
		
		for (Vorton v : toInsert){
			jt.getRoot().insert(v);
		}
		
		nanos = System.currentTimeMillis() - nanos;
		System.out.println(nanos + " ms to build.");
		
		jt.getRoot().updateDerivedQuantities();
		
		ArrayList<Vorton> results = new ArrayList<Vorton>(100);
		Vector3f qVec = new Vector3f(0.5f, 0.5f, 0.5f);
		nanos = System.nanoTime();
		jt.getRoot().getInfluentialVortons(qVec, 0.01f, results);
		nanos = System.nanoTime() - nanos;
		System.out.println(nanos + " ns to query.");
		
//		System.out.println("Results:");
//		for (Vorton v : results){
//			System.out.println(v);
//		}
		jt.getRoot().printTraversal(0);
	}
	
	public static void main(String[] args){
		for (int i = 0; i < 1; i++){
			doTestRun();
		}
	}
}
