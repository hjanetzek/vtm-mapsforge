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
import java.util.logging.Logger;

public class ProtobufDecoder {
	private static final Logger LOG = Logger.getLogger(ProtobufDecoder.class.getName());

	protected final static int VARINT_LIMIT = 5;
	private final static int VARINT_MAX = 10;

	private final static int BUFFER_SIZE = 1 << 15; // 32kb
	byte[] buffer = new byte[BUFFER_SIZE];

	// position in buffer
	int bufferPos;

	// bytes available in buffer
	int bufferFill;

	// offset of buffer in message
	private int mBufferOffset;

	// max bytes to read: message = header + content
	private long mReadMax;

	// overall bytes of message read
	private int mReadPos;

	private InputStream mInputStream;

	private final UTF8Decoder mStringDecoder;

	public ProtobufDecoder(){
		mStringDecoder = new UTF8Decoder();
	}

	public void setInputStream(InputStream is, int contentLength){
		mInputStream = is;
		bufferFill = 0;
		bufferPos = 0;
		mReadPos = 0;
		mReadMax = contentLength;
	}

	public void skip()throws IOException{
		int bytes = decodeVarint32();
		bufferPos += bytes;
	}

	public void decodeVarintArray(int num, short[] array) throws IOException {
		int bytes = decodeVarint32();

		readBuffer(bytes);

		int cnt = 0;

		byte[] buf = buffer;
		int pos = bufferPos;
		int end = pos + bytes;
		int val;

		while (pos < end) {
			if (cnt == num)
				throw new IOException("invalid array size " + num);

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
			} else  if (buf[pos + 4] >= 0){
				val = (buf[pos++] & 0x7f)
						| (buf[pos++] & 0x7f) << 7
						| (buf[pos++] & 0x7f) << 14
						| (buf[pos++] & 0x7f) << 21
						| (buf[pos++]) << 28;
			} else
				throw new IOException("malformed VarInt32");

			array[cnt++] = (short) val;
		}

		if (pos != bufferPos + bytes)
			throw new IOException("invalid array " + num);

		bufferPos = pos;
	}

	protected int decodeVarint32() throws IOException {
		if (bufferPos + VARINT_MAX > bufferFill)
			readBuffer(4096);

		byte[] buf = buffer;
		int pos = bufferPos;
		int val;

		if (buf[pos] >= 0) {
			val = buf[pos++];
		} else {

			if (buf[pos + 1] >= 0) {
				val = (buf[pos++] & 0x7f)
						| (buf[pos++]) << 7;

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

				// 'Discard upper 32 bits'
				int max = pos + VARINT_LIMIT;
				while (pos < max)
					if (buf[pos++] >= 0)
						break;

				if (pos == max)
					throw new IOException("malformed VarInt32");
			}
		}

		bufferPos = pos;

		return val;
	}

	public String decodeString() throws IOException {
		final int size = decodeVarint32();
		readBuffer(size);

		String result;

		if (mStringDecoder == null)
			result = new String(buffer,bufferPos, size, "UTF-8");
		else
			result = mStringDecoder.decode(buffer, bufferPos, size);

		bufferPos += size;

		return result;

	}
	public boolean hasData() {
		try {
			return readBuffer(1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;

		//return mBufferOffset + bufferPos < mReadEnd;
	}

	public int position() {
		return mBufferOffset + bufferPos;
	}

	public boolean readBuffer(int size) throws IOException {
		// check if buffer already contains the request bytes
		if (bufferPos + size < bufferFill)
			return true;

		// check if inputstream is read to the end
		if (mReadPos == mReadMax)
			return false;

		int maxSize = buffer.length;

		if (size > maxSize) {
			LOG.info( "increase read buffer to " + size + " bytes");
			maxSize = size;
			byte[] tmp = new byte[maxSize];
			bufferFill -= bufferPos;
			System.arraycopy(buffer, bufferPos, tmp, 0, bufferFill);
			mBufferOffset += bufferPos;
			bufferPos = 0;
			buffer = tmp;
		}

		if (bufferFill == bufferPos) {
			mBufferOffset += bufferPos;
			bufferPos = 0;
			bufferFill = 0;
		} else if (bufferPos + size > maxSize) {
			// copy bytes left to the beginning of buffer
			bufferFill -= bufferPos;
			System.arraycopy(buffer, bufferPos, buffer, 0, bufferFill);
			mBufferOffset += bufferPos;
			bufferPos = 0;
		}

		int max = maxSize - bufferFill;

		while ((bufferFill - bufferPos) < size && max > 0) {

			max = maxSize - bufferFill;
			if (max > mReadMax- mReadPos)
				max = (int) (mReadMax- mReadPos);
			// read until requested size is available in buffer
			//LOG.info("reading " + max);

			int len = mInputStream.read(buffer, bufferFill, max);
			//LOG.info("read " + len);


			if (len < 0) {
				// finished reading, mark end
				buffer[bufferFill] = 0;
				return false;
			}

			mReadPos += len;
			bufferFill += len;

			if (mReadPos == mReadMax)
				break;

		}
		return true;
	}

}
