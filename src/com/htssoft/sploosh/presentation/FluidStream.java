package com.htssoft.sploosh.presentation;

import com.htssoft.sploosh.VortonFreezeframe;
import com.htssoft.sploosh.space.OTree;

public class FluidStream {
	FluidTracerMesh tracerMesh;
	VortonFreezeframe vorticityField;
	
	public FluidStream(int nTracers, OTree vorticityTree){
		tracerMesh = new FluidTracerMesh(nTracers);
		vorticityField = new VortonFreezeframe(vorticityTree);
	}
}
