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
 * File: org.bgp4j.rib.RoutingInformationBaseTest.java 
 */
package org.bgp4j.rib;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bgp4j.net.AddressFamily;
import org.bgp4j.net.AddressFamilyKey;
import org.bgp4j.net.InetAddressNextHop;
import org.bgp4j.net.NetworkLayerReachabilityInformation;
import org.bgp4j.net.NextHop;
import org.bgp4j.net.RIBSide;
import org.bgp4j.net.SubsequentAddressFamily;
import org.bgp4j.net.attributes.PathAttribute;
import org.bgp4j.weld.WeldTestCaseBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class RoutingInformationBaseTest extends WeldTestCaseBase {

	
	private static final String RIB_NAME = "rib1";
	private static final AddressFamilyKey RIB_AFK = new AddressFamilyKey(AddressFamily.IPv4, SubsequentAddressFamily.NLRI_UNICAST_FORWARDING);
	private static final RIBSide RIB_SIDE = RIBSide.Local;
	private static final NetworkLayerReachabilityInformation LESS_NLRI = new NetworkLayerReachabilityInformation(24, 
			new byte[] { (byte)0xc0, (byte)0xa8, (byte)0x04 });
	private static final NetworkLayerReachabilityInformation MORE_NLRI_1 = new NetworkLayerReachabilityInformation(28, 
			new byte[] { (byte)0xc0, (byte)0xa8, (byte)0x04, (byte)0x10 });
	private static final NetworkLayerReachabilityInformation MORE_NLRI_2 = new NetworkLayerReachabilityInformation(28, 
			new byte[] { (byte)0xc0, (byte)0xa8, (byte)0x04, (byte)0x20 });	
	
	@Before
	public void before() {
		catcher = obtainInstance(RouteEventCatcher.class);
		catcher.reset();

		listener = new RouteEventCatchListener();
		List<RoutingEventListener> listeners = new LinkedList<RoutingEventListener>();
		
		listeners.add(listener);
		
		rib = obtainInstance(RoutingInformationBase.class);
		rib.setPeerName(RIB_NAME);
		rib.setAddressFamilyKey(RIB_AFK);
		rib.setSide(RIB_SIDE);
		rib.setListeners(listeners);
		
		attrs = new HashSet<PathAttribute>();
	}
	
	@After
	public void after() {
		catcher = null;
		
		rib.destroyRIB();
		rib.setListeners(null);
		rib = null;
		
		attrs = null;
	}
	
	private RouteEventCatcher catcher;
	private RouteEventCatchListener listener;
	private RoutingInformationBase rib;
	private Collection<PathAttribute> attrs;
	
	@Test
	public void testAddSinglePrefix() {
		rib.addRoutes(Arrays.asList(LESS_NLRI), attrs, null);

		Assert.assertEquals(1, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
	}

	@Test
	public void testAddSinglePrefixNextHop() throws Exception {
		InetAddressNextHop<InetAddress> nextHop = new InetAddressNextHop<InetAddress>(
				InetAddress.getByAddress(new byte[] { (byte)0xc0, (byte)0xa8, (byte)0x01, (byte)0x01 }));
		
		rib.addRoutes(Arrays.asList(LESS_NLRI), attrs, nextHop);

		Assert.assertEquals(1, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, nextHop)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, nextHop)));
	}

	@Test
	public void testAddTwoPrefixFirstLessSecondMore() {
		rib.addRoutes(Arrays.asList(LESS_NLRI, MORE_NLRI_1), attrs, null);
		
		Assert.assertEquals(2, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
	}
	
	@Test
	public void testAddTwoPrefixFirstMoreSecondLess() {
		rib.addRoutes(Arrays.asList(MORE_NLRI_1, LESS_NLRI), attrs, null);
		
		Assert.assertEquals(2, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
	}
	
	@Test
	public void testAddThreePrefix() {
		rib.addRoutes(Arrays.asList(MORE_NLRI_1, MORE_NLRI_2, LESS_NLRI), attrs, null);
		
		Assert.assertEquals(3, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2, attrs, null)));
	}
	
	@Test
	public void testAddThreePrefixRemoveOne() {
		rib.addRoutes(Arrays.asList(MORE_NLRI_1, MORE_NLRI_2, LESS_NLRI), attrs, null);
		
		Assert.assertEquals(3, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2, attrs, null)));
		
		rib.withdrawRoutes(Arrays.asList(MORE_NLRI_2));
		Assert.assertEquals(3, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(1, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteWithdrawnEvents().contains(new RouteWithdrawn(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2)));
		Assert.assertEquals(3, listener.getRouteAddedEvents().size());
		Assert.assertEquals(1, listener.getRouteWithdrawnEvents().size());
		Assert.assertTrue(listener.getRouteWithdrawnEvents().contains(new RouteWithdrawn(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2)));
	}
	
	public static class RecordingNodeVisitor implements RoutingInformationBaseVisitor {

		public static class Record {
			private String ribName;
			private AddressFamilyKey afk;
			private RIBSide side;
			private NetworkLayerReachabilityInformation nlri;
			private NextHop nextHop;
			private Collection<PathAttribute> pathAttributes;
			
			public Record(String peerName, AddressFamilyKey afk, RIBSide side, 
					NetworkLayerReachabilityInformation nlri, NextHop nextHop, Collection<PathAttribute> pathAttributes) {
				this.ribName = peerName;
				this.afk = afk;
				this.side = side;
				this.nlri = nlri;
				this.nextHop = nextHop;
				this.pathAttributes = pathAttributes;
			}

			/* (non-Javadoc)
			 * @see java.lang.Object#hashCode()
			 */
			@Override
			public int hashCode() {
				HashCodeBuilder builder = (new HashCodeBuilder())
						.append(ribName)
						.append(afk)
						.append(side)
						.append(nlri)
						.append(nextHop);
				
				for(PathAttribute a : pathAttributes)
					builder.append(a);
				
				return builder.toHashCode();
			}

			/* (non-Javadoc)
			 * @see java.lang.Object#equals(java.lang.Object)
			 */
			@Override
			public boolean equals(Object obj) {
				if(!(obj instanceof Record))
					return false;
				
				Record o = (Record)obj;
				EqualsBuilder builder = (new EqualsBuilder())
						.append(getRibName(), o.getRibName())
						.append(getAfk(), o.getAfk())
						.append(getSide(), o.getSide())
						.append(getNextHop(), o.getNextHop())
						.append(getNlri(), o.getNlri())
						.append(getPathAttributes().size(), o.getPathAttributes().size());
				
				if(builder.isEquals()) {
					Iterator<PathAttribute> lit = getPathAttributes().iterator();
					Iterator<PathAttribute> rit = o.getPathAttributes().iterator();
					
					while(lit.hasNext())
						builder.append(lit.next(), rit.next());
				}
				
				return builder.isEquals();
			}

			/**
			 * @return the nlri
			 */
			public NetworkLayerReachabilityInformation getNlri() {
				return nlri;
			}

			/**
			 * @return the nextHop
			 */
			public NextHop getNextHop() {
				return nextHop;
			}

			/**
			 * @return the pathAttributes
			 */
			public Collection<PathAttribute> getPathAttributes() {
				return pathAttributes;
			}

			/**
			 * @return the peerName
			 */
			public String getRibName() {
				return ribName;
			}

			/**
			 * @return the afk
			 */
			public AddressFamilyKey getAfk() {
				return afk;
			}

			/**
			 * @return the side
			 */
			public RIBSide getSide() {
				return side;
			}
		}

		private List<Record> records = new LinkedList<Record>();


		/**
		 * @return the records
		 */
		public List<Record> getRecords() {
			return records;
		}


		@Override
		public void visitRouteNode(String peerName, AddressFamilyKey afk,
				RIBSide side, NetworkLayerReachabilityInformation nlri,
				NextHop nextHop, Collection<PathAttribute> pathAttributes) {
			records.add(new Record(peerName, afk, side, nlri, nextHop, pathAttributes));
		}
	}

	@Test
	public void testVisitThreePrefix() {
		rib.addRoutes(Arrays.asList(MORE_NLRI_1, MORE_NLRI_2, LESS_NLRI), attrs, null);
		
		Assert.assertEquals(3, catcher.getRouteAddedEvents().size());
		Assert.assertEquals(0, catcher.getRouteWithdrawnEvents().size());
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(catcher.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, LESS_NLRI, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_1, attrs, null)));
		Assert.assertTrue(listener.getRouteAddedEvents().contains(new RouteAdded(RIB_NAME, RIB_SIDE, RIB_AFK, MORE_NLRI_2, attrs, null)));
		
		RecordingNodeVisitor visitor = new RecordingNodeVisitor();
		
		rib.visitRoutingNodes(visitor);
		
		Iterator<RecordingNodeVisitor.Record> it = visitor.getRecords().iterator();
		
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals(new RecordingNodeVisitor.Record(RIB_NAME, RIB_AFK, RIB_SIDE, LESS_NLRI, null, attrs), it.next());
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals(new RecordingNodeVisitor.Record(RIB_NAME, RIB_AFK, RIB_SIDE, MORE_NLRI_1, null, attrs), it.next());
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals(new RecordingNodeVisitor.Record(RIB_NAME, RIB_AFK, RIB_SIDE, MORE_NLRI_2, null, attrs), it.next());
		Assert.assertFalse(it.hasNext());
	}

}
