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
 * File: org.bgp4j.netty.fsm.InternalFSMTest.java 
 */
package org.bgp4j.netty.fsm;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Date;

import junit.framework.Assert;

import org.apache.commons.configuration.XMLConfiguration;
import org.bgp4j.config.Configuration;
import org.bgp4j.config.ConfigurationParser;
import org.bgp4j.netty.FSMState;
import org.bgp4j.weld.WeldTestCaseBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class InternalFSMTest extends WeldTestCaseBase {

	@Before
	public void before() {
		fsm = obtainInstance(InternalFSM.class);
		callbacks = mock(InternalFSMCallbacks.class);
		parser = obtainInstance(ConfigurationParser.class);
		anyChannelMatcher = new AnyFSMChannelMatcher();
		connectedBundle = new InternalFSMTestBundle(mock(FSMChannel.class), true);
		activeBundle = new InternalFSMTestBundle(mock(FSMChannel.class), false);
	}
	
	@After
	public void after() {
		fsm.destroyFSM();
		
		fsm = null;
		callbacks = null;
		parser = null;
		anyChannelMatcher = null;
		connectedBundle = null;
		activeBundle = null;
	}
	
	private InternalFSM fsm;
	private InternalFSMCallbacks callbacks;
	private ConfigurationParser parser;
	private AnyFSMChannelMatcher anyChannelMatcher;
	private InternalFSMTestBundle connectedBundle;
	private InternalFSMTestBundle activeBundle;

	@Test
	public void testManualStartEventInActiveMode() throws Exception {
		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer("peer1"), callbacks);

		Assert.assertEquals(FSMState.Idle, fsm.getState());
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());

		verify(callbacks).fireConnectRemotePeer();
		assertMachineInConnectState(false);
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
	}
	
	// TODO rethink these two unit tests. The behaviour has changed much in the actual code
//	@Test
//	public void testAutomaticStartEventInActiveModeWithoutEvent() throws Exception {
//		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer("peer1"), callbacks);
//
//		Assert.assertEquals(FSMState.Idle, fsm.getState());
//		Assert.assertTrue(fsm.isAutomaticStartRunning());
//		Assert.assertNotNull(fsm.getAutomaticStartDueWhen());
//		
//		conditionalSleep(fsm.getAutomaticStartDueWhen());
//		
//		verify(callbacks).fireConnectRemotePeer();
//		assertMachineInConnectState(false);
//		Assert.assertEquals(0, fsm.getConnectRetryCounter());
//	}
//
//	@Test
//	public void testAutomaticStartEventInPassiveModeWithoutEvent() throws Exception {
//		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer("peer2"), callbacks);
//
//		Assert.assertEquals(FSMState.Idle, fsm.getState());
//		Assert.assertTrue(fsm.isAutomaticStartRunning());
//		Assert.assertNotNull(fsm.getAutomaticStartDueWhen());
//		
//		conditionalSleep(fsm.getAutomaticStartDueWhen());
//		
//		verify(callbacks, never()).fireConnectRemotePeer();
//		Assert.assertEquals(0, fsm.getConnectRetryCounter());
//		assertMachineInActiveState(true, false);
//	}

	@Test
	public void testAutomaticStartEventInActiveMode() throws Exception {
		initializeFSMToConnectState("peer1");
		assertMachineInConnectState(false);
	}
	
	@Test
	public void testManualStartEventInPassiveMode() throws Exception {
		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer("peer2"), callbacks);

		Assert.assertEquals(FSMState.Idle, fsm.getState());
		fsm.handleEvent(FSMEvent.manualStart());
		
		verify(callbacks, never()).fireConnectRemotePeer();
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInActiveState(true, false);
	}
	
	@Test
	public void testAutomaticStartEventInPassiveMode() throws Exception {
		initializeFSMToActiveState("peer2");
		assertMachineInActiveState(true, false);
	}

	@Test
	public void testStartEventStopEvent() throws Exception {
		initializeFSMToConnectState("peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());

		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testStartEventInActiveModeConnectRetryTimerExpires() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleep(fsm.getConnectRetryTimerDueWhen());
		
		// verify(callbacks).fireDisconnectRemotePeer(argThat(nullMatcher));
		verify(callbacks, times(2)).fireConnectRemotePeer();
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInConnectState(false);		
	}

	@Test
	public void testStartEventInPassiveModeConnectRetryTimerExpires() throws Exception {
		initializeFSMToActiveState("peer2");

		conditionalSleep(fsm.getConnectRetryTimerDueWhen());

		verify(callbacks, never()).fireDisconnectRemotePeer(argThat(anyChannelMatcher)); // from connect retry timer expired handler
		verify(callbacks).fireConnectRemotePeer(); // from initial connect
		assertMachineInConnectState(false);
	}

	@Test
	public void testStartEventInActiveModeConnectStateTcpConnectionFailsNoPeerDampening() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionFails(null));

		assertMachineInIdleState(null, false);
	}

	@Test
	public void testStartEventInActiveModeConnectStateTcpConnectionFailsPeerDampening() throws Exception {
		initializeFSMToConnectState("peer3");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionFails(null));

		assertMachineInIdleState(null, true);
	}

	@Test
	public void testStartEventInActiveModeConnectionSuccessNoOpenDelay() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		assertMachineInOpenSentState(connectedBundle);
	}

	@Test
	public void testStartEventInActiveModeConnectionSuccessOpenDelay() throws Exception {
		initializeFSMToConnectState("peer4");
		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer("peer4"), callbacks);

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInConnectState(true);
		
		conditionalSleep(fsm.getDelayOpenTimerDueWhen());
		
		verify(callbacks).fireConnectRemotePeer();
		assertMachineInOpenSentState(connectedBundle);
	}

	@Test
	public void testStartEventInActiveModeConnectStateTcpConnectionFailsOpenDelay() throws Exception {
		initializeFSMToConnectState("peer4");
		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer("peer4"), callbacks);

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInConnectState(true);
		
		conditionalSleepShort(fsm.getDelayOpenTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		verify(callbacks).fireConnectRemotePeer();
		assertMachineInActiveState(true, false);
	}

	// -- Connect state transitions

