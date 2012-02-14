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
 * File: org.bgp4.config.nodes.ServerConfigurationDecorator.java 
 */
package org.bgp4.config.nodes;

import java.net.InetSocketAddress;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public abstract class ClientPortConfigurationDecorator implements ClientConfiguration {

	private ClientConfiguration decorated;
	
	protected ClientPortConfigurationDecorator(ClientConfiguration decorated) {
		this.decorated = decorated;
	}
	
	/* (non-Javadoc)
	 * @see org.bgp4.config.nodes.ServerConfiguration#getListenAddress()
	 */
	@Override
	public InetSocketAddress getRemoteAddress() {
		InetSocketAddress sockAddr = decorated.getRemoteAddress();
		
		
		if(sockAddr.getPort() == 0)
			sockAddr = new InetSocketAddress(sockAddr.getAddress(), getDefaultPort());
		
		return sockAddr;
	}

	/**
	 * obtain the default port
	 * @return
	 */
	protected abstract int getDefaultPort();
}
