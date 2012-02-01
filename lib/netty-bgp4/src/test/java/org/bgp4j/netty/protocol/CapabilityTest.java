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
 * File: org.bgp4j.netty.protocol.CapabilityTest.java 
 */
package org.bgp4j.netty.protocol;

import junit.framework.Assert;

import org.bgp4j.netty.BGPv4Constants;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class CapabilityTest {

	@Test
	public void testMultiProtocolCapability() {
		byte[] packet = new byte[] { 0x01, 0x04, 0x00, 0x01, 0x00, 0x01 };
		ChannelBuffer buffer = ChannelBuffers.buffer(packet.length);
		
		buffer.writeBytes(packet);
		
		Capability cap = Capability.decodeCapability(buffer);
		
		Assert.assertEquals(cap.getClass(), MultiProtocolCapability.class);
		Assert.assertEquals(cap.getCapabilityType(), BGPv4Constants.BGP_CAPABILITY_TYPE_MULTIPROTOCOL);
		Assert.assertEquals(BGPv4Constants.AddressFamily.IPv4, ((MultiProtocolCapability)cap).getAfi());
		Assert.assertEquals(BGPv4Constants.SubsequentAddressFamily.NLRI_UNICAST_FORWARDING, ((MultiProtocolCapability)cap).getSafi());
	}
	
	@Test
	public void testAutonomousSystem4Capability() {
		byte[] packet = new byte[] { 0x41, 0x04, 0x00, 0x00, (byte)0xfc, 0x00 };
		ChannelBuffer buffer = ChannelBuffers.buffer(packet.length);
		
		buffer.writeBytes(packet);
		
		Capability cap = Capability.decodeCapability(buffer);
		
		Assert.assertEquals(cap.getClass(), AutonomousSystem4Capability.class);
		Assert.assertEquals(cap.getCapabilityType(), BGPv4Constants.BGP_CAPABILITY_TYPE_AS4_NUMBERS);
		Assert.assertEquals(((AutonomousSystem4Capability)cap).getAutonomousSystem(), 64512);
	}
	
	@Test
	public void testRouteRefreshCapability() {
		byte[] packet = new byte[] { 0x02, 0x00 };
		ChannelBuffer buffer = ChannelBuffers.buffer(packet.length);
		
		buffer.writeBytes(packet);
		
		Capability cap = Capability.decodeCapability(buffer);
		
		Assert.assertEquals(cap.getClass(), RouteRefreshCapability.class);
	}
	
}
