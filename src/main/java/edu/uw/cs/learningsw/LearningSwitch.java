package edu.uw.cs.learningsw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.rmi.runtime.Log;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

public class LearningSwitch implements IOFMessageListener, IFloodlightModule {
	
	protected IFloodlightProviderService floodlightProvider;
	protected Map<Long, Short> macToPort;
	protected static Logger logger;
	
	// 0 - NOTHING, 1 - HUB, 2 - LEARNING_SWITCH_WO_RULES, 3 - LEARNING_SWITCH_WITH_RULES
	protected static int CTRL_LEVEL = 3;
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 20; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0;

	@Override
	public String getName() {
		return LearningSwitch.class.getName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		macToPort = new HashMap<Long, Short>();
		logger = LoggerFactory.getLogger(LearningSwitch.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		logger.info("Learning Switch starts");
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}
	
	private void pushPacket(IOFSwitch sw, OFPacketIn pi, short outPort) {
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                .getMessage(OFType.PACKET_OUT);
        po.setBufferId(pi.getBufferId());
        po.setInPort(pi.getInPort());

        logger.debug("out port: {}", outPort);
        // set actions
        OFActionOutput action = new OFActionOutput().setPort(outPort);
        po.setActions(Collections.singletonList((OFAction)action));
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set data if is is included in the packetin
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength() + packetData.length));
            po.setPacketData(packetData);
        } else {
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength()));
        }
        try {
            sw.write(po, null);
        } catch (IOException e) {
            logger.error("Failure writing PacketOut", e);
        }
	}
	
	private Command ctrlLogicSwitchWithRules(IOFSwitch sw, OFPacketIn pi) {
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac = Ethernet.toLong(match.getDataLayerDestination());
		
		Short inPort = pi.getInPort();
		
		if (!macToPort.containsKey(sourceMac)) {
			macToPort.put(sourceMac, inPort);
			logger.debug("mac {} from port {}", HexString.toHexString(sourceMac), inPort);
		}
		
		Short outPort = macToPort.get(destMac);
		if (outPort == null) {
			pushPacket(sw, pi, (short) OFPort.OFPP_FLOOD.getValue());
		} else {
			OFFlowMod rule = (OFFlowMod) floodlightProvider.getOFMessageFactory()
					.getMessage(OFType.FLOW_MOD);
			match.setWildcards(~OFMatch.OFPFW_DL_DST);
			rule.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE)
				.setCommand(OFFlowMod.OFPFC_ADD)
				.setMatch(match);
			
			List<OFAction> actions = new ArrayList<OFAction>();
			actions.add(new OFActionOutput(outPort));
			rule.setActions(actions);
			
			rule.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
			
			logger.info("install rule fore destination {}", HexString.toHexString(destMac));
			
			try {
				sw.write(rule, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			pushPacket(sw, pi, outPort);
		}
		
		return Command.CONTINUE;
	}
	
	private Command ctrlLogicSwitchWithoutRules(IOFSwitch sw, OFPacketIn pi) {
		
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac = Ethernet.toLong(match.getDataLayerDestination());
		
		Short inPort = pi.getInPort();
		
		if (!macToPort.containsKey(sourceMac)) {
			macToPort.put(sourceMac, inPort);
			logger.debug("mac {} from port {}", HexString.toHexString(sourceMac), inPort);
		}
		
		Short outPort = macToPort.get(destMac);
		pushPacket(sw, pi, 
				(outPort == null) ? (short) OFPort.OFPP_FLOOD.getValue() : outPort);
		
		return Command.CONTINUE;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		OFMatch match = new OFMatch();
		match.loadFromPacket(((OFPacketIn) msg).getPacketData(), ((OFPacketIn) msg).getInPort());
		
		if (match.getDataLayerType() != Ethernet.TYPE_IPv4 && match.getDataLayerType() != Ethernet.TYPE_ARP)
			return Command.CONTINUE;
		
//		logger.info("inpacket type: " + Integer.toHexString(match.getDataLayerType()));
		
		switch (msg.getType()) {
		case PACKET_IN:
			logger.debug("Receive a packet from port {} !!!", match.getInputPort());
			
			if (LearningSwitch.CTRL_LEVEL == 1) {
				// TODO:HUB
			}
			else if (LearningSwitch.CTRL_LEVEL == 2) {
				return this.ctrlLogicSwitchWithoutRules(sw, (OFPacketIn) msg);
			}
			else if (LearningSwitch.CTRL_LEVEL == 3) {
				return this.ctrlLogicSwitchWithRules(sw, (OFPacketIn) msg);
			}
			break;
			
		default:
			break;
		}
		
		return Command.CONTINUE;
	}

}
