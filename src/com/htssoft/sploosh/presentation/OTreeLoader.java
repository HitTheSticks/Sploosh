package com.htssoft.sploosh.presentation;

import java.io.IOException;
import java.io.InputStream;

import com.htssoft.sploosh.OTreeStorage;
import com.htssoft.sploosh.space.OTree;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

public class OTreeLoader implements AssetLoader {

	@Override
	public Object load(AssetInfo assetInfo) throws IOException {
		InputStream is = assetInfo.openStream();
		
		OTree retval = (OTree) OTreeStorage.load(is);
		
		is.close();
		
		return retval;
	}

}
