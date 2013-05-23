/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013 Hannes Jantzek
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.reader;

import java.io.File;
import java.util.HashMap;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.LayerManager;
import org.mapsforge.map.layer.TileLayer;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.MapWorker;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;

public class VtmRenderLayer extends TileLayer<RendererJob> {

	public static Layer create(TileCache tileCache, MapViewPosition mapViewPosition,
	                           LayerManager layerManager, GraphicFactory g) {

		VtmRenderLayer tileRendererLayer =
		        new VtmRenderLayer(tileCache, mapViewPosition, layerManager, g);

		tileRendererLayer.open();

		tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
		tileRendererLayer.setTextScale(1.0f);
		return tileRendererLayer;
	}

	private final VtmMapDatabase mapDatabase;
	private File mapFile = new File("/");
	private final MapWorker mapWorker;
	private float textScale;
	private XmlRenderTheme xmlRenderTheme;

	public VtmRenderLayer(TileCache tileCache, MapViewPosition mapViewPosition,
	                      LayerManager layerManager,
	                      GraphicFactory graphicFactory) {
		super(tileCache, mapViewPosition, graphicFactory);

		this.mapDatabase = new VtmMapDatabase();
		DatabaseRenderer databaseRenderer = new DatabaseRenderer(this.mapDatabase, graphicFactory);

		this.mapWorker = new MapWorker(tileCache, this.jobQueue, databaseRenderer, layerManager);
		this.mapWorker.start();

		this.textScale = 1;
	}

	@Override
	public void destroy() {
		super.destroy();
	}

	public File getMapFile() {
		return this.mapFile;
	}

	public float getTextScale() {
		return this.textScale;
	}

	public XmlRenderTheme getXmlRenderTheme() {
		return this.xmlRenderTheme;
	}

	public void open() {
		HashMap<String, String> options = new HashMap<String, String>();
		options.put("url", "http://city.informatik.uni-bremen.de/osci/testing/");
		this.mapDatabase.open(options);
	}

	public void setTextScale(float textScale) {
		this.textScale = textScale;
	}

	public void setXmlRenderTheme(XmlRenderTheme xmlRenderTheme) {
		this.xmlRenderTheme = xmlRenderTheme;
	}

	@Override
	protected RendererJob createJob(Tile tile) {
		return new RendererJob(tile, this.mapFile, this.xmlRenderTheme, this.textScale);
	}

}