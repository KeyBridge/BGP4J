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
 * File: org.bgp4j.netty.handlers.BgpEventFireChannelFutureListener.java 
 */
package org.bgp4j.netty.handlers;

import java.util.LinkedList;
import java.util.List;

import org.bgp4j.net.events.BgpEvent;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class BgpEventFireChannelFutureListener implements ChannelFutureListener {

	private ChannelHandlerContext upstreamContext;
	private List<BgpEvent> bgpEvents = new LinkedList<BgpEvent>();
	
	BgpEventFireChannelFutureListener(ChannelHandlerContext upstreamContext) {
		this.upstreamContext = upstreamContext;
	}
	
	/* (non-Javadoc)
	 * @see org.jboss.netty.channel.ChannelFutureListener#operationComplete(org.jboss.netty.channel.ChannelFuture)
	 */
	@Override
	public void operationComplete(ChannelFuture future) throws Exception {
		if(upstreamContext != null && !bgpEvents.isEmpty()) {
			bgpEvents.forEach((n) ->  upstreamContext.fireUserEventTriggered(n));
		}
	}

	/**
	 * @param bgpEvent the bgpEvent to set
	 */
	public void addBgpEvent(BgpEvent bgpEvent) {
		this.bgpEvents.add(bgpEvent);
	}

}
