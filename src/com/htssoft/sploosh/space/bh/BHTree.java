package com.htssoft.sploosh.space.bh;

import java.util.ArrayList;
import java.util.Collection;

import com.jme3.math.Vector3f;

/**
 * A Barnes-Hutt tree.
 * */
public abstract class BHTree<BODY_T extends Body> {
	
	/**
	 * The body for this cell.
	 * */
	protected BODY_T body;
	protected ArrayList<BODY_T> overflowList = new ArrayList<BODY_T>();
	
	/**
	 * The split point of this BHTree.
	 * */
	protected Vector3f splitPoint = new Vector3f();
	
	/**
	 * Cell minimum.
	 * */
	public Vector3f cellMin = new Vector3f();
	
	/**
	 * Cell maximum.
	 * */
	public Vector3f cellMax = new Vector3f();
	
	/**
	 * The half length of a cube extent.
	 * */
	protected float areaWidth;
	
	/**
	 * Children of this node.
	 * */
	BHTree<BODY_T>[] children = null;
	
	public BHTree(Vector3f min, Vector3f max){
		this.cellMax.set(max);
		this.cellMin.set(min);
		areaWidth = Math.max(Math.max(cellMax.x - cellMin.x, cellMax.y - cellMin.y), cellMax.z - cellMax.z);
		
		splitPoint.interpolate(cellMin, cellMax, 0.5f);
	}
	
	/**
	 * Is this a leaf node?
	 * */
	public boolean isLeaf(){
		return children == null;
	}
	
	/**
	 * Do we have a body already?
	 * */
	public boolean hasBody(){
		return body != null;
	}
	
	/**
	 * A body has been added through this branch
	 * of the tree.
	 * */
	public boolean isTouched(){
		return !isLeaf() || hasBody();
	}
	
	/**
	 * Get influential bodies.
	 * */
	public void getInfluential(Vector3f pos, float theta, Collection<BODY_T> storage){
		if (!isTouched()){
			return;
		}
		
		float d = getSuperPosition().distance(pos);
		float s_d = areaWidth / d;
		if (Float.isInfinite(s_d) || s_d > theta){ //close enough to recurse
			if (isLeaf()){
				if (hasBody()){
					storage.add(body);
					if (!overflowList.isEmpty()){
						storage.addAll(overflowList);
					}
				}
				return;
			}
			
			for (int i = 0; i < children.length; i++){
				children[i].getInfluential(pos, theta, storage);
			}
			return;
		}
		else { //just use the super body.
			BODY_T b = getSuperBody();
			if (b != null){
				storage.add(getSuperBody());
			}
		}
	}
	
	/**
	 * Recursively add a body to the tree.
	 * */
	public void add(BODY_T newBody){
		if (newBody == null){
			throw new NullPointerException();
		}
		
		if (!contains(newBody.getPosition())){
			throw new IllegalStateException("Cannot add outside bounding box. " + newBody.getPosition());
		}
		
		integrateBody(newBody);
		
		if (isLeaf()){
			if (!hasBody()){ //empty, so just add it.
				this.body = newBody;
				return;
			}
			
			// they have identical position.
			if (newBody.getPosition().equals(body.getPosition())){
				overflowList.add(newBody);
				return;
			}
			
			//otherwise we need to split.
			split();
			chooseChild(this.body.getPosition()).add(this.body);
			if (!overflowList.isEmpty()){
				for (BODY_T b : overflowList){
					chooseChild(b.getPosition()).add(b);
				}
				overflowList.clear();
			}
			this.body = null;
			chooseChild(newBody.getPosition()).add(newBody);
		}
		else { //this is an internal node.
			chooseChild(newBody.getPosition()).add(newBody);
		}
	}
	
	/**
	 * Create a new node.
	 * */
	public abstract BHTree<BODY_T> newNode(Vector3f min, Vector3f max);
	
	/**
	 * Add this body to the integration.
	 * */
	public abstract void integrateBody(BODY_T body);

	/**
	 * Override this to update derived values.
	 * */
	public abstract void doUpdate();
	
	/**
	 * Get the integrated body for this node.
	 * */
	public abstract BODY_T getSuperBody();
	
	/**
	 * Get the position of the super body.
	 * */
	public abstract Vector3f getSuperPosition();
	
	/**
	 * Walk through the tree and update derived values.
	 * */
	public void updateDerived(){
		if (!isTouched()){
			return;
		}
		
		doUpdate();
		
		if (isLeaf()){
			return;
		}

		for (int i = 0; i < children.length; i++){
			children[i].updateDerived();
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void split(){
		if (children != null){
			throw new IllegalStateException("Cannot resplit node.");
		}
		
		Vector3f tempMin = new Vector3f();
		Vector3f tempMax = new Vector3f();
		
		children = new BHTree[8];
		
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
			
			BHTree<BODY_T> childTree = newNode(tempMin, tempMax);
			children[i] = childTree;
		}
	}
	
	/**
	 * Does this cell contain the given point?
	 * */
	public boolean contains(Vector3f pos){
		return pos.x >= cellMin.x && pos.x <= cellMax.x &&
			   pos.y >= cellMin.y && pos.y <= cellMax.y &&
			   pos.z >= cellMin.z && pos.z <= cellMax.z;
	}
	
	/**
	 * Get the appropriate child tree.
	 * */
	protected BHTree<BODY_T> chooseChild(Vector3f pos){
		int index = chooseChildIndex(pos);
		return children[index];
	}
	
	/**
	 * Get the index of the quadrant based on the point.
	 * */
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
}
