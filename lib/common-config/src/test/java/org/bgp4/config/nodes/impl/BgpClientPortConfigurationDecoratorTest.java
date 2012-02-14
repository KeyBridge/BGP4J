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
 * File: org.bgp4.config.nodes.impl.BgpServerPortConfigurationDecoratorTest.java 
 */
package org.bgp4.config.nodes.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class BgpClientPortConfigurationDecoratorTest {

	@Test
	public void testDefaultPortGivenAddress() throws UnknownHostException {
		ClientConfigurationImpl impl = new ClientConfigurationImpl(InetAddress.getByName("192.168.4.1"));
		BgpClientPortConfigurationDecorator decorator = new BgpClientPortConfigurationDecorator(impl);
		
		Assert.assertEquals(179, decorator.getRemoteAddress().getPort());
		Assert.assertEquals(InetAddress.getByName("192.168.4.1"), decorator.getRemoteAddress().getAddress());
	}

	@Test
	public void testOtherPortNoAddress() throws Exception {
		ClientConfigurationImpl impl = new ClientConfigurationImpl(InetAddress.getByName("192.168.4.1"), 17179);
		BgpClientPortConfigurationDecorator decorator = new BgpClientPortConfigurationDecorator(impl);
		
		Assert.assertEquals(17179, decorator.getRemoteAddress().getPort());
		Assert.assertEquals(InetAddress.getByName("192.168.4.1"), decorator.getRemoteAddress().getAddress());
	}

	@Test
	public void testOtherPortGivenAddress() throws Exception {
		ClientConfigurationImpl impl = new ClientConfigurationImpl(InetAddress.getByName("192.168.4.1"), 17179);
		BgpClientPortConfigurationDecorator decorator = new BgpClientPortConfigurationDecorator(impl);
		
		Assert.assertEquals(17179, decorator.getRemoteAddress().getPort());
		Assert.assertEquals(InetAddress.getByName("192.168.4.1"), decorator.getRemoteAddress().getAddress());
	}
}
