/**
 * 
 */
package org.bgp4j.netty.protocol;

import java.util.LinkedList;
import java.util.List;

import org.bgp4j.netty.BGPv4Constants;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * @author rainer
 *
 */
public class OpenPacket extends BGPv4Packet {
	private int protocolVersion;
	private int autonomousSystem;
	private int as4AutonomousSystem = -1;
	private int holdTime;
	private int bgpIdentifier;
	private List<Capability> capabilities = new LinkedList<Capability>();
	
	/**
	 * @return the protocolVersion
	 */
	public int getProtocolVersion() {
		return protocolVersion;
	}
	
	/**
	 * @param protocolVersion the protocolVersion to set
	 */
	public void setProtocolVersion(int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}
	
	/**
	 * @return the autonomuosSystem
	 */
	public int getAutonomousSystem() {
		return autonomousSystem;
	}
	
	/**
	 * @param autonomuosSystem the autonomuosSystem to set
	 */
	public void setAutonomousSystem(int autonomuosSystem) {
		this.autonomousSystem = autonomuosSystem;
	}
	
	/**
	 * @return the holdTime
	 */
	public int getHoldTime() {
		return holdTime;
	}
	
	/**
	 * @param holdTime the holdTime to set
	 */
	public void setHoldTime(int holdTime) {
		this.holdTime = holdTime;
	}
	
	/**
	 * @return the bgpIdentifier
	 */
	public int getBgpIdentifier() {
		return bgpIdentifier;
	}
	
	/**
	 * @param bgpIdentifier the bgpIdentifier to set
	 */
	public void setBgpIdentifier(int bgpIdentifier) {
		this.bgpIdentifier = bgpIdentifier;
	}
	
	/**
	 * @return the capabilities
	 */
	public List<Capability> getCapabilities() {
		return capabilities;
	}
	
	/**
	 * @param capabilities the capabilities to set
	 */
	public void setCapabilities(List<Capability> capabilities) {
		this.capabilities = capabilities;
	}

	@Override
	protected ChannelBuffer encodePayload() {
		ChannelBuffer buffer = ChannelBuffers.buffer(BGPv4Constants.BGP_PACKET_MAX_LENGTH);
		
		buffer.writeByte(getProtocolVersion());
		buffer.writeShort(getAutonomousSystem());
		buffer.writeShort(getHoldTime());
		buffer.writeInt(getBgpIdentifier());
		
		ChannelBuffer capabilities = Capability.encodeCapabilities(getCapabilities());
		
		buffer.writeByte(capabilities.readableBytes());
		if(capabilities.readableBytes() > 0)
			buffer.writeBytes(capabilities);
		
		return buffer;
	}

	@Override
	protected int getType() {
		return BGPv4Constants.BGP_PACKET_TYPE_OPEN;
	}

	/**
	 * @return the as4AutonomousSystem
	 */
	public int getAs4AutonomousSystem() {
		return as4AutonomousSystem;
	}

	/**
	 * @param as4AutonomousSystem the as4AutonomousSystem to set
	 */
	public void setAs4AutonomousSystem(int as4AutonomousSystem) {
		this.as4AutonomousSystem = as4AutonomousSystem;
	}
	
	/**
	 * get the effective autonomous system number. RFC 4893 defines that the AS OPEN field carries the
	 * magic number AS_TRANS and the the four-byte AS number is carried in capability field if the speakers
	 * support four-byte AS numbers
	 * 
	 * @return
	 */
	public int getEffectiveAutonomousSystem() {
		if(getAutonomousSystem() == BGPv4Constants.BGP_AS_TRANS)
			return getAs4AutonomousSystem();
		else
			return getAutonomousSystem();
	}
}
