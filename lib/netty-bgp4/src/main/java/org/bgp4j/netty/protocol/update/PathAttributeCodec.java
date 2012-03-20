/**
 *  Copyright 2012 Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 * File: org.bgp4j.netty.protocol.update.PathAttributeCodec.java 
 */
package org.bgp4j.netty.protocol.update;

import java.util.HashMap;
import java.util.Map;

import org.bgp4j.net.ASPathAttribute;
import org.bgp4j.net.AggregatorPathAttribute;
import org.bgp4j.net.AtomicAggregatePathAttribute;
import org.bgp4j.net.ClusterListPathAttribute;
import org.bgp4j.net.CommunityPathAttribute;
import org.bgp4j.net.LocalPrefPathAttribute;
import org.bgp4j.net.MultiExitDiscPathAttribute;
import org.bgp4j.net.MultiProtocolReachableNLRI;
import org.bgp4j.net.MultiProtocolUnreachableNLRI;
import org.bgp4j.net.NextHopPathAttribute;
import org.bgp4j.net.OriginPathAttribute;
import org.bgp4j.net.OriginatorIDPathAttribute;
import org.bgp4j.net.PathAttribute;
import org.bgp4j.net.UnknownPathAttribute;
import org.bgp4j.netty.BGPv4Constants;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class PathAttributeCodec {

	private static  Map<Class<? extends PathAttribute>, PathAttributeCodecHandler<? extends PathAttribute>> codecs; 

	static {
		codecs = new HashMap<Class<? extends PathAttribute>, PathAttributeCodecHandler<? extends PathAttribute>>();
		
		codecs.put(AggregatorPathAttribute.class, new AggregatorPathAttributeCodecHandler());
		codecs.put(ASPathAttribute.class, new ASPathAttributeCodecHandler());
		codecs.put(AtomicAggregatePathAttribute.class, new AtomicAggregatePathAttributeCodecHandler());
		codecs.put(ClusterListPathAttribute.class, new ClusterListPathAttributeCodecHandler());
		codecs.put(CommunityPathAttribute.class, new CommunityPathAttributeCodecHandler());
		codecs.put(LocalPrefPathAttribute.class, new LocalPrefPathAttributeCodecHandler());
		codecs.put(MultiExitDiscPathAttribute.class, new MultiExitDiscPathAttributeCodecHandler());
		codecs.put(MultiProtocolReachableNLRI.class, new MultiProtocolReachableNLRICodecHandler());
		codecs.put(MultiProtocolUnreachableNLRI.class, new MultiProtocolUnreachableNLRICodecHandler());
		codecs.put(NextHopPathAttribute.class, new NextHopPathAttributeCodecHandler());
		codecs.put(OriginatorIDPathAttribute.class, new OriginatorIDPathAttributeCodecHandler());
		codecs.put(OriginPathAttribute.class, new OriginPathAttributeCodecHandler());
		codecs.put(UnknownPathAttribute.class, new UnknownPathAttributeCodecHandler());
	}
	
	/**
	 * encode the path attribute for network transmission
	 * 
	 * @return an encoded formatted path attribute
	 */
	public static ChannelBuffer encodePathAttribute(PathAttribute attr)  {
		ChannelBuffer buffer = ChannelBuffers.buffer(BGPv4Constants.BGP_PACKET_MAX_LENGTH);
		int valueLength = valueLength(attr);
		int attrFlagsCode = 0;
				
		if(attr.isOptional())
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_OPTIONAL_BIT;
		
		if(attr.isTransitive())
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_TRANSITIVE_BIT;

		if(attr.isPartial())
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_PARTIAL_BIT;
		
		if(valueLength > 255)
			attrFlagsCode |= BGPv4Constants.BGP_PATH_ATTRIBUTE_EXTENDED_LENGTH_BIT;
		
		attrFlagsCode |= (typeCode(attr) & BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MASK);

		buffer.writeShort(attrFlagsCode);
		
		if(valueLength > 255)
			buffer.writeShort(valueLength);
		else
			buffer.writeByte(valueLength);
		
		if(valueLength > 0)
			buffer.writeBytes(encodeValue(attr));
		
		return buffer;
	}
	
	public static int calculateEncodedPathAttributeLength(PathAttribute attr) {
		int size = 2; // attribute flags + type field;
		int valueLength = valueLength(attr);
		
		size += (valueLength > 255) ? 2 : 1; // length field;
		size += valueLength;
		
		return size;
	}
	
	/**
	 * get the attribute value length
	 * @return
	 */
	@SuppressWarnings("unchecked")
	static int valueLength(PathAttribute attr) {
		if(codecs.containsKey(attr.getClass())) {
			return ((PathAttributeCodecHandler<PathAttribute>)codecs.get(attr.getClass())).valueLength(attr);
		} else {
			throw new IllegalArgumentException("cannot handle path attribute of type: " + attr.getClass().getName());			
		}
	}

	/**
	 * get the specific type code (see RFC 4271)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	static int typeCode(PathAttribute attr) {
		if(codecs.containsKey(attr.getClass())) {
			return ((PathAttributeCodecHandler<PathAttribute>)codecs.get(attr.getClass())).typeCode(attr);
		} else {
			throw new IllegalArgumentException("cannot handle path attribute of type: " + attr.getClass().getName());			
		}
	}

	/**
	 * get the encoded attribute value
	 */
	@SuppressWarnings("unchecked")
	static ChannelBuffer encodeValue(PathAttribute attr) {
		if(codecs.containsKey(attr.getClass())) {
			return ((PathAttributeCodecHandler<PathAttribute>)codecs.get(attr.getClass())).encodeValue(attr);
		} else {
			throw new IllegalArgumentException("cannot handle path attribute of type: " + attr.getClass().getName());			
		}
	}

}
