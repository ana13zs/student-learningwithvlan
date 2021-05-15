/*package org.student.learningwithvlan;*/
/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javafx.util.Pair;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skeletal ONOS application component.
 */
/*
@Component(immediate = true)
public class AppComponent {

    // Option 2
    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.student.learningwithvlan");
        TrafficSelector packetSelector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build();
        packetService.requestPackets(packetSelector, PacketPriority.REACTIVE, appId);
        packetService.addProcessor(processor, PacketProcessor.director(1));
        log.info("App Started");
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;log.info("App Stopped");
    }

    private ApplicationId appId;
    private final Logger log = LoggerFactory.getLogger(getClass());

    ConcurrentHashMap<DeviceId,
            ConcurrentHashMap<MacAddress, Pair<PortNumber, VlanId>>>
            switchTable = new ConcurrentHashMap<>(2);
    ConcurrentHashMap<MacAddress, Pair<PortNumber, VlanId>> hostTable1 = new ConcurrentHashMap<>();
    ConcurrentHashMap<MacAddress, Pair<PortNumber, VlanId>> hostTable3 = new ConcurrentHashMap<>();
    Pair<PortNumber, VlanId> vlanPair1 = new Pair(PortNumber.portNumber("1"), VlanId.vlanId("1"));
    Pair<PortNumber, VlanId> vlanPair3 = new Pair(PortNumber.portNumber("3"), VlanId.vlanId("3"));

    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            //Discard if  packet is null.
            if (ethPkt == null) {
                log.info("Discarding null packet");
                return;
            }
            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4)
                return;
            log.info("Proccesing packet request.");

            //switches link is in ports 7
            MacAddress SrcMac = ethPkt.getSourceMAC();
            MacAddress DstMac = ethPkt.getDestinationMAC();
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            // First step is to check if the packet came from a newly discovered switch.
            // Create a new entry if required.
            if (!switchTable.containsKey(deviceId)){
                log.info("Adding new switch: " + deviceId.toString());
                ConcurrentHashMap<MacAddress, Pair<PortNumber, VlanId>> hostTable = new ConcurrentHashMap<>();
                switchTable.put(deviceId, hostTable);
            }
            // Now lets check if the source host is a known host. If it is not add it to the switchTable.
            ConcurrentHashMap<MacAddress,Pair<PortNumber, VlanId>> hostTable = switchTable.get(deviceId);
            PortNumber port = pkt.receivedFrom().port();
            MacAddress srcMac = ethPkt.getSourceMAC();
            if (!hostTable.containsKey(srcMac)) {
                log.info("Adding new host: " + srcMac.toString() + " for switch " + deviceId.toString());
                Pair<PortNumber, VlanId> portTable = new Pair(port, VlanId.vlanId(port.toString()));
                hostTable.put(srcMac,portTable);
                switchTable.replace(deviceId, hostTable);
            }
            // To take care of loops, we must drop the packet if the port from which it came from does not match the
            // port that the source host should be attached to.
            if (!hostTable.get(srcMac).equals(pkt.receivedFrom().port())) {
                log.info("Dropping packet to break loop");
                return;
            }

            // Now lets check if we know the destination host. If we do, assign the correct output port.
            // By default set the port to FLOOD.
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber outPort = null;
            if (hostTable.containsKey(dstMac)) {
                switch ((int) srcMac.toLong()) {
                    case 1:
                        outPort = PortNumber.portNumber(3);
                        break;
                    case 2:
                        outPort = PortNumber.portNumber(4);
                        break;
                    case 3:
                        outPort = PortNumber.portNumber(1);
                        break;
                    case 4:
                        outPort = PortNumber.portNumber(2);
                        break;
                    default:
                        log.info("Wrong destination");
                        break;
                }
                log.info("Setting output port to: " + outPort);
            }
            //Generate the traffic selector based on the packet that arrived.
            TrafficSelector packetSelector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchEthDst(dstMac).build();

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outPort).build();

            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(packetSelector)
                    .withTreatment(treatment)
                    .withPriority(5000)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(appId)
                    .makeTemporary(5)
                    .add();

            if (outPort != null)
                flowObjectiveService.forward(deviceId, forwardingObjective);
            context.treatmentBuilder().addTreatment(treatment);
            context.send();
        }
    }
}
    */
