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
 * File: org.bgp4j.netty.protocol.update.UpdatePacketDecoder.java 
 */
package org.bgp4j.netty.protocol.update;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.bgp4j.netty.BGPv4Constants;
import org.bgp4j.netty.NetworkLayerReachabilityInformation;
import org.bgp4j.netty.protocol.ASType;
import org.bgp4j.netty.protocol.BGPv4Packet;
import org.bgp4j.netty.protocol.NotificationPacket;
import org.bgp4j.netty.protocol.ProtocolPacketUtils;
import org.bgp4j.netty.protocol.update.CommunityPathAttribute.CommunityMember;
import org.bgp4j.netty.protocol.update.OriginPathAttribute.Origin;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class UpdatePacketDecoder {
	private @Inject Logger log;

	/**
	 * decode the OPEN network packet. The passed channel buffer MUST point to the first packet octet AFTER the type octet.
	 * 
	 * @param buffer the buffer containing the data. 
	 * @return
	 */
	public BGPv4Packet decodeUpdatePacket(ChannelBuffer buffer) {
		UpdatePacket packet = new UpdatePacket();
		
		ProtocolPacketUtils.verifyPacketSize(buffer, BGPv4Constants.BGP_PACKET_MIN_SIZE_UPDATE, -1);
		
		if(buffer.readableBytes() < 2)
			throw new MalformedAttributeListException();
		
		// handle withdrawn routes
		int withdrawnOctets = buffer.readUnsignedShort();
		
		// sanity checking
		if(withdrawnOctets > buffer.readableBytes())
			throw new MalformedAttributeListException();
		
		ChannelBuffer withdrawnBuffer = null;		
		
		if(withdrawnOctets > 0) {
			withdrawnBuffer = ChannelBuffers.buffer(withdrawnOctets);
			
			buffer.readBytes(withdrawnBuffer);
		}

		// sanity checking
		if(buffer.readableBytes() < 2)
			throw new MalformedAttributeListException();

		// handle path attributes
		int pathAttributeOctets =  buffer.readUnsignedShort();
		
		// sanity checking
		if(pathAttributeOctets > buffer.readableBytes())
			throw new MalformedAttributeListException();
			
		ChannelBuffer pathAttributesBuffer = null;
		
		if(pathAttributeOctets > 0) {
			pathAttributesBuffer = ChannelBuffers.buffer(pathAttributeOctets);
			
			buffer.readBytes(pathAttributesBuffer);
		}
		
		if(withdrawnBuffer != null) {
			try {
				packet.getWithdrawnRoutes().addAll(decodeWithdrawnRoutes(withdrawnBuffer));
			} catch(IndexOutOfBoundsException e) {
				throw new MalformedAttributeListException();
			}
		}

		if(pathAttributesBuffer != null) {
			try {
				packet.getPathAttributes().addAll(decodePathAttributes(pathAttributesBuffer));
			} catch (IndexOutOfBoundsException ex) {
				throw new MalformedAttributeListException();
			}
		}
		
		// handle network layer reachability information
		if(buffer.readableBytes() > 0) {
			try {
				while (buffer.readable()) {
					packet.getNlris().add(
							NetworkLayerReachabilityInformation
									.decodeNLRI(buffer));
				}
			} catch (IndexOutOfBoundsException e) {
				throw new MalformedAttributeListException();
			}
		}
		
		return packet;
	}

	public NotificationPacket decodeUpdateNotification(ChannelBuffer buffer, int errorSubcode) {
		return null;
	}

	private ASPathAttribute decodeASPathAttribute(ChannelBuffer buffer, ASType asType) {
		ASPathAttribute attr = new ASPathAttribute(asType);

		/*
		 * If an IBGP speaker announces an internal INCOMPLETE route, the AS_PATH attribute is empty
		 */
		if(buffer.readable()) {
			try {
				attr.setPathType(ASPathAttribute.PathType.fromCode(buffer
						.readUnsignedByte()));

				int asCount = buffer.readUnsignedByte();

				for (int i = 0; i < asCount; i++) {
					int as;

					if (asType == ASType.AS_NUMBER_4OCTETS)
						as = (int) buffer.readUnsignedInt();
					else
						as = buffer.readUnsignedShort();

					attr.getAses().add(as);
				}

				// if there are more octets to read at this point, the packet is
				// malformed
				if (buffer.readable())
					throw new MalformedASPathAttributeException();
			} catch (IllegalArgumentException e) {
				log.error("cannot convert AS_PATH type", e);

				throw new MalformedASPathAttributeException();
			} catch (IndexOutOfBoundsException e) {
				log.error("short AS_PATH attribute", e);

				throw new MalformedASPathAttributeException();
			}
		} else {
			
		}
		return attr;
	}

	private OriginPathAttribute decodeOriginPathAttribute(ChannelBuffer buffer) {
		OriginPathAttribute attr = new OriginPathAttribute();
		
		if(buffer.readableBytes() != 1)
			throw new AttributeLengthException();
		
		try {
			attr.setOrigin(Origin.fromCode(buffer.readUnsignedByte()));
		} catch(IllegalArgumentException e) {
			log.error("cannot convert ORIGIN code", e);
			
			throw new InvalidOriginException();
		}
		
		return attr;
	}

	private MultiExitDiscPathAttribute decodeMultiExitDiscPathAttribute(ChannelBuffer buffer) {
		MultiExitDiscPathAttribute attr = new MultiExitDiscPathAttribute();
		
		if(buffer.readableBytes() != 4)
			throw new AttributeLengthException();
		
		attr.setDiscriminator((int)buffer.readUnsignedInt());
		
		return attr;
	}

	private LocalPrefPathAttribute decodeLocalPrefPathAttribute(ChannelBuffer buffer) {
		LocalPrefPathAttribute attr = new LocalPrefPathAttribute();
		
		if(buffer.readableBytes() != 4)
			throw new AttributeLengthException();
		
		attr.setLocalPreference((int)buffer.readUnsignedInt());
		
		return attr;
	}

	private NextHopPathAttribute decodeNextHopPathAttribute(ChannelBuffer buffer) {
		NextHopPathAttribute attr = new NextHopPathAttribute();
		
		if(buffer.readableBytes() != 4)
			throw new AttributeLengthException();
		
		try {
			byte[] addr = new byte[4];
			
			buffer.readBytes(addr);
			attr.setNextHop((Inet4Address)Inet4Address.getByAddress(addr));
		} catch (UnknownHostException e) {
			throw new InvalidNextHopException();
		}
		
		return attr;
	}

	private AtomicAggregatePathAttribute decodeAtomicAggregatePathAttribute(ChannelBuffer buffer) {
		AtomicAggregatePathAttribute attr = new AtomicAggregatePathAttribute();
		
		if(buffer.readableBytes() != 0)
			throw new AttributeLengthException();
		
		return attr;
	}

	private AggregatorPathAttribute decodeAggregatorPathAttribute(ChannelBuffer buffer, ASType asType) {
		AggregatorPathAttribute attr = new AggregatorPathAttribute(asType);
		
		if(buffer.readableBytes() != attr.getValueLength())
			throw new AttributeLengthException();		
		
		if(asType == ASType.AS_NUMBER_4OCTETS)
			attr.setAsNumber((int)buffer.readUnsignedInt());
		else
			attr.setAsNumber(buffer.readUnsignedShort());
		
		try {
			byte[] addr = new byte[4];
			
			buffer.readBytes(addr);
			attr.setAggregator((Inet4Address)Inet4Address.getByAddress(addr));
		} catch (UnknownHostException e) {
			throw new OptionalAttributeErrorException();
		}

		return attr;
	}

	private CommunityPathAttribute decodeCommunityPathAttribute(ChannelBuffer buffer) {
		CommunityPathAttribute attr = new CommunityPathAttribute();
		
		if(buffer.readableBytes() < 4 || (buffer.readableBytes() % 4 != 0))
			throw new OptionalAttributeErrorException();
		
		attr.setCommunity((int)buffer.readUnsignedInt());
		while(buffer.readable()) {
			CommunityMember member = new CommunityMember();
			
			member.setAsNumber(buffer.readUnsignedShort());
			member.setMemberFlags(buffer.readUnsignedShort());
			
			attr.getMembers().add(member);
		}
		
		return attr;
	}
	
	private List<Attribute> decodePathAttributes(ChannelBuffer buffer) {
		List<Attribute> attributes = new LinkedList<Attribute>();
		
		while(buffer.readable()) {
			buffer.markReaderIndex();
	
			try {
				int flagsType = buffer.readUnsignedShort();
				boolean optional = ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_OPTIONAL_BIT) != 0);
				boolean transitive = ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_TRANSITIVE_BIT) != 0);
				boolean partial = ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_PARTIAL_BIT) != 0);
				int typeCode = (flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MASK);
				int valueLength = 0;
	
				if ((flagsType & BGPv4Constants.BGP_PATH_ATTRIBUTE_EXTENDED_LENGTH_BIT) != 0)
					valueLength = buffer.readUnsignedShort();
				else
					valueLength = buffer.readUnsignedByte();
	
				ChannelBuffer valueBuffer = ChannelBuffers.buffer(valueLength);
	
				buffer.readBytes(valueBuffer);
	
				Attribute attr = null;
			
				switch (typeCode) {
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AGGREGATOR:
					attr = decodeAggregatorPathAttribute(valueBuffer, ASType.AS_NUMBER_2OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AS4_AGGREGATOR:
					attr = decodeAggregatorPathAttribute(valueBuffer, ASType.AS_NUMBER_4OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AS4_PATH:
					attr = decodeASPathAttribute(valueBuffer, ASType.AS_NUMBER_4OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_AS_PATH:
					attr = decodeASPathAttribute(valueBuffer, ASType.AS_NUMBER_2OCTETS);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_ATOMIC_AGGREGATE:
					attr = decodeAtomicAggregatePathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_COMMUNITIES:
					attr = decodeCommunityPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_LOCAL_PREF:
					attr = decodeLocalPrefPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_MULTI_EXIT_DISC:
					attr = decodeMultiExitDiscPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_NEXT_HOP:
					attr = decodeNextHopPathAttribute(valueBuffer);
					break;
				case BGPv4Constants.BGP_PATH_ATTRIBUTE_TYPE_ORIGIN:
					attr = decodeOriginPathAttribute(valueBuffer);
					break;
				default:
					attr = new UnknownPathAttribute(typeCode, valueBuffer);
					break;
				}
				attr.setOptional(optional);
				attr.setTransitive(transitive);
				attr.setPartial(partial);
				
				attributes.add(attr);
			} catch(AttributeException ex) {
				int endReadIndex = buffer.readerIndex();
				
				buffer.resetReaderIndex();
				
				int attributeLength = endReadIndex - buffer.readerIndex();
				byte[] packet = new byte[attributeLength];
				
				buffer.readBytes(packet);
				ex.setOffendingAttribute(packet);
				
				throw ex;
			} catch(IndexOutOfBoundsException ex) {
				int endReadIndex = buffer.readerIndex();
				
				buffer.resetReaderIndex();
				
				int attributeLength = endReadIndex - buffer.readerIndex();
				byte[] packet = new byte[attributeLength];
				
				buffer.readBytes(packet);
	
				throw new AttributeLengthException(packet);
			}
			
		}
		
		return attributes;
	}

	private List<NetworkLayerReachabilityInformation> decodeWithdrawnRoutes(ChannelBuffer buffer)  {
		List<NetworkLayerReachabilityInformation> routes = new LinkedList<NetworkLayerReachabilityInformation>();
		
		while(buffer.readable()) {
			routes.add(NetworkLayerReachabilityInformation.decodeNLRI(buffer));			
		}
		return routes;
	}

}
