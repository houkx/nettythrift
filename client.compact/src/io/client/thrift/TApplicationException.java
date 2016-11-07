/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.client.thrift;

/**
 * Application level exception
 *
 */
@SuppressWarnings("serial")
public class TApplicationException extends Exception {
	//
	// private static final int MESSAGE_FIELD = (11 << 16) | 1;
	// private static final int TYPE_FIELD = (8 << 16) | 2;

	// private static final long serialVersionUID = 1L;

	public static final int UNKNOWN = 0;
	public static final int UNKNOWN_METHOD = 1;
	public static final int INVALID_MESSAGE_TYPE = 2;
	public static final int WRONG_METHOD_NAME = 3;
	public static final int BAD_SEQUENCE_ID = 4;
	public static final int MISSING_RESULT = 5;
	public static final int INTERNAL_ERROR = 6;
	public static final int PROTOCOL_ERROR = 7;
	public static final int INVALID_TRANSFORM = 8;
	public static final int INVALID_PROTOCOL = 9;
	public static final int UNSUPPORTED_CLIENT_TYPE = 10;

	protected int type_;

	// public TApplicationException() {
	// super();
	// }
	//
	// public TApplicationException(int type) {
	// super();
	// type_ = type;
	// }
	//
	TApplicationException(int type, String message) {
		super(message);
		type_ = type;
	}

	// public TApplicationException(String message) {
	// super(message);
	// }

	public int getType() {
		return type_;
	}

	static TApplicationException read(TCompactProtocol iprot) throws Exception {
		int field;
		iprot.readStructBegin();

		String message = null;
		int type = 0;

		while (true) {
			field = iprot.readFieldBegin();
			byte fieldType = (byte) ((field >> 16) & 0x0000ffff);
			short fieldId = (short) (field & 0x0000ffff);
			if (fieldType == 0) {
				break;
			}
			switch (fieldId) {
			case 1:
				if (fieldType == 11) {
					message = iprot.readString();
				} else {
					ProtocolIOUtil.skip(iprot, fieldType);
				}
				break;
			case 2:
				if (fieldType == 8) {
					type = iprot.readI32();
				} else {
					ProtocolIOUtil.skip(iprot, fieldType);
				}
				break;
			default:
				ProtocolIOUtil.skip(iprot, fieldType);
				break;
			}
			iprot.readFieldEnd();
		}
		iprot.readStructEnd();

		return new TApplicationException(type, message);
	}
	//
	// public void write(CompactProtocol oprot) throws Exception {
	// oprot.writeStructBegin();
	// if (getMessage() != null) {
	// oprot.writeFieldBegin(MESSAGE_FIELD);
	// oprot.writeString(getMessage());
	// oprot.writeFieldEnd();
	// }
	// oprot.writeFieldBegin(TYPE_FIELD);
	// oprot.writeI32(type_);
	// oprot.writeFieldEnd();
	// oprot.writeFieldStop();
	// oprot.writeStructEnd();
	// }
}
