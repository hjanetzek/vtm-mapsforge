package org.mapsforge.map.reader.header;

import org.mapsforge.core.model.BoundingBox;

public class MapInfo {

	private MapInfo() {
		MapFileInfoBuilder b = new MapFileInfoBuilder();
		b.boundingBox = new BoundingBox(-180, -90, 180, 90);
		b.fileSize = 1;
		b.fileVersion = 3;
		b.projectionName = "TILE";
		//b.optionalFields = new OptionalFields();
		//b.optionalFields.comment ="yo!";
		//b.optionalFields.hasStartPosition = true;
		//b.optionalFields.startPosition = new LatLong(53.11, 8.85);
		//b.optionalFields.startZoomLevel= 10;
		info = b.build();
	}
	public static MapInfo INSTANCE = new MapInfo();
	public static MapFileInfo info;
}
