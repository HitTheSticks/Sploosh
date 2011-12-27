package com.htssoft.sploosh.space;

import com.htssoft.sploosh.ThreadVars;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class UniformGrid<T> {
	protected int[] nCells = new int[3];
	protected BoundingBox bounds = new BoundingBox();
	protected Vector3f extent = new Vector3f();
	protected T[] values;
	protected T defaultValue;
	
	@SuppressWarnings("unchecked")
	public UniformGrid(Vector3f min, Vector3f max, int cellsPerUnit, T defaultValue){
		bounds.setMinMax(min, max);
		extent.set(max).subtractLocal(min);
		this.defaultValue = defaultValue;
		
		nCells[0] = (int) (FastMath.ceil(extent.x / cellsPerUnit));
		nCells[1] = (int) (FastMath.ceil(extent.y / cellsPerUnit));
		nCells[2] = (int) (FastMath.ceil(extent.z / cellsPerUnit));
		
		values = (T[]) new Object[nCells[0] * nCells[1] * nCells[2]];
	}
	
	public void insert(T value, Vector3f position, ThreadVars vars){
		if (!bounds.contains(position)){
			throw new IllegalArgumentException("Tried to add " + position + " to grid with bounds " + bounds);
		}

		values[indexOfPosition(position, vars)] = value;
	}
	
	public T get(Vector3f position, ThreadVars vars){
		int index = indexOfPosition(position, vars);
		
		T retval = values[index];
		if (retval == null){
			return defaultValue;
		}
		
		return retval;
	}
	
	protected int indexOfPosition(Vector3f position, ThreadVars vars){
		vars.temp0.set(position).subtractLocal(bounds.getMin(vars.temp1));
		vars.temp0.divideLocal(extent);
		int x, y, z;
		x = (int) (vars.temp0.x * nCells[0]);
		y = (int) (vars.temp0.y * nCells[1]);
		z = (int) (vars.temp0.z * nCells[2]);
		return linearize(x, y, z);
	}
	
	public void printTable(){
		for (int z = 0; z < nCells[2]; z++){
			for (int y = 0; y < nCells[1]; y++){
				for (int x = 0; x < nCells[0]; x++){
					System.out.print(values[linearize(x, y, z)]);
				}
				System.out.print("\n");
			}
			System.out.println("\t\t***");
		}
	}
	
	private int linearize(int x, int y, int z){
		return (z * nCells[0] * nCells[1]) + (y * nCells[0]) + x;
	}
}
