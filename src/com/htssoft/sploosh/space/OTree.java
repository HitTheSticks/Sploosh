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
	Vector3f tempMin = new Vector3f(), tempMax = new Vector3f();
	
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
	public void getInfluentialVortons(Vector3f query, List<Vorton> storage) {
		root.getInfluentialVortons(query, storage);
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
		SimpleVorton superVorton = new SimpleVorton();
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
		protected void insert(Vorton vorton){
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
		
		public void reform(Vector3f min, Vector3f max){
			cellMin.set(min);
			cellMax.set(max);
			superVorton.getPosition().zero();
			superVorton.getVort().zero();
			vortonsPassedThroughHere = 0;
		}
		
		/**
		 * Split this node.
		 * */
		protected void split(int curLevel, int targetLevel){
			
			if (curLevel == targetLevel){
				if (items == null){
					items = new ArrayList<Vorton>();
				}
				else {
					items.clear();
				}
				return;
			}
			
			if (splitPoint == null){
				splitPoint = new Vector3f();
				splitPoint.interpolate(cellMin, cellMax, 0.5f);	
				children = new OTreeNode[8];
			}
			else {
				splitPoint.interpolate(cellMin, cellMax, 0.5f);
			}
			
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
				
				OTreeNode childNode;
				
				if (children[child] == null){
					childNode = new OTreeNode(tempMin, tempMax, curLevel + 1); 
					children[child] = childNode;	
				}
				else {
					childNode = children[child];
					childNode.reform(tempMin, tempMax);
				}
								
				childNode.split(curLevel + 1, targetLevel);
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
		 * This is essentially the multipole method. [Or, maybe not, upon further
		 * reading. Anybody know what the difference is between this and formal
		 * "fast multipole method"?]
		 * */
		protected void getInfluentialVortons(Vector3f pos, List<Vorton> storage){
			if (!contains(pos)){
				Vector3f superVort = superVorton.getVort();
				if (superVort.x != 0f || superVort.y != 0f || superVort.z != 0f){ //non-zero vort contributes
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
		protected void queryCellNeighbors(Vector3f pos, List<Vorton> storage){
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
		
		public OTreeNode deepCopy(){
			OTreeNode other = new OTreeNode(cellMin, cellMax, level);
			other.superVorton.set(this.superVorton);
			
			if (this.splitPoint != null){
				other.splitPoint = new Vector3f(this.splitPoint);
			}
			
			if (items != null){
				other.items = new ArrayList<Vorton>();
				for (Vorton v : items){
					other.items.add(new SimpleVorton(v));
				}
			}
			
			if (children != null){
				other.children = new OTreeNode[8];
				for (int i = 0; i < children.length; i++){
					if (children[i] != null){
						other.children[i] = children[i].deepCopy();
					}
				}
			}
			
			return other;
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
