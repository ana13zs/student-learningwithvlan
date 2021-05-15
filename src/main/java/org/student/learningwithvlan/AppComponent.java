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
package org.student.learningwithvlan;

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

        import java.util.concurrent.ConcurrentHashMap;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    ConcurrentHashMap<DeviceId, ConcurrentHashMap<MacAddress,PortNumber>> switchTable = new ConcurrentHashMap<>();

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    private ApplicationId appId;
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.student.learningswitch");
        TrafficSelector packetSelector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build();
        packetService.requestPackets(packetSelector, PacketPriority.REACTIVE, appId);
        packetService.addProcessor(processor, PacketProcessor.director(1));
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;log.info("Stopped");
    }

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

            if(ethPkt.getEtherType() != Ethernet.TYPE_IPV4)
                return;
            log.info("Proccesing packet request...");

            // First step is to check if the packet came from a newly discovered switch.
            // Create a new entry if required.
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            if (!switchTable.containsKey(deviceId)){
                log.info("Adding new switch: " + deviceId.toString());
                ConcurrentHashMap<MacAddress, PortNumber> hostTable = new ConcurrentHashMap<>();
                switchTable.put(deviceId, hostTable);
            }

            // Now lets check if the source host is a known host. If it is not add it to the switchTable.
            ConcurrentHashMap<MacAddress,PortNumber> hostTable = switchTable.get(deviceId);
            MacAddress srcMac = ethPkt.getSourceMAC();
            if (!hostTable.containsKey(srcMac)){
                log.info("Adding new host: "+srcMac.toString()+" to switch "+deviceId.toString());
                hostTable.put(srcMac,pkt.receivedFrom().port());
                switchTable.replace(deviceId,hostTable);
            }

            // To take care of loops, we must drop the packet if the port from which it came from does not match the
            // port that the source host should be attached to.
            if (!hostTable.get(srcMac).equals(pkt.receivedFrom().port())){
                log.info("Dropping packet to break loop");
                return;
            }

            // Now lets check if we know the destination host. If we do, assign the correct output port.
            // By default set the port to FLOOD.
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber outPort = PortNumber.FLOOD;

//            switch ((int) srcMac.toLong()) {
//                case 1:
//                    log.info("SrcMac: " + srcMac.toString());
//                    outPort = PortNumber.portNumber("3");
////                    log.info("Setting output port to 3");
//                    break;
//                case 2:
//                    log.info("SrcMac: " + srcMac.toString());
//                    outPort = PortNumber.portNumber("4");
////                    log.info("Setting output port to 4");
//                    break;
//                case 3:
//                    log.info("SrcMac: " + srcMac.toString());
//                    outPort = PortNumber.portNumber("1");
//                    log.info("Setting output port to 1");
//                    break;
//                case 4:
//                    log.info("SrcMac: " + srcMac.toString());
//                    outPort = PortNumber.portNumber("2");
//                    log.info("Setting output port to " + outPort.toLong());
//                    break;
//                default:
//                    break;
//            }

//            log.info("At device "+ deviceId.toString()+ ":");
//            log.info("Packet received from "+ pkt.receivedFrom().port().toLong());
//            log.info("SrcMac: " + srcMac.toString());
//            log.info("DstMac: " + dstMac.toString());

            if (switchTable.get(deviceId).containsKey(dstMac)){
                outPort = hostTable.get(dstMac);
                log.info("Setting learned output port to: " + outPort);
            }
            else {
                switch ((int) pkt.receivedFrom().port().toLong()) {
                    case 1:
                    case 3:
                    case 2:
                    case 4:
                        outPort = PortNumber.portNumber("5");
                        log.info("Forwarding to the other switch");
                        break;
                    case 5:
                        switch ((int) srcMac.toLong()) {
                            case 1:
                                outPort = PortNumber.portNumber("3");
                                log.info("Setting output port to " + outPort.toLong());
                                break;
                            case 2:
                                outPort = PortNumber.portNumber("4");
                                log.info("Setting output port to " + outPort.toLong());
                                break;
                            case 3:
                                outPort = PortNumber.portNumber("1");
                                log.info("Setting output port to 1");
                                break;
                            case 4:
                                outPort = PortNumber.portNumber("2");
                                log.info("Setting output port to " + outPort.toLong());
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
            }

            //Generate the traffic selector based on the packet that arrived.
            TrafficSelector packetSelector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchEthSrc(srcMac).build();

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outPort).build();

            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(packetSelector)
                    .withTreatment(treatment)
                    .withPriority(5000)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(appId)
                    .makeTemporary(10)
                    .add();

            if (outPort != PortNumber.FLOOD) {
                if(dstMac.toLong()%2 == srcMac.toLong()%2) {
                    hostTable.put(dstMac,outPort);
                    switchTable.put(deviceId, hostTable);
                    log.info("VLAN from " + srcMac.toString() + " and " + dstMac.toString()+ " match.");
                    log.info("MAC " + dstMac + " and port " + outPort.toLong() + " added to device " + deviceId );
                    flowObjectiveService.forward(deviceId, forwardingObjective);
                    context.treatmentBuilder().addTreatment(treatment);
                    log.info("And flow rule applied ");
                    log.info("TABLE:" );
                            if (switchTable.containsKey(DeviceId.deviceId(("of:0000000000000001"))))
                                log.info("S1: " + switchTable.get(DeviceId.deviceId(("of:0000000000000001"))));
                            if (switchTable.containsKey(DeviceId.deviceId(("of:0000000000000002"))))
                                log.info("S2: " + switchTable.get(DeviceId.deviceId(("of:0000000000000002"))));
                    log.info(" ");
                    context.send();
                }
            }
        }
    }
}
