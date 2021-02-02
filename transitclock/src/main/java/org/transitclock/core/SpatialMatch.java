package org.transitclock.core;

import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.Location;

public abstract class SpatialMatch {
	protected final long avlTime;
	protected final Block block;
	protected final int tripIndex;
	protected Location predictedLocation;
	
	public Location getPredictedLocation() {
		return predictedLocation;
	}
	public SpatialMatch(long avlTime, Block block, int tripIndex) {
		super();
		this.avlTime = avlTime;
		this.block = block;
		this.tripIndex = tripIndex;
		this.predictedLocation=computeLocation();
	}
	public long getAvlTime() {
		return avlTime;
	}
	public Block getBlock() {
		return block;
	}
	public int getTripIndex() {
		return tripIndex;
	}
	protected abstract Location computeLocation();
	
}
