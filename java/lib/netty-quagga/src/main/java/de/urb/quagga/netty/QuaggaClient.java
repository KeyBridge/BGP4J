/**
 * 
 */
package de.urb.quagga.netty;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.bgp4j.weld.Config;
import org.bgp4j.weld.Configuration;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;


/**
 * @author rainer
 *
 */
public class QuaggaClient {
	private @Inject Logger log;
	private @Config @Inject Configuration config;
	
	private Channel clientChannel;
	private ChannelFactory channelFactory;

	private @Inject QuaggaChannelHandler quaggaChannelHander;
	private @Inject QuaggaPacketReframer quaggaPacketReframer;
	private @Inject QuaggaPacketDecoder quaggaPacketDecoder;
	private @Inject QuaggaPacketEncoder quaggaPacketEncoder;

	public void startClient() throws Exception {
		channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
		
		ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
		
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(
						quaggaPacketReframer,
						quaggaPacketDecoder,
						quaggaPacketEncoder,
						quaggaChannelHander
						);
			}
		});
		
		bootstrap.setOption("tcpnoDelay", true);
		bootstrap.setOption("keepAlive", true);
		
		boolean connected = false;
		
		while(!connected) {
			ChannelFuture future = bootstrap.connect(new InetSocketAddress(InetAddress.getByName("localhost"), config.getZebraPort()));

			try {
				future = future.await();
			} catch(InterruptedException e) {
				log.info("caught interrupt", e);
			}
			
			if(future.isDone()) {
				if(future.isSuccess()) {
					connected = true;
					this.clientChannel = future.getChannel();
				} else {
					log.warn("Cannot connect to zebra server", future.getCause());
					
					// sleep for one second and retry
					try {
						Thread.sleep(1000);
					} catch(InterruptedException e) {}
				}
			}
		}
		
	}
	
	public void stopClient() {
		if(clientChannel != null) {
			clientChannel.getCloseFuture().awaitUninterruptibly();
			this.clientChannel = null;
			
			channelFactory.releaseExternalResources();
			this.channelFactory = null;
		}
	}
	
	public void waitForChannelClose() {
		if(this.clientChannel != null) {
			this.clientChannel.getCloseFuture().awaitUninterruptibly();
		}
	}
}