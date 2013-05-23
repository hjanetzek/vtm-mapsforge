/*
 * Copyright 2013
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;

public class TileDecoder extends ProtobufDecoder {
	private static final Logger LOG = Logger.getLogger(TileDecoder.class.getName());

	private Tile mTile;

	private static final int TAG_TILE_VERSION = 1;
	// private static final int TAG_TILE_TIMESTAMP = 2;
	// private static final int TAG_TILE_ISWATER = 3;

	private static final int TAG_TILE_NUM_TAGS = 11;
	private static final int TAG_TILE_NUM_KEYS = 12;
	private static final int TAG_TILE_NUM_VALUES = 13;

	private static final int TAG_TILE_TAG_KEYS = 14;
	private static final int TAG_TILE_TAG_VALUES = 15;
	private static final int TAG_TILE_TAGS = 16;

	private static final int TAG_TILE_LINE = 21;
	private static final int TAG_TILE_POLY = 22;
	private static final int TAG_TILE_POINT = 23;

	private static final int TAG_ELEM_NUM_INDICES = 1;
	private static final int TAG_ELEM_NUM_TAGS = 2;
	// private static final int TAG_ELEM_HAS_ELEVATION = 3;
	private static final int TAG_ELEM_TAGS = 11;
	private static final int TAG_ELEM_INDEX = 12;
	private static final int TAG_ELEM_COORDS = 13;
	private static final int TAG_ELEM_LAYER = 21;

	private short[] mTmpShortArray = new short[100];
	private final Tag[][] mElementTags;

	private final TagSet curTags = new TagSet(100);
	// private IMapDatabaseCallback mMapGenerator;
	// scale coordinates to tile size
	private final static float REF_TILE_SIZE = 4096.0f;
	private final double mScale = REF_TILE_SIZE;

	private MapReadResultBuilder mMapReadResultBuilder;
	private List<Way> mWays;
	private List<PointOfInterest> mPois;

	TileDecoder() {
		// reusable tag set
		Tag[][] tags = new Tag[10][];
		for (int i = 0; i < 10; i++)
			tags[i] = new Tag[i + 1];
		mElementTags = tags;

	}

	MapReadResult decode(InputStream is, int contentLength, Tile tile)
	        throws IOException {
		mMapReadResultBuilder = new MapReadResultBuilder();
		mWays = new ArrayList<Way>();
		mPois = new ArrayList<PointOfInterest>();

		System.out.println(tile + " bytes:" + contentLength);

		setInputStream(is, contentLength);
		mTile = tile;

		curTags.clear(true);
		int version = -1;

		int val;
		int numTags = 0;
		int numKeys = -1;
		int numValues = -1;

		int curKey = 0;
		int curValue = 0;

		String[] keys = null;
		String[] values = null;

		while (hasData() && (val = decodeVarint32()) > 0) {
			// read tag and wire type
			int tag = (val >> 3);

			switch (tag) {
			case TAG_TILE_LINE:
			case TAG_TILE_POLY:
			case TAG_TILE_POINT:
				decodeTileElement(tag);
				break;

			case TAG_TILE_TAG_KEYS:
				if (keys == null || curKey >= numKeys) {
					LOG.info(mTile + " wrong number of keys " + numKeys);
					return null;
				}
				keys[curKey++] = decodeString();
				break;

			case TAG_TILE_TAG_VALUES:
				if (values == null || curValue >= numValues) {
					LOG.info(mTile + " wrong number of values " + numValues);
					return null;
				}
				values[curValue++] = decodeString();
				break;

			case TAG_TILE_NUM_TAGS:
				numTags = decodeVarint32();
				break;

			case TAG_TILE_NUM_KEYS:
				numKeys = decodeVarint32();
				keys = new String[numKeys];
				break;

			case TAG_TILE_NUM_VALUES:
				numValues = decodeVarint32();
				values = new String[numValues];
				break;

			case TAG_TILE_TAGS:
				int len = numTags * 2;
				if (mTmpShortArray.length < len)
					mTmpShortArray = new short[len];

				decodeVarintArray(len, mTmpShortArray);
				if (!decodeTileTags(numTags, mTmpShortArray, keys, values)) {
					LOG.info(mTile + " invalid tags");
					return null;
				}
				break;

			case TAG_TILE_VERSION:
				version = decodeVarint32();
				if (version != 4) {
					LOG.info(mTile + " invalid version " + version);
					return null;
				}
				break;

			default:
				LOG.info(mTile + " invalid type for tile: " + tag);
				return null;
			}
		}

		mMapReadResultBuilder
		        .add(new PoiWayBundle(mPois, mWays));

		return mMapReadResultBuilder.build();
	}

	private boolean decodeTileTags(int numTags, short[] tagIdx, String[] keys, String[] vals) {
		Tag tag;

		for (int i = 0; i < numTags * 2; i += 2) {
			int k = tagIdx[i];
			int v = tagIdx[i + 1];
			String key, val;

			if (k < Tags.ATTRIB_OFFSET) {
				if (k > Tags.MAX_KEY)
					return false;
				key = Tags.keys[k];
			} else {
				k -= Tags.ATTRIB_OFFSET;
				if (k >= keys.length)
					return false;
				key = keys[k];
			}

			if (v < Tags.ATTRIB_OFFSET) {
				if (v > Tags.MAX_VALUE)
					return false;
				val = Tags.values[v];
			} else {
				v -= Tags.ATTRIB_OFFSET;
				if (v >= vals.length)
					return false;
				val = vals[v];
			}

			tag = new Tag(key, val);

			curTags.add(tag);
		}

		return true;
	}

	private short[] decodeWayIndices(int indexCnt) throws IOException {
		if (mTmpShortArray.length < indexCnt)
			mTmpShortArray = new short[indexCnt];

		short[] index = mTmpShortArray;

		decodeVarintArray(indexCnt, index);

		// set end marker
		if (indexCnt < index.length)
			index[indexCnt] = -1;

		return index;
	}

	private boolean decodeTileElement(int type) throws IOException {

		int bytes = decodeVarint32();
		Tag[] tags = null;
		short[] index = null;

		int end = position() + bytes;
		int numIndices = 1;
		int numTags = 1;

		boolean fail = false;

		int coordCnt = 0;
		if (type == TAG_TILE_POINT)
			coordCnt = 1;

		int layer = 5;

		List<LatLong[][]> geoms = null;
		LatLong position = null;

		while (position() < end) {
			// read tag and wire type
			int val = decodeVarint32();
			if (val == 0)
				break;

			int tag = (val >> 3);

			switch (tag) {
			case TAG_ELEM_TAGS:
				tags = decodeElementTags(numTags);
				break;

			case TAG_ELEM_NUM_INDICES:
				numIndices = decodeVarint32();
				break;

			case TAG_ELEM_NUM_TAGS:
				numTags = decodeVarint32();
				break;

			case TAG_ELEM_INDEX:
				index = decodeWayIndices(numIndices);

				for (int i = 0; i < numIndices; i++)
					coordCnt += index[i];
				break;

			case TAG_ELEM_COORDS:
				if (coordCnt == 0) {
					LOG.info(mTile + " no coordinates");
				}

				if (type == TAG_TILE_LINE || type == TAG_TILE_POLY) {
					geoms = decodeInterleavedPoints(index, coordCnt, (type == TAG_TILE_POLY));
				} else {
					// int len =
					decodeVarint32();
					for (int i = 0; i < coordCnt; i += 1) {
						int x = deZigZag(decodeVarint32());
						int y = deZigZag(decodeVarint32());
						position = new LatLong(y / mScale, x / mScale);
					}
				}

				break;

			case TAG_ELEM_LAYER:
				layer = decodeVarint32();
				break;

			default:
				LOG.info(mTile + " invalid type for way: " + tag);
			}
		}

		if (fail || tags == null || numIndices == 0) {
			LOG.info(mTile + " failed reading way: bytes:" + bytes + " index:"
			         + (Arrays.toString(index)) + " tag:"
			         + (tags != null ? Arrays.deepToString(tags) : "null") + " "
			         + numIndices + " " + coordCnt);
			return false;
		}

		List<Tag> wayTags = new ArrayList<Tag>(Arrays.asList(tags));

		if (type == TAG_TILE_LINE || type == TAG_TILE_POLY) {

			for (LatLong[][] g : geoms)
				mWays.add(new Way((byte) layer, wayTags, g, null));
		} else {
			//System.out.println("add poi" + position + " " + Arrays.deepToString(tags));
			if (position != null)
				mPois.add(new PointOfInterest((byte) layer, wayTags, position));
		}

		return true;
	}

	protected List<LatLong[][]> decodeInterleavedPoints(short[] index, int numPoints,
	                                                    boolean poly) throws IOException {
		int bytes = decodeVarint32();

		readBuffer(bytes);

		int cnt = 0;
		int lastX = 0;
		int lastY = 0;
		boolean even = true;

		byte[] buf = buffer;
		int pos = bufferPos;
		int end = pos + bytes;
		int val;

		List<LatLong[][]> geoms = new ArrayList<LatLong[][]>();
		LatLong[][] geom;
		if (index == null) {
			LOG.info("skip coords " + numPoints + " byte:" + bytes);

		}
		for (int i = 0; i < index.length && index[i] >= 0;) {
			if (index[i] == 0) {
				i++;
				continue;
			}

			if (!poly) {
				geom = new LatLong[1][];
				geom[0] = new LatLong[index[i]];
				geoms.add(geom);
				i++;
				continue;
			}

			int rings = 0;
			for (; i + rings < index.length && index[i + rings] > 0;)
				rings++;

			geom = new LatLong[rings][];
			for (cnt = 0; cnt < rings; cnt++) {
				geom[cnt] = new LatLong[index[i + cnt] + 1];
			}
			i += rings;
			geoms.add(geom);
		}

		int curGeom = 0;
		geom = geoms.get(curGeom);

		int ring = 0;
		int num = 0;

		while (pos < end) {
			if (buf[pos] >= 0) {
				val = buf[pos++];

			} else if (buf[pos + 1] >= 0) {
				val = (buf[pos++] & 0x7f)
				      | buf[pos++] << 7;

			} else if (buf[pos + 2] >= 0) {
				val = (buf[pos++] & 0x7f)
				      | (buf[pos++] & 0x7f) << 7
				      | (buf[pos++]) << 14;

			} else if (buf[pos + 3] >= 0) {
				val = (buf[pos++] & 0x7f)
				      | (buf[pos++] & 0x7f) << 7
				      | (buf[pos++] & 0x7f) << 14
				      | (buf[pos++]) << 21;

			} else {
				val = (buf[pos++] & 0x7f)
				      | (buf[pos++] & 0x7f) << 7
				      | (buf[pos++] & 0x7f) << 14
				      | (buf[pos++] & 0x7f) << 21
				      | (buf[pos]) << 28;

				if (buf[pos++] < 0)
					throw new IOException("malformed VarInt32");
			}

			// zigzag decoding
			int s = ((val >>> 1) ^ -(val & 1));

			if (even) {
				lastX = lastX + s;
				even = false;
			} else {
				lastY = lastY + s;
				even = true;

				geom[ring][num++] = new LatLong(lastY / mScale, lastX / mScale);

				// close polygon
				if (poly && num == geom[ring].length - 1) {
					geom[ring][num++] = geom[ring][0];
				}

				if (num < geom[ring].length)
					continue;

				num = 0;
				ring++;

				if (ring < geom.length)
					continue;

				if (geoms.size() == ++curGeom)
					break;

				geom = geoms.get(curGeom);
				ring = 0;
			}
		}
		for (LatLong[][] g : geoms) {
			int c = 0;
			for (int i = 0; i < g.length; i++)
				for (int j = 0; j < g[i].length; j++) {
					if (g[i][j] == null)
						c++;
				}
			if (c > 0)
				System.out.println("eeek" + c);
		}
		// System.out.println("read points " + numPoints + "/" + cntAll);
		if (pos != bufferPos + bytes)
			throw new IOException("invalid array " + numPoints);

		bufferPos = pos;

		return geoms;
	}

	private static int deZigZag(int val) {
		return ((val >>> 1) ^ -(val & 1));
	}

	private Tag[] decodeElementTags(int numTags) throws IOException {
		if (mTmpShortArray.length < numTags)
			mTmpShortArray = new short[numTags];
		short[] tagIds = mTmpShortArray;

		decodeVarintArray(numTags, tagIds);

		Tag[] tags;

		if (numTags < 11)
			tags = mElementTags[numTags - 1];
		else
			tags = new Tag[numTags];

		int max = curTags.numTags;

		for (int i = 0; i < numTags; i++) {
			int idx = tagIds[i];

			if (idx < 0 || idx > max) {
				LOG.info(mTile + " invalid tag:" + idx + " " + i);
				return null;
			}

			tags[i] = curTags.tags[idx];
		}

		return tags;
	}
}