//	@Test
//	public void testTransitionConnectByOpenEventOpenDelayTimerNotRunning() throws Exception {
//		initializeFSMToConnectState("peer1");
//		
//		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
//		fsm.handleEvent(FSMEvent.bgpOpen());
//		
//		Assert.assertEquals(1, fsm.getConnectRetryCounter());
//		assertMachineInIdleState(false);
//	}

	@Test
	public void testTransitionConnectOnConnectedChannelByOpenEventOpenDelayTimerRunningWithHoldTimer() throws Exception {
		initializeFSMToConnectState("peer4");

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInConnectState(true);

		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		assertMachineInOpenConfirmState(connectedBundle, true);
	}

	@Test
	public void testTransitionConnectOnActiveChannelByOpenEventOpenDelayTimerRunningWithHoldTimer() throws Exception {
		initializeFSMToConnectState("peer4");

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInConnectState(true);

		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		assertMachineInOpenConfirmState(activeBundle, true);
	}

	@Test
	public void testTransitionConnectOnConnectedChannelByOpenEventOpenDelayTimerRunningWithoutHoldTimer() throws Exception {
		initializeFSMToConnectState("peer5");

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInConnectState(true);

		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		assertMachineInOpenConfirmState(connectedBundle, false);
	}

	@Test
	public void testTransitionConnectOnActiveChannelByOpenEventOpenDelayTimerRunningWithoutHoldTimer() throws Exception {
		initializeFSMToConnectState("peer5");

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInConnectState(true);

		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));

		verify(callbacks).fireConnectRemotePeer();
		assertMachineInOpenConfirmState(activeBundle, false);
	}

	@Test
	public void testTransitionConnectByOpenMessageError() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByBgpHeaderError() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByNotifiyVersionError() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByAutomaticStop() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByHoldTimerExpires() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByKeepaliveTimerExpires() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByIdleHoldTimerExpires() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByNotifiyMessage() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByKeepaliveMessage() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByUpdateMessage() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionConnectByUpdateMessageError() throws Exception {
		initializeFSMToConnectState("peer1");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	// -- Active state transitions

	@Test
	public void testTransitionActiveByManualStop() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByDuplicatedConnectionConfirmedWithOpenDelayTimer() throws Exception {
		InternalFSMTestBundle secondActiveBundle = new InternalFSMTestBundle(mock(FSMChannel.class), false);
		
		initializeFSMToActiveState("peer7");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		conditionalSleepShort(fsm.getDelayOpenTimerDueWhen(), 50);
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(secondActiveBundle.getChannel()));
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());

		conditionalSleep(fsm.getDelayOpenTimerDueWhen());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(secondActiveBundle);
	}
	
	@Test
	public void testTransitionActiveByConnectionAckedWithOpenDelayTimer() throws Exception {
		initializeFSMToActiveState("peer7");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		conditionalSleepShort(fsm.getDelayOpenTimerDueWhen(), 50);
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));

		conditionalSleep(fsm.getDelayOpenTimerDueWhen());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(connectedBundle);
		verify(callbacks).fireSendOpenMessage(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionActiveByDelayOpenTimerExpires() throws Exception {
		initializeFSMToActiveState("peer7");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		
		conditionalSleep(fsm.getDelayOpenTimerDueWhen());
		// fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}
	
	@Test
	public void testTransitionActiveByTcpConnectionSuccessWithDelayOpenTimer() throws Exception {
		initializeFSMToActiveState("peer7");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInActiveState(false, true);
	}
	
	@Test
	public void testTransitionActiveByTcpConnectionSuccessWithoutDelayOpenTimer() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}
	
	@Test
	public void testTransitionActiveByTcpConnectionFailure() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionFails(null));
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}
		
	@Test
	public void testTransitionActiveByOpenEventOpenDelayTimerRunningWithHoldTimer() throws Exception {
		initializeFSMToActiveState("peer7");

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));

		verify(callbacks, never()).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInActiveState(false, true);

		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));

		verify(callbacks, never()).fireConnectRemotePeer();
		assertMachineInOpenConfirmState(activeBundle, true);
	}

	@Test
	public void testTransitionActiveByOpenEventOpenDelayTimerRunningWithoutHoldTimer() throws Exception {
		initializeFSMToActiveState("peer8");

		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));

		verify(callbacks, never()).fireConnectRemotePeer();
		verify(callbacks, never()).fireSendOpenMessage(argThat(anyChannelMatcher));
		assertMachineInActiveState(false, true);

		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));

		verify(callbacks, never()).fireConnectRemotePeer();
		assertMachineInOpenConfirmState(activeBundle, false);
	}

	@Test
	public void testTransitionActiveByOpenMessageError() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByBgpHeaderError() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByNotifiyVersionError() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByAutomaticStop() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByHoldTimerExpires() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByKeepaliveTimerExpires() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByIdleHoldTimerExpires() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByNotifiyMessage() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByKeepaliveMessage() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByUpdateMessage() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionActiveByUpdateMessageError() throws Exception {
		initializeFSMToActiveState("peer2");
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 50);
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	// -- OpenSent state transitions
	@Test
	public void testTransitionOnConnectedChannelOpenSentByAutomaticStart() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(connectedBundle);
	}	
	
	@Test
	public void testTransitionOnActiveChannelOpenSentByAutomaticStart() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}	
	
	@Test
	public void testTransitionOnConnectedChannelOpenSentByManualStart() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(connectedBundle);
	}	
	
	@Test
	public void testTransitionOnActiveChannelOpenSentByManualStart() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}	
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByAutomaticStop() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByAutomaticStop() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByManualStop() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByManualStop() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		verify(callbacks).fireSendHoldTimerExpiredNotification(connectedBundle.getMatcherArg());
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		verify(callbacks).fireSendHoldTimerExpiredNotification(activeBundle.getMatcherArg());
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByTcpConnectionFails() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInActiveState(true, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByTcpConnectionFails() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInActiveState(true, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByBgpOpen() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInOpenConfirmState(connectedBundle, true);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByBgpOpen() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInOpenConfirmState(activeBundle, true);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByBgpHeaderError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByBgpHeaderError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByNofiticationVersionError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByNofiticationVersionError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByConnectRetryTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByConnectRetryTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByNotifyMessage() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByNotifyMessage() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByKeepaliveMessage() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByKeepaliveMessage() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByUpdateMessage() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelByUpdateMessage() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelByUpdateMessageError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelByUpdateMessageError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}

	// -- Open Sent state transitions with second connection	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByBgpOpenLowerLocalBgpIdentifier() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		verify(callbacks).fireSendCeaseNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
		assertMachineInOpenConfirm(activeBundle, true, 1, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByBgpOpenHigherBgpIdentifier() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer9");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInOpenConfirmState(connectedBundle, true);
		verify(callbacks).fireSendCeaseNotification(activeBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByBgpOpenLowerLocalBgpIdentifier() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInOpenConfirmState(activeBundle, true);
		verify(callbacks).fireSendCeaseNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByBgpOpenHigherBgpIdentifier() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer9");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		verify(callbacks).fireSendCeaseNotification(activeBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
		// assertMachineInOpenConfirm(connectedBundle, true);
		assertMachineInOpenConfirm(connectedBundle, true, 1, false);
	}

	@Test
	public void testTransitionOnConnectedChannelOpenSentTcpConnectionConfirmedByAutomaticStart() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(connectedBundle);
	}	
	
	@Test
	public void testTransitionOnActiveChannelOpenSentTcpConnectionRequestAckedByAutomaticStart() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}	
	
	@Test
	public void testTransitionOnConnectedChannelOpenSentTcpConnectionConfirmedByManualStart() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(connectedBundle);
	}	
	
	@Test
	public void testTransitionOnActiveChannelOpenSentTcpConnectionRequestAckedByManualStart() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}	
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByAutomaticStop() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByAutomaticStop() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByManualStop() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByManualStop() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		verify(callbacks).fireSendHoldTimerExpiredNotification(connectedBundle.getMatcherArg());
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		verify(callbacks).fireSendHoldTimerExpiredNotification(activeBundle.getMatcherArg());
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByConnectedTcpConnectionFails() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInActiveState(true, false);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByActiveTcpConnectionFails() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(connectedBundle);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByConnectedTcpConnectionFails() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(activeBundle);
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByActiveTcpConnectionFails() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
//		assertMachineInActiveState(true, false);
//		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
		assertMachineInConnectState(false);
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByBgpHeaderError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByBgpHeaderError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByNofiticationVersionError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getChannel());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionConfirmedByNofiticationVersionError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByConnectRetryTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByConnectRetryTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByNotifyMessage() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByNotifyMessage() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByKeepaliveMessage() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByKeepaliveMessage() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByUpdateMessage() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByUpdateMessage() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenSentOnConnectedChannelTcpConnectionConfirmedByUpdateMessageError() throws Exception {
		initializeFSMToOpenSentState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenSentOnActiveChannelTcpConnectionRequestAckedByUpdateMessageError() throws Exception {
		initializeFSMToOpenSentState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	// -- Open Confirm state transitions
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByAutomaticStart() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirm(connectedBundle, true, 1);
	}	
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByAutomaticStart() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirm(activeBundle, true, 1);
	}	
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByManualStart() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirm(connectedBundle, true, 1);
	}	
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByManualStart() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirm(activeBundle, true, 1);
	}	
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByAutomaticStop() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByAutomaticStop() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByManualStop() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByManualStop() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendHoldTimerExpiredNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendHoldTimerExpiredNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByTcpConnectionFails() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByTcpConnectionFails() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(null, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByBgpOpen() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByBgpOpen() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByBgpHeaderError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByBgpHeaderError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByNofiticationVersionError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByNofiticationVersionError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByConnectTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByConnectTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirm(connectedBundle, true, 2);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirm(activeBundle, true, 2);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnactiveChannelByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByNotifyMessage() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByNotifyMessage() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByKeepaliveMessage() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(connectedBundle, true);
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByKeepaliveMessage() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(activeBundle, true);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByUpdateMessage() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelByUpdateMessage() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelByUpdateMessageError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelByUpdateMessageError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}

	// -- Open Confirm with two connections
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByBgpOpenLowerLocalBgpIdentifier() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		verify(callbacks).fireSendCeaseNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
		assertMachineInOpenConfirm(activeBundle, true, 0, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByBgpOpenHigherBgpIdentifier() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer9");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInOpenConfirmState(connectedBundle, true);
		verify(callbacks).fireSendCeaseNotification(activeBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByBgpOpenLowerLocalBgpIdentifier() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInOpenConfirmState(activeBundle, true);
		verify(callbacks).fireSendCeaseNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByBgpOpenHigherBgpIdentifier() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer9");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		verify(callbacks).fireSendCeaseNotification(activeBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
		// assertMachineInOpenConfirm(connectedBundle, true);
		assertMachineInOpenConfirm(activeBundle, true, 1, false);
	}

	@Test
	public void testTransitionOnConnectedChannelOpenConfirmTcpConnectionConfirmedByAutomaticStart() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(connectedBundle, true);
	}	
	
	@Test
	public void testTransitionOnActiveChannelOpenConfirmTcpConnectionRequestAckedByAutomaticStart() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(activeBundle, true);
	}	
	
	@Test
	public void testTransitionOnConnectedChannelOpenConfirmTcpConnectionConfirmedByManualStart() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(connectedBundle, true);
	}	
	
	@Test
	public void testTransitionOnActiveChannelOpenConfirmTcpConnectionRequestAckedByManualStart() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(activeBundle, true);
	}	
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByAutomaticStop() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByAutomaticStop() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByManualStop() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByManualStop() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		verify(callbacks).fireSendHoldTimerExpiredNotification(connectedBundle.getMatcherArg());
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		verify(callbacks).fireSendHoldTimerExpiredNotification(activeBundle.getMatcherArg());
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByConnectedTcpConnectionFails() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleStateWithoutDisconnect(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByActiveTcpConnectionFails() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");

		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(connectedBundle, true);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByConnectedTcpConnectionFails() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(activeBundle, true);
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByActiveTcpConnectionFails() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleStateWithoutDisconnect(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByBgpHeaderError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByBgpHeaderError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByBgpOpenMessageError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByNofiticationVersionError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getChannel());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionConfirmedByNofiticationVersionError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByConnectRetryTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByConnectRetryTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());
		
		assertMachineInOpenConfirm(connectedBundle, true, 2);
		verify(callbacks, never()).fireSendKeepaliveMessage(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByKeepaliveTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		assertMachineInOpenConfirm(activeBundle, true, 2);
		verify(callbacks, never()).fireSendKeepaliveMessage(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByDelayOpenTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByIdleHoldTimerExpires() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByNotifyMessage() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByNotifyMessage() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByKeepaliveMessage() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByKeepaliveMessage() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		assertMachineInEstablishedState(activeBundle, true);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByUpdateMessage() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByUpdateMessage() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionOpenConfirmOnConnectedChannelTcpConnectionConfirmedByUpdateMessageError() throws Exception {
		initializeFSMToOpenConfirmState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionOpenConfirmOnActiveChannelTcpConnectionRequestAckedByUpdateMessageError() throws Exception {
		initializeFSMToOpenConfirmState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	// -- Established state transitions
	@Test
	public void testTransitionEstablishedOnConnectedChannelByAutomaticStart() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
	}	
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByAutomaticStart() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
	}	
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByManualStart() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
	}	
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByManualStart() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
	}	
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByAutomaticStop() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByAutomaticStop() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByManualStop() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByManualStop() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendHoldTimerExpiredNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendHoldTimerExpiredNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByTcpConnectionFails() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByTcpConnectionFails() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByBgpOpen() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByBgpOpen() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByBgpHeaderError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByBgpHeaderError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByBgpOpenMessageError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByBgpOpenMessageError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByNofiticationVersionError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByNofiticationVersionError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByConnectTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByConnectTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByKeepaliveTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true, 2);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByKeepaliveTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true, 2);
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelByDelayOpenTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByDelayOpenTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByIdleHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByIdleHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByNotifyMessage() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByNotifyMessage() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByKeepaliveMessage() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(connectedBundle, true);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByKeepaliveMessage() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(activeBundle, true);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByUpdateMessage() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelByUpdateMessage() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelByUpdateMessageError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendUpdateErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelByUpdateMessageError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendUpdateErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
	}

	// -- Established state transitions with two connections
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByAutomaticStart() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}	
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByAutomaticStart() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}	
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByManualStart() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}	
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByManualStart() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStart());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}	
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByAutomaticStop() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByAutomaticStop() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.automaticStop());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByManualStop() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByManualStop() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.manualStop());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendHoldTimerExpiredNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.holdTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendHoldTimerExpiredNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByTcpConnectionFails() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByTcpConnectionFails() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByTcpConnectionFailsOnSecondConnection() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(activeBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByTcpConnectionFailsOnSecondConnection() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.tcpConnectionFails(connectedBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByBgpOpenOnEstablishedChannel() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByBgpOpenOnEstablishedChannel() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByBgpOpenOnSecondChannelLowerLocalBgpIdentifier() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireSendCeaseNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByBgpOpenOnSecondChannelLowerLocalBgpIdentifier() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireSendCeaseNotification(connectedBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByBgpOpenOnSecondChannelHigherLocalBgpIdentifier() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer9");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(activeBundle.getChannel()));
		
		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks).fireSendCeaseNotification(activeBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByBgpOpenOnSecondChannelHigherLocalBgpIdentifier() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer9");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpen(connectedBundle.getChannel()));
		
		assertMachineInEstablishedState(connectedBundle, true, 0);
		verify(callbacks).fireSendCeaseNotification(activeBundle.getMatcherArg());
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByBgpHeaderError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByBgpHeaderError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpHeaderError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByBgpOpenMessageError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByBgpOpenMessageError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.bgpOpenMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByNofiticationVersionError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByNofiticationVersionError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessageVersionError());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByConnectTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByConnectTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.connectRetryTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByKeepaliveTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true, 2);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByKeepaliveTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepaliveTimerExpires());

		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true, 2);
		verify(callbacks, never()).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByDelayOpenTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByDelayOpenTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.delayOpenTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByIdleHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByIdleHoldTimerExpires() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.idleHoldTimerExpires());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByNotifyMessage() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByNotifyMessage() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.notifyMessage());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendInternalErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByKeepaliveMessage() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByKeepaliveMessage() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.keepAliveMessage());

		assertMachineInEstablishedState(activeBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByUpdateMessage() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(connectedBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByUpdateMessage() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessage());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(activeBundle, true);
		verify(callbacks, never()).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}
	
	@Test
	public void testTransitionEstablishedOnConnectedChannelTcpConnectionConfirmedByUpdateMessageError() throws Exception {
		initializeFSMToEstablishedState(connectedBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(activeBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendUpdateErrorNotification(connectedBundle.getMatcherArg());
		assertMachineInIdleState(connectedBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(activeBundle.getMatcherArg());
	}

	@Test
	public void testTransitionEstablishedOnActiveChannelTcpConnectionRequestAckedByUpdateMessageError() throws Exception {
		initializeFSMToEstablishedState(activeBundle, "peer1");
		
		fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(connectedBundle.getChannel()));
		fsm.handleEvent(FSMEvent.updateMessageError());
		
		Assert.assertEquals(1, fsm.getConnectRetryCounter());
		verify(callbacks).fireSendUpdateErrorNotification(activeBundle.getMatcherArg());
		assertMachineInIdleState(activeBundle, false);
		verify(callbacks).fireDisconnectRemotePeer(connectedBundle.getMatcherArg());
	}

	// -- end of test messages
	private Configuration loadConfiguration(String fileName) throws Exception {
		return parser.parseConfiguration(new XMLConfiguration(fileName));
	}
	
	private void conditionalSleep(Date untilWhen) throws InterruptedException {
		long sleep = (untilWhen.getTime() - System.currentTimeMillis()) + 1000L;
		
		if(sleep > 0)
			Thread.sleep(sleep);
	}

	/**
	 * Sleep a fractional time of the interval between now and the given date.
	 * 
	 * @param untilWhen the date to sleep until if the fraction is 100
	 * @param fraction the fractional value as percentage between 0 and 100
	 * 
	 * @throws InterruptedException
	 */
	private void conditionalSleepShort(Date untilWhen, long fraction) throws InterruptedException {
		long sleep = (((untilWhen.getTime() - System.currentTimeMillis()) * fraction) / 100L);
		
		if(sleep > 0)
			Thread.sleep(sleep);
	}
	
	/**
	 * Configure the machine and bring it to the connect state.
	 * 
	 * @param peerName
	 * @throws Exception
	 */
	private void initializeFSMToConnectState(String peerName) throws Exception {
		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer(peerName), callbacks);

		Assert.assertEquals(FSMState.Idle, fsm.getState());
		fsm.handleEvent(FSMEvent.automaticStart());
		
		verify(callbacks).fireConnectRemotePeer();
		Assert.assertEquals(0, fsm.getConnectRetryCounter());

		assertMachineInConnectState(false);
	}
	
	/**
	 * Configure the machine and bring it to the connect state.
	 * 
	 * @param peerName
	 * @throws Exception
	 */
	private void initializeFSMToOpenSentState(InternalFSMTestBundle testBundle, String peerName) throws Exception {
		initializeFSMToConnectState(peerName);
		
		conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 25);
		if(testBundle.isTcpConnectionAck())
			fsm.handleEvent(FSMEvent.tcpConnectionRequestAcked(testBundle.getChannel()));
		else
			fsm.handleEvent(FSMEvent.tcpConnectionConfirmed(testBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenSentState(testBundle);

	}
	
	/**
	 * Configure the machine and bring it to the connect state.
	 * 
	 * @param peerName
	 * @throws Exception
	 */
	private void initializeFSMToOpenConfirmState(InternalFSMTestBundle testBundle, String peerName) throws Exception {
		initializeFSMToOpenSentState(testBundle, peerName);
		
		// conditionalSleepShort(fsm.getConnectRetryTimerDueWhen(), 25);
		Thread.sleep(1000);
		
		fsm.setPeerProposedHoldTime(10);
		fsm.handleEvent(FSMEvent.bgpOpen(testBundle.getChannel()));
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInOpenConfirmState(testBundle, true);

	}

	/**
	 * Configure the machine and bring it to the connect state.
	 * 
	 * @param peerName
	 * @throws Exception
	 */
	private void initializeFSMToEstablishedState(InternalFSMTestBundle testBundle, String peerName) throws Exception {
		initializeFSMToOpenConfirmState(testBundle, peerName);
		
		Thread.sleep(1000);
		
		fsm.handleEvent(FSMEvent.keepAliveMessage());
		
		Assert.assertEquals(0, fsm.getConnectRetryCounter());
		assertMachineInEstablishedState(testBundle, true);
	}

	/**
	 * Configure the machine and bring it to the active state.
	 * 
	 * @param peerName
	 * @throws Exception
	 */
	private void initializeFSMToActiveState(String peerName) throws Exception {
		fsm.setup(loadConfiguration("org/bgp4j/netty/fsm/Config-With-BgpPeers.xml").getPeer(peerName), callbacks);

		Assert.assertEquals(FSMState.Idle, fsm.getState());
		fsm.handleEvent(FSMEvent.automaticStart());
		
		verify(callbacks,never()).fireConnectRemotePeer();
		Assert.assertEquals(0, fsm.getConnectRetryCounter());

		assertMachineInActiveState(true, false);
	}
	
	/**
	 * check if the machine is in connect state and that the timer are in the following states:
	 * <ul>
	 * <li><b>Connect retry timer:</b> Running if the delay open timer must be not running, not running if the open dealy timer must be running 
	 * <li><b>Delay open timer:</b> Conditionally checked
	 * <li><b>Idle hold timer:</b>Not running
	 * <li><b>hold timer:</b>Not running
	 * <li><b>Keepalive timer:</b>Not running
	 * </ul>
	 * This method does not check the connect retry counter because it is irrelevant for the machine to transition to the idle state.
	 * @throws Exception
	 */
	private void assertMachineInConnectState(boolean mustHaveOpenDelayTimer) throws Exception {
		Assert.assertEquals(FSMState.Connect, fsm.getState());
		
		if(mustHaveOpenDelayTimer) {
			Assert.assertFalse(fsm.isConnectRetryTimerRunning());
			Assert.assertNull(fsm.getConnectRetryTimerDueWhen());		
			Assert.assertTrue(fsm.isDelayOpenTimerRunning());
			Assert.assertNotNull(fsm.getDelayOpenTimerDueWhen());
		} else {
			Assert.assertTrue(fsm.isConnectRetryTimerRunning());
			Assert.assertNotNull(fsm.getConnectRetryTimerDueWhen());		
			Assert.assertFalse(fsm.isDelayOpenTimerRunning());
			Assert.assertNull(fsm.getDelayOpenTimerDueWhen());			
		}
		
		Assert.assertFalse(fsm.isHoldTimerRunning());
		Assert.assertNull(fsm.getIdleHoldTimerDueWhen());
		
		Assert.assertFalse(fsm.isHoldTimerRunning());
		Assert.assertNull(fsm.getHoldTimerDueWhen());
		
		Assert.assertFalse(fsm.isKeepaliveTimerRunning());
		Assert.assertNull(fsm.getKeepaliveTimerDueWhen());
	}

	/**
	 * check if the machine is in active state and that the timer are in the following states:
	 * <ul>
	 * <li><b>Connect retry timer:</b> Running
	 * <li><b>Delay open timer:</b> Conditionally checked
	 * <li><b>Idle hold timer:</b>Not running
	 * <li><b>Hold timer:</b>Not running
	 * <li><b>Keepalive timer:</b>Not running
	 * </ul>
	 * This method does not check the connect retry counter because it is irrelevant for the machine to transition to the active state.
	 * @throws Exception
	 */
	private void assertMachineInActiveState(boolean mustHaveConnectRetryTimer, boolean mustHaveOpenDelayTimer) throws Exception {
		Assert.assertEquals(FSMState.Active, fsm.getState());
		if(mustHaveConnectRetryTimer) {
			Assert.assertTrue(fsm.isConnectRetryTimerRunning());
			Assert.assertNotNull(fsm.getConnectRetryTimerDueWhen());
		} else {
			Assert.assertFalse(fsm.isConnectRetryTimerRunning());
			Assert.assertNull(fsm.getConnectRetryTimerDueWhen());			
		}
		
		if(mustHaveOpenDelayTimer) {
			Assert.assertTrue(fsm.isDelayOpenTimerRunning());
			Assert.assertNotNull(fsm.getDelayOpenTimerDueWhen());
		} else {
			Assert.assertFalse(fsm.isDelayOpenTimerRunning());
			Assert.assertNull(fsm.getDelayOpenTimerDueWhen());			
		}
		
		Assert.assertFalse(fsm.isHoldTimerRunning());
		Assert.assertNull(fsm.getIdleHoldTimerDueWhen());
		
		Assert.assertFalse(fsm.isHoldTimerRunning());
		Assert.assertNull(fsm.getHoldTimerDueWhen());
		
		Assert.assertFalse(fsm.isKeepaliveTimerRunning());
		Assert.assertNull(fsm.getKeepaliveTimerDueWhen());
	}

	/**
	 * check if the machine is in active state and that the timer are in the following states:
	 * <ul>
	 * <li><b>Connect retry timer:</b> Not running
	 * <li><b>Delay open timer:</b> Not running
	 * <li><b>Idle hold timer:</b>Not running
	 * <li><b>Hold timer:</b>Running
	 * <li><b>Keepalive timer:</b>Not running
	 * </ul>
	 * This method does not check the connect retry counter because it is irrelevant for the machine to transition to the active state.
	 * @throws Exception
	 */
	private void assertMachineInOpenSentState(InternalFSMTestBundle testBundle) throws Exception {
		Assert.assertEquals(FSMState.OpenSent, fsm.getState());
		Assert.assertFalse(fsm.isConnectRetryTimerRunning());
		Assert.assertNull(fsm.getConnectRetryTimerDueWhen());		
		Assert.assertFalse(fsm.isDelayOpenTimerRunning());
		Assert.assertNull(fsm.getDelayOpenTimerDueWhen());			
		Assert.assertTrue(fsm.isHoldTimerRunning());
		Assert.assertNotNull(fsm.getHoldTimerDueWhen());		
		Assert.assertFalse(fsm.isIdleHoldTimerRunning());
		Assert.assertNull(fsm.getIdleHoldTimerDueWhen());		
		Assert.assertFalse(fsm.isKeepaliveTimerRunning());
		Assert.assertNull(fsm.getKeepaliveTimerDueWhen());
		
		verify(callbacks, atLeastOnce()).fireSendOpenMessage(testBundle.getMatcherArg());			
		verify(callbacks, atLeastOnce()).fireCompleteBGPLocalInitialization();
	}

	/**
	 * check if the machine is in active state and that the timer are in the following states:
	 * <ul>
	 * <li><b>Connect retry timer:</b> Not running
	 * <li><b>Delay open timer:</b> Not running
	 * <li><b>Idle hold timer:</b>Not running
	 * <li><b>Hold timer:</b>Conditionally checked
	 * <li><b>Keepalive timer:</b>Conditonally checked
	 * </ul>
	 * This method does not check the connect retry counter because it is irrelevant for the machine to transition to the active state.
	 * @throws Exception
	 */
	private void assertMachineInOpenConfirm(InternalFSMTestBundle testBundle, boolean mustHaveHoldAndKeepaliveTimer, 
			int numberOfKeepalivesSent, boolean mustHaveSentOpen) throws Exception {
		Assert.assertEquals(FSMState.OpenConfirm, fsm.getState());
		Assert.assertFalse(fsm.isConnectRetryTimerRunning());
		Assert.assertNull(fsm.getConnectRetryTimerDueWhen());		
		Assert.assertFalse(fsm.isDelayOpenTimerRunning());
		Assert.assertNull(fsm.getDelayOpenTimerDueWhen());			
		Assert.assertFalse(fsm.isIdleHoldTimerRunning());
		Assert.assertNull(fsm.getIdleHoldTimerDueWhen());		
		
		if(mustHaveHoldAndKeepaliveTimer) {
			Assert.assertTrue(fsm.isHoldTimerRunning());
			Assert.assertNotNull(fsm.getHoldTimerDueWhen());
			Assert.assertTrue(fsm.isKeepaliveTimerRunning());
			Assert.assertNotNull(fsm.getKeepaliveTimerDueWhen());
		} else {
			Assert.assertFalse(fsm.isHoldTimerRunning());
			Assert.assertNull(fsm.getHoldTimerDueWhen());
			Assert.assertFalse(fsm.isKeepaliveTimerRunning());
			Assert.assertNull(fsm.getKeepaliveTimerDueWhen());
		}

		if(mustHaveSentOpen)
			verify(callbacks).fireSendOpenMessage(testBundle.getMatcherArg());
		if(numberOfKeepalivesSent > 0)
			verify(callbacks, times(numberOfKeepalivesSent)).fireSendKeepaliveMessage(testBundle.getMatcherArg());
		else
			verify(callbacks, never()).fireSendKeepaliveMessage(testBundle.getMatcherArg());
		verify(callbacks).fireCompleteBGPLocalInitialization();
		verify(callbacks).fireCompleteBGPPeerInitialization();
	}

	private void assertMachineInOpenConfirmState(InternalFSMTestBundle testBundle, boolean mustHaveHoldAndKeepaliveTimer) throws Exception {
		assertMachineInOpenConfirm(testBundle, mustHaveHoldAndKeepaliveTimer, 1, true);
	}

	private void assertMachineInOpenConfirm(InternalFSMTestBundle testBundle, boolean mustHaveHoldAndKeepaliveTimer, int numberOfKeepalivesSent) throws Exception {
		assertMachineInOpenConfirm(testBundle, mustHaveHoldAndKeepaliveTimer, numberOfKeepalivesSent, true);
	}

	/**
	 * check if the machine is in active state and that the timer are in the following states:
	 * <ul>
	 * <li><b>Connect retry timer:</b> Not running
	 * <li><b>Delay open timer:</b> Not running
	 * <li><b>Idle hold timer:</b>Not running
	 * <li><b>Hold timer:</b>Conditionally checked
	 * <li><b>Keepalive timer:</b>Conditonally checked
	 * </ul>
	 * This method does not check the connect retry counter because it is irrelevant for the machine to transition to the active state.
	 * @throws Exception
	 */
	private void assertMachineInEstablishedState(InternalFSMTestBundle testBundle, boolean mustHaveHoldAndKeepaliveTimer, int numberOfKeepalivesSent) throws Exception {
		Assert.assertEquals(FSMState.Established, fsm.getState());
		Assert.assertFalse(fsm.isConnectRetryTimerRunning());
		Assert.assertNull(fsm.getConnectRetryTimerDueWhen());		
		Assert.assertFalse(fsm.isDelayOpenTimerRunning());
		Assert.assertNull(fsm.getDelayOpenTimerDueWhen());			
		Assert.assertFalse(fsm.isIdleHoldTimerRunning());
		Assert.assertNull(fsm.getIdleHoldTimerDueWhen());		
		
		if(mustHaveHoldAndKeepaliveTimer) {
			Assert.assertTrue(fsm.isHoldTimerRunning());
			Assert.assertNotNull(fsm.getHoldTimerDueWhen());
			Assert.assertTrue(fsm.isKeepaliveTimerRunning());
			Assert.assertNotNull(fsm.getKeepaliveTimerDueWhen());
		} else {
			Assert.assertFalse(fsm.isHoldTimerRunning());
			Assert.assertNull(fsm.getHoldTimerDueWhen());
			Assert.assertFalse(fsm.isKeepaliveTimerRunning());
			Assert.assertNull(fsm.getKeepaliveTimerDueWhen());
		}

		verify(callbacks, times(numberOfKeepalivesSent)).fireSendKeepaliveMessage(testBundle.getMatcherArg());
		verify(callbacks).fireEstablished();
	}

	private void assertMachineInEstablishedState(InternalFSMTestBundle testBundle, boolean mustHaveHoldAndKeepaliveTimer) throws Exception {
		assertMachineInEstablishedState(testBundle, mustHaveHoldAndKeepaliveTimer, 1);
	}
	
	/**
	 * check if the machine is in idle state and that the timers are in the following state
	 * <ul>
	 * <li><b>Connect retry timer:</b> Not running
	 * <li><b>Delay open timer:</b> Not running
	 * <li><b>Idle hold timer:</b>Condintially checked
	 * <li><b>Hold timer:</b>Not running
	 * <li><b>Keepalive timer:</b>Not running
	 * </ul>
	 * This method does not check the connect retry counter because it is irrelevant for the machine to transition to the active state.

	 * @throws Exception
	 */
	private void assertMachineInIdleState(InternalFSMTestBundle testBundle, boolean musthaveIdleHoldTimer) throws Exception  {
		Assert.assertEquals(FSMState.Idle, fsm.getState());
		
		Assert.assertFalse(fsm.isConnectRetryTimerRunning());
		Assert.assertNull(fsm.getConnectRetryTimerDueWhen());
		Assert.assertFalse(fsm.isHoldTimerRunning());
		Assert.assertNull(fsm.getHoldTimerDueWhen());
		Assert.assertFalse(fsm.isDelayOpenTimerRunning());
		Assert.assertNull(fsm.getDelayOpenTimerDueWhen());
		Assert.assertFalse(fsm.isKeepaliveTimerRunning());
		Assert.assertNull(fsm.getKeepaliveTimerDueWhen());

		if(musthaveIdleHoldTimer) {
			Assert.assertTrue(fsm.isIdleHoldTimerRunning());
			Assert.assertNotNull(fsm.getIdleHoldTimerDueWhen());
		} else {
			Assert.assertFalse(fsm.isIdleHoldTimerRunning());
			Assert.assertNull(fsm.getIdleHoldTimerDueWhen());			
		}
		
		if(testBundle != null)
			verify(callbacks, atLeastOnce()).fireDisconnectRemotePeer(testBundle.getMatcherArg());
		else
			verify(callbacks, never()).fireDisconnectRemotePeer(argThat(anyChannelMatcher));
		
		verify(callbacks, atLeastOnce()).fireReleaseBGPResources();		
	}

	private void assertMachineInIdleStateWithoutDisconnect(InternalFSMTestBundle testBundle, boolean musthaveIdleHoldTimer) throws Exception  {
		Assert.assertEquals(FSMState.Idle, fsm.getState());
		
		Assert.assertFalse(fsm.isConnectRetryTimerRunning());
		Assert.assertNull(fsm.getConnectRetryTimerDueWhen());
		Assert.assertFalse(fsm.isHoldTimerRunning());
		Assert.assertNull(fsm.getHoldTimerDueWhen());
		Assert.assertFalse(fsm.isDelayOpenTimerRunning());
		Assert.assertNull(fsm.getDelayOpenTimerDueWhen());
		Assert.assertFalse(fsm.isKeepaliveTimerRunning());
		Assert.assertNull(fsm.getKeepaliveTimerDueWhen());

		if(musthaveIdleHoldTimer) {
			Assert.assertTrue(fsm.isIdleHoldTimerRunning());
			Assert.assertNotNull(fsm.getIdleHoldTimerDueWhen());
		} else {
			Assert.assertFalse(fsm.isIdleHoldTimerRunning());
			Assert.assertNull(fsm.getIdleHoldTimerDueWhen());			
		}
		
		verify(callbacks, never()).fireDisconnectRemotePeer(testBundle.getMatcherArg());
		
		verify(callbacks, atLeastOnce()).fireReleaseBGPResources();		
	}
}
