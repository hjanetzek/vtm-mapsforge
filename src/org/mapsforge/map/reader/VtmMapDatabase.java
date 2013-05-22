/*
 * Copyright 2012 Hannes Janetzek
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

import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.mapsforge.core.model.Tile;
import org.mapsforge.map.reader.header.FileOpenResult;
import org.mapsforge.map.reader.header.MapFileInfo;

/**
 *
 *
 */
public class VtmMapDatabase extends MapDatabase {
	private static final Logger LOG = Logger.getLogger(VtmMapDatabase.class.getName());


	// 'open' state
	private boolean mOpen = false;
	private LwHttp conn;

	@Override
	public MapReadResult readMapData(Tile tile) {
		//LOG.info(">>> " + tile);

		//QueryResult result = QueryResult.SUCCESS;
		MapReadResult result = null;
		try {
			InputStream is;
			if (conn.sendRequest(tile) && (is = conn.readHeader()) != null) {
				//conn.cacheBegin(tile, f);
				result = mTileDecoder.decode(is, conn.contentLength, tile);
			} else {
				LOG.info( tile + " Network Error");
				return null;
			}
		} catch (SocketException ex) {
			LOG.info( tile + " Socket exception: " + ex.getMessage());
			return null;
		} catch (SocketTimeoutException ex) {
			LOG.info( tile + " Socket Timeout exception: " + ex.getMessage());
			return null;
		} catch (UnknownHostException ex) {
			LOG.info( tile + " no network");
			return null;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}

		conn.mLastRequest = System.currentTimeMillis();
		if (result == null)
			LOG.info("<<< " + tile);

		return result;
	}

	@Override
	public MapFileInfo getMapFileInfo() {
		return null;
	}

	TileDecoder mTileDecoder;
	@Override
	public boolean hasOpenFile(){
		return mOpen;
	}

	public FileOpenResult open(HashMap<String, String> options) {
		if (mOpen)
			return FileOpenResult.SUCCESS;

		if (options == null || !options.containsKey("url"))
			return new FileOpenResult("options missing");

		conn = new LwHttp();

		if (!conn.setServer(options.get("url"))) {
			return new FileOpenResult("invalid url: " + options.get("url"));
		}

		mOpen = true;

		mTileDecoder = new TileDecoder();

		return FileOpenResult.SUCCESS;
	}

	public void close() {
		mOpen = false;
		conn.close();
	}
}
