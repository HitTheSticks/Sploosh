package com.htssoft.sploosh;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.ObjectBuffer;
import com.htssoft.sploosh.space.OTree;
import com.htssoft.sploosh.space.OTreeNode;
import com.jme3.math.Vector3f;

public class OTreeStorage {
	
	protected static Kryo getKryo(){
		Kryo kryo = new Kryo();
		
		kryo.register(OTreeNode.class);
		kryo.register(OTreeNode[].class);
		kryo.register(OTree.class);
		kryo.register(SimpleVorton.class);
		kryo.register(Vector3f.class);
		kryo.register(ArrayList.class);
		
		return kryo;
	}
	
	protected static ObjectBuffer getObjectBuffer(Kryo k){
		return new ObjectBuffer(k, 2000000);
	}
	
	public static OTree load(InputStream in){
		return (OTree) getObjectBuffer(getKryo()).readObject(in, OTree.class);
	}
	
	public static OTree load(String filename){
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filename);
			return load(fis);
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		}
		finally {
			try {
				if (fis != null)
					fis.close();
			}
			catch (Exception ex){
				ex.printStackTrace();
			}
		}
		
		return null;
	}
	
	public static void dump(OTree tree, String filename){
		try {
			FileOutputStream fos = new FileOutputStream(filename);
			ObjectBuffer objBuf = getObjectBuffer(getKryo());
			objBuf.writeObject(fos, tree);
			fos.close();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
			System.exit(1);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
