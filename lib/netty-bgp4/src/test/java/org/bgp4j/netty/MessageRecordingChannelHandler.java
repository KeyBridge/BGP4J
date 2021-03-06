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
 */
package org.bgp4j.netty;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class MessageRecordingChannelHandler extends SimpleChannelHandler {
	@Inject Logger log;

	private Map<Channel, List<ChannelEvent>> events = new HashMap<Channel, List<ChannelEvent>>();
	private PeerConnectionInformation peerInfo;
	private Lock lock;
	private Condition condition;
	
	public MessageRecordingChannelHandler() {
		lock = new ReentrantLock();
		condition = lock.newCondition();
	}
	
	/* (non-Javadoc)
	 * @see org.jboss.netty.channel.SimpleChannelHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		Channel channel = ctx.getChannel();
		
		if(channel.getParent() != null && channel.getParent() instanceof ServerChannel)
			channel = channel.getParent();
		
		if(!events.containsKey(channel))
			events.put(channel, new LinkedList<ChannelEvent>());
		events.get(channel).add(e);
		
		lock.lock();
		try {
			condition.signal();
		} finally {
			lock.unlock();
		}
	}

	public boolean waitOnMessageReceived(int secondsToWait) throws InterruptedException  {
		lock.lock();
		
		try {
			return condition.await(secondsToWait, TimeUnit.SECONDS);
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * @return the events
	 */
	public List<ChannelEvent> eventsForChannel(Channel channel) {
		if(events.containsKey(channel))
			return events.get(channel);
		else
			return new LinkedList<ChannelEvent>();
	}

	@SuppressWarnings("unchecked")
	public <T extends ChannelEvent> T nextEvent(Channel channel) {
		if(!eventsForChannel(channel).isEmpty())
			return (T)eventsForChannel(channel).remove(0);
		else
			throw new IllegalStateException("empty event queue");
	}

	public int getWaitingEventNumber(Channel channel) {
		return eventsForChannel(channel).size();
	}

	/* (non-Javadoc)
	 * @see org.jboss.netty.channel.SimpleChannelHandler#childChannelOpen(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChildChannelStateEvent)
	 */
	@Override
	public void childChannelOpen(ChannelHandlerContext ctx,
			ChildChannelStateEvent e) throws Exception {
		// TODO Auto-generated method stub
		super.childChannelOpen(ctx, e);
	}

	/* (non-Javadoc)
	 * @see org.jboss.netty.channel.SimpleChannelHandler#channelOpen(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
	 */
	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		if(peerInfo != null) {
			Channel channel = ctx.getChannel();
			
			for(String name : channel.getPipeline().getNames()) {
				ChannelHandler handler = channel.getPipeline().get(name);
	
				if(handler.getClass().isAnnotationPresent(PeerConnectionInformationAware.class)) {
					channel.getPipeline().getContext(handler).setAttachment(peerInfo);
				}
			}
		}
	}

	/**
	 * @return the peerInfo
	 */
	public PeerConnectionInformation getPeerInfo() {
		return peerInfo;
	}

	/**
	 * @param peerInfo the peerInfo to set
	 */
	public void setPeerInfo(PeerConnectionInformation peerInfo) {
		this.peerInfo = peerInfo;
	}
}
