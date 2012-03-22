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
 * File: org.bgp4j.rib.RoutingInformationBase.java 
 */
package org.bgp4j.rib;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.bgp4j.net.AddressFamilyKey;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class PeerRoutingInformationBase {

	private String peerName;
	private Map<AddressFamilyKey, RoutingInformationBase> localRIBs = new HashMap<AddressFamilyKey, RoutingInformationBase>();
	private Map<AddressFamilyKey, RoutingInformationBase> remoteRIBs = new HashMap<AddressFamilyKey, RoutingInformationBase>();
	private @Inject Instance<RoutingInformationBase> ribProvider;
	private @Inject Event<RoutingInformationBaseCreated> created;
	private @Inject Event<RoutingInformationBaseDestroyed> destroyed;
	
	public PeerRoutingInformationBase() {
	}

	/**
	 * @return the peerName
	 */
	public String getPeerName() {
		return peerName;
	}

	public void allocateRoutingInformationBase(RIBSide side, AddressFamilyKey afk) {
		RoutingInformationBase rib = null;
		
		switch(side) {
		case Local:
			rib = allocateRoutingInformationBase(localRIBs, side, afk);
			break;
		case Remote:
			rib = allocateRoutingInformationBase(remoteRIBs, side, afk);
			break;
		}
		
		if(rib != null) {
			created.fire(new RoutingInformationBaseCreated(this.peerName, side, afk));
		}
	}
	
	public void destroyRoutingInformationBase(RIBSide side, AddressFamilyKey afk) {
		boolean found = false;
		
		switch(side) {
		case Local:
			found = destroyRoutingInformationBase(localRIBs, afk);
			break;
		case Remote:
			found = destroyRoutingInformationBase(remoteRIBs, afk);
			break;
		}
		
		if(found ) {
			destroyed.fire(new RoutingInformationBaseDestroyed(this.peerName, side, afk));
		}
		
	}	
	
	public RoutingInformationBase routingBase(RIBSide side, AddressFamilyKey afk) {
		RoutingInformationBase rib = null;
		
		switch(side) {
		case Local:
			rib = localRIBs.get(afk);
		break;
		case Remote:
			rib = remoteRIBs.get(afk);
			break;
		}
		
		return rib;
	}
	
	private RoutingInformationBase allocateRoutingInformationBase(Map<AddressFamilyKey, RoutingInformationBase> ribs, RIBSide side, AddressFamilyKey afk) {
		RoutingInformationBase rib = null;

		if(!ribs.containsKey(afk)) {
			rib = ribProvider.get();
			rib.setAddressFamilyKey(afk);
			rib.setPeerName(peerName);
			rib.setSide(side);
			
			ribs.put(afk, rib);
		}
		
		return rib;
	}

	private boolean destroyRoutingInformationBase(Map<AddressFamilyKey, RoutingInformationBase> ribs, AddressFamilyKey afk) {
		boolean found = false;
		
		if((found = ribs.containsKey(afk))) {
			ribs.remove(afk).destroyRIB();
		}
		
		return found;
	}	
	
	/**
	 * @param peerName the peerName to set
	 */
	void setPeerName(String peerName) {
		this.peerName = peerName;
	}

	public void destroyAllRoutingInformationBases() {
		for(Entry<AddressFamilyKey, RoutingInformationBase> entry : localRIBs.entrySet())
			entry.getValue().destroyRIB();
		for(Entry<AddressFamilyKey, RoutingInformationBase> entry : remoteRIBs.entrySet())
			entry.getValue().destroyRIB();
		
		localRIBs.clear();
		remoteRIBs.clear();
	}
}