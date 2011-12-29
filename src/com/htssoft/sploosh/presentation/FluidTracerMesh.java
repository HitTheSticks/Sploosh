package com.htssoft.sploosh.presentation;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.util.BufferUtils;

public class FluidTracerMesh extends Mesh {
	protected int verts;
	protected ArrayList<Vector3f> positions;
	protected Vector3f minPosition = new Vector3f();
	protected Vector3f maxPosition = new Vector3f();
	protected Vector3f scale = new Vector3f(1f, 1f, 1f);
	protected Vector3f temp = new Vector3f();
	
	public FluidTracerMesh(int nParticles){
		verts = nParticles;
		positions = new ArrayList<Vector3f>(verts);
		for (int i = 0; i < verts; i++){
			positions.add(new Vector3f());
		}
		
		setMode(Mode.Points);
		//start
		FloatBuffer pb = BufferUtils.createVector3Buffer(verts);
		VertexBuffer pvb = new VertexBuffer(Type.Position);
		pvb.setupData(Usage.Stream, 3, Format.Float, pb);
		setBuffer(pvb);
		
        //index
        ShortBuffer ib = BufferUtils.createShortBuffer(verts);
        for (short i = 0; i < verts; i++){
        	ib.put(i);
        }
        setBuffer(Type.Index, 1, ib);
	}
	
	public void setScale(Vector3f scale){
		this.scale.set(scale);
	}
	
	public Vector3f getScale(){
		return scale;
	}
	
	public List<Vector3f> getBuffer(){
		return positions;
	}
	
	public void updateBuffers(){
		VertexBuffer posVB = getBuffer(Type.Position);
		FloatBuffer pos = (FloatBuffer) posVB.getData();
		
		
		BoundingBox bb = (BoundingBox) getBound();
		
		minPosition.set(Vector3f.POSITIVE_INFINITY);
		maxPosition.set(Vector3f.NEGATIVE_INFINITY);
		
		pos.clear();
		
		for (int i = 0; i < positions.size(); i++){
			temp.set(positions.get(i));
			temp.multLocal(scale);
			pos.put(temp.x).put(temp.y).put(temp.z);
			minPosition.minLocal(temp);
			maxPosition.maxLocal(temp);
		}
		
		pos.clear();
		posVB.updateData(pos);
		
		bb.setMinMax(minPosition, maxPosition);
	}
}
