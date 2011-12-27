package com.htssoft.sploosh.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.htssoft.sploosh.Vorton;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class OTree {
	protected int bucketLoad = 32;
	/**
	 * To be used only on insertion, which is single-threaded.
	 * */
	private Vector3f tempVec = new Vector3f();
	protected OTreeNode root;
	
	public OTree(){
		root = new OTreeNode();
	}
	
	public OTreeNode getRoot(){
		return root;
	}
	
	public class OTreeNode {
		Vector3f cellMin = new Vector3f(Vector3f.POSITIVE_INFINITY);
		Vector3f cellMax = new Vector3f(Vector3f.NEGATIVE_INFINITY);
		Vector3f splitPoint;
		ArrayList<Vorton> items = null;
		OTreeNode[] children = null;
		Vorton superVorton = new Vorton();
		int vortonsPassedThroughHere = 0;
		
		public Vector3f getMin(){
			return cellMin;
		}
		
		public Vector3f getMax(){
			return cellMax;
		}
		
		public void getItems(Collection<Vorton> store){
			store.addAll(items);
		}
		
		/**
		 * Insert another point, expanding this bounding box,
		 * and potentially splitting this node.
		 * */
		public void insert(Vorton vorton){
			cellMin.minLocal(vorton.position); //update bounding box
			cellMax.maxLocal(vorton.position);
			superVorton.vorticity.addLocal(vorton.vorticity);
			vorton.position.mult(vorton.vorticity, tempVec);
			superVorton.position.addLocal(tempVec); //this needs to be eventually divided by n
			vortonsPassedThroughHere++; //and here's our n.
			
			if (items == null && children == null){ //we're unused so far
				items = new ArrayList<Vorton>(bucketLoad + 1);
			}
			else if (children != null){ //we've previously had data, but have now split.
				chooseChild(vorton.position).insert(vorton);
				return;
			}
			
			items.add(vorton);
			
			if (items.size() > bucketLoad){
				split();
			}
		}
		
		/**
		 * Updates all derived quantities, must be called before
		 * getting aggregate supervortons.
		 * */
		public void updateDerivedQuantities(){
			superVorton.position.divideLocal(vortonsPassedThroughHere);
			if (children == null){
				return;
			}
			
			for (OTreeNode child : children){
				if (child == null){
					continue;
				}
				child.updateDerivedQuantities();
			}
		}
		
		protected void split(){
			splitPoint = new Vector3f();
			splitPoint.interpolate(cellMin, cellMax, 0.5f);	
			children = new OTreeNode[8];
			
			Iterator<Vorton> it = items.iterator();
			while (it.hasNext()){
				Vorton i = it.next();
				int child = chooseChildIndex(i.position);
				if (children[child] == null){
					children[child] = new OTreeNode();
				}
				children[child].insert(i);
				it.remove();
			}
			items = null;
		}
		
		/**
		 * Does this cell contain the given point?
		 * */
		public boolean contains(Vector3f pos){
			return pos.x >= cellMin.x && pos.x <= cellMax.x &&
				   pos.y >= cellMin.y && pos.y <= cellMax.y &&
				   pos.z >= cellMin.z && pos.z <= cellMax.z;
		}
		
		protected int chooseChildIndex(Vector3f pos){
			int index = 0;
			if (pos.x < splitPoint.x){
				index |= 0x01;
			}
			if (pos.y < splitPoint.y){
				index |= 0x02;
			}
			if (pos.z < splitPoint.z){
				index |= 0x04;
			}
			
			return index;
		}
		
		protected OTreeNode chooseChild(Vector3f pos){
			int index = chooseChildIndex(pos);
			if (children[index] == null){
				children[index] = new OTreeNode();
			}
			return children[index];
		}
		
		public void getLeaves(ArrayList<OTreeNode> store){
			if (items != null){
				store.add(this);
				return;
			}
			
			for (OTreeNode child : children){
				if (child == null){
					continue;
				}
				child.getLeaves(store);
			}
		}
		
		/**
		 * Get the list of vortons (super and otherwise) that contribute
		 * to the given position's velocity.
		 * 
		 * This is essentially the multipole method.
		 * */
		public void getInfluentialVortons(Vector3f pos, List<Vorton> storage){
			if (!contains(pos)){
				storage.add(superVorton);
				return;
			}
			
			if (children == null){
				storage.addAll(items);
				return;
			}
			
			for (OTreeNode child : children){
				if (child == null){
					continue;
				}
				
				child.getInfluentialVortons(pos, storage);
				
			}
		}
		
		/**
		 * Get all of the Vortons in the given cell.
		 * */
		public void queryCellNeighbors(Vector3f pos, List<Vorton> storage){
			if (!contains(pos)){ //we don't contain the position
				return;
			}
			
			if (children == null){ //we have no children, so we're the leaf cell
				storage.addAll(items);
				return;
			}
			
			for (OTreeNode child : children){
				if (child == null){
					continue;
				}
				child.queryCellNeighbors(pos, storage);
			}
		}
		
		
		public void printTraversal(int depth){
			System.out.print("\n");
			printTab(depth);
			System.out.print(depth + " Bounds: [" + cellMin + " " + cellMax + "]");
			if (items != null){
				printTab(depth);
				System.out.print("I");
				for (Vorton v : items){
					System.out.print("[" + v.toString() + "], ");
				}
			}
			if (children != null){
				printTab(depth);
				for (OTreeNode node : children){
					if (node == null){
						continue;
					}
					node.printTraversal(depth+1);
				}
			}
		}
	}
	
	private static void printTab(int tabDepth){
		for (int i = 0; i < tabDepth; i++){
			System.out.print(" ");
		}
	}
	
	private static void doTestRun(){
		OTree jt = new OTree();
		
		ArrayList<Vorton> toInsert = new ArrayList<Vorton>();
		
		for (int i = 0; i < 6000; i++){
			Vorton v = new Vorton();
			v.position.set(FastMath.nextRandomFloat(), FastMath.nextRandomFloat(), FastMath.nextRandomFloat());
			v.vorticity.set(FastMath.nextRandomFloat(), FastMath.nextRandomFloat(), FastMath.nextRandomFloat());
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
		jt.getRoot().getInfluentialVortons(qVec, results);
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
