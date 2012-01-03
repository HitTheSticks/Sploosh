package com.htssoft.sploosh.space;

import java.util.ArrayList;
import java.util.Collection;
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
	Vector3f tempVec = new Vector3f();
	
	/**
	 * Create a new octree with the given bounding box.
	 * */
	public OTree(Vector3f min, Vector3f max){
		root = new OTreeNode(min, max, 0);
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
	
	/**
	 * A node in the octree.
	 * */
	public class OTreeNode {
		public Vector3f cellMin = new Vector3f();
		public Vector3f cellMax = new Vector3f();
		public final int level;
		Vector3f splitPoint;
		ArrayList<Vorton> items = null;
		OTreeNode[] children = null;
		Vorton superVorton = new SimpleVorton();
		int vortonsPassedThroughHere = 0;
		
		OTreeNode(Vector3f min, Vector3f max, int level){
			cellMin.set(min);
			cellMax.set(max);
			this.level = level;
		}
		
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
		 * Insert another point
		 * */
		public void insert(Vorton vorton){
			superVorton.getVort().addLocal(vorton.getVort());
			//vorton.getPosition().mult(vorton.getVort().length(), tempVec);
			superVorton.getPosition().addLocal(vorton.getPosition()); //this needs to be eventually divided by n
			//superVorton.getPosition().addLocal(tempVec);
			vortonsPassedThroughHere++; //and here's our n.	
			
			if (children != null){
				chooseChild(vorton.getPosition()).insert(vorton);
				return;
			}
			
			items.add(vorton);
		}
		
		/**
		 * Updates all derived quantities, must be called before
		 * getting aggregate supervortons.
		 * */
		public void updateDerivedQuantities(){
			if (vortonsPassedThroughHere > 0){
				superVorton.getPosition().divideLocal(vortonsPassedThroughHere);
			}
			
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
		
		/**
		 * Split this node.
		 * */
		protected void split(int curLevel, int targetLevel){
			if (splitPoint != null){
				throw new IllegalStateException("This cell has already been split. You cannot resplit it.");
			}
			
			if (curLevel == targetLevel){
				items = new ArrayList<Vorton>();
				return;
			}
			
			splitPoint = new Vector3f();
			splitPoint.interpolate(cellMin, cellMax, 0.5f);	
			children = new OTreeNode[8];
			
			Vector3f tempMin = new Vector3f(), tempMax = new Vector3f();
			
			for (int i = 0; i < 8; i++){
				int child = i;
				if ((child & 0x01) == 0){ //low x
					tempMin.x = cellMin.x;
					tempMax.x = splitPoint.x;
				}
				else {
					tempMin.x = splitPoint.x;
					tempMax.x = cellMax.x;
				}
				
				if ((child & 0x02) == 0){ //low y
					tempMin.y = cellMin.y;
					tempMax.y = splitPoint.y;
				}
				else {
					tempMin.y = splitPoint.y;
					tempMax.y = cellMax.y;
				}
				
				if ((child & 0x04) == 0){ //low z
					tempMin.z = cellMin.z;
					tempMax.z = splitPoint.z;
				}
				else {
					tempMin.z = splitPoint.z;
					tempMax.z = cellMax.z;
				}
				
				OTreeNode newNode = new OTreeNode(tempMin, tempMax, curLevel + 1); 
				children[child] = newNode;
				newNode.split(curLevel + 1, targetLevel);
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
			if (pos.x > splitPoint.x){
				index |= 0x01;
			}
			if (pos.y > splitPoint.y){
				index |= 0x02;
			}
			if (pos.z > splitPoint.z){
				index |= 0x04;
			}
			
			return index;
		}
		
		protected OTreeNode chooseChild(Vector3f pos){
			int index = chooseChildIndex(pos);
			return children[index];
		}
		
		/**
		 * Gets all leaves in the tree that have at least
		 * one vorton in them.
		 * */
		public void getLeaves(ArrayList<OTreeNode> store){
			if (items != null){
				if (items.size() > 0){
					store.add(this);
				}
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
		 * Get the list of vortons (super and elementary) that contribute
		 * to the given position's velocity.
		 * 
		 * This is essentially the multipole method.
		 * */
		public void getInfluentialVortons(Vector3f pos, List<Vorton> storage){
			if (!contains(pos)){
				if (!superVorton.getVort().isZero()){
					storage.add(superVorton);
				}
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
		
		/**
		 * Print an pre-order traversal of the tree.
		 * */
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
		
		/**
		 * Get all nodes in the tree by pre-order.
		 * */
		public void preOrderTraverse(List<OTreeNode> out){
			out.add(this);
			if (children == null){
				return;
			}
			
			for (OTreeNode child : children){
				if (child == null){
					continue;
				}
				child.preOrderTraverse(out);
			}
		}
	}
	
	/**
	 * Get the pre-order traversal of this tree.
	 * */
	public List<OTreeNode> preOrderTraversal(){
		ArrayList<OTreeNode> retval = new ArrayList<OTreeNode>();
		root.preOrderTraverse(retval);
		return retval;
	}
	
	private static void printTab(int tabDepth){
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
