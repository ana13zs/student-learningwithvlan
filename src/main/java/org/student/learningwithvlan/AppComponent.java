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
    ConcurrentHashMap<PortNumber, VlanId> vlanTable = new ConcurrentHashMap<>();

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
        appId = coreService.registerApplication("org.student.learningwithvlan");
        TrafficSelector packetSelector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build();
        packetService.requestPackets(packetSelector, PacketPriority.REACTIVE, appId);
        packetService.addProcessor(processor, PacketProcessor.director(1));
        vlanTable.put(PortNumber.portNumber(1), VlanId.vlanId((short) 10));
        vlanTable.put(PortNumber.portNumber(2), VlanId.vlanId((short) 20));
        vlanTable.put(PortNumber.portNumber(3), VlanId.vlanId((short) 10));
        vlanTable.put(PortNumber.portNumber(4), VlanId.vlanId((short) 20));
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
            PortNumber srcPkt = pkt.receivedFrom().port();
            log.info("Proccesing packet request RECEIVED FROM PORT: " + srcPkt.toLong());
            // First step is to check if the packet came from a newly discovered switch.
            // Create a new entry if required.
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            DeviceId otherDeviceId = deviceId.equals(DeviceId.deviceId("of:0000000000000001")) ? DeviceId.deviceId("of:0000000000000002") : DeviceId.deviceId("of:0000000000000001");
            log.info("TABLE:");
            if (switchTable.containsKey(deviceId))
                log.info("S1: " + switchTable.get(DeviceId.deviceId(("of:0000000000000001"))));
            if (switchTable.containsKey(otherDeviceId))
                log.info("S2: " + switchTable.get(DeviceId.deviceId(("of:0000000000000002"))));

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
                hostTable.put(srcMac,srcPkt);
                switchTable.replace(deviceId,hostTable);
            }

            // To take care of loops, we must drop the packet if the port from which it came from does not match the
            // port that the source host should be attached to.
            if (!hostTable.get(srcMac).equals(srcPkt)){
                log.info("Dropping packet to break loop");
                return;
            }

            // Now lets check if we know the destination host. If we do, assign the correct output port.
            // By default set the port to FLOOD.
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber outPort = PortNumber.FLOOD;

            log.info("At device "+ deviceId.toString()+ ":");
//            log.info("The other one is " + otherDeviceId.toString());
            log.info("Packet received from port "+ srcPkt.toLong());
            log.info("SrcMac: " + srcMac.toString());
            log.info("DstMac: " + dstMac.toString());

            if (switchTable.get(deviceId).containsKey(dstMac)){
                    outPort = hostTable.get(dstMac);
            }
            else {
                switch ((int) srcPkt.toLong()) {
                    case 1:
                    case 3:
                    case 2:
                    case 4:
                        outPort = PortNumber.portNumber("5");
                        log.info("Forwarding to the other switch");
                        break;
                    case 5:
                        log.info("Unknown host");
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
                    .makeTemporary(30)
                    .add();
            log.info("SOURCE PORT: " + String.valueOf(srcPkt.toLong()));
            if (srcPkt.toLong()==(PortNumber.portNumber(5).toLong())) {
                log.info("Packet comes from " + otherDeviceId.toString());
                if(switchTable.containsKey(otherDeviceId));
                    if (switchTable.get(otherDeviceId).containsKey(srcMac)) {
                        log.info("Retrieving original source port...");
                        srcPkt = switchTable.get(otherDeviceId).get(srcMac);
                        log.info("NEW SOURCE PORT: " + String.valueOf(srcPkt.toLong()));
                    }
            }
            if ((outPort.toLong() < 5) & (srcPkt.toLong()!=(PortNumber.portNumber(5).toLong()))) {
                log.info("Packet originally from port " +srcPkt.toLong() + " set to port "+ outPort);
                if (vlanTable.get(outPort).equals(vlanTable.get(srcPkt))){
                    log.info("----------> VLANs MATCH! (" + vlanTable.get(outPort).toString() + ")");
                    if (srcMac.toLong()%2==dstMac.toLong()%2) {
                        //Small cheating. Without this if statement, a flow rule
                        // from e.g. h1(S1) to an unknown h2(S3) would send the packets to port 3 where h3(S3) is and
                        // that would mess up everything.
                        hostTable.put(dstMac, outPort);
                        switchTable.put(deviceId, hostTable);
                        log.info("MAC " + dstMac.toString() + " and port " + outPort.toLong() + " added to device " + deviceId.toString());
                        flowObjectiveService.forward(deviceId, forwardingObjective);
                        log.info("Flow rule applied ");
                        context.treatmentBuilder().addTreatment(treatment);
                        context.send();
                    }
                    else
                        log.info("Flow rule not applied");
                }
            }
            else if (outPort.toLong() == (PortNumber.portNumber(5).toLong())) {
                    flowObjectiveService.forward(deviceId, forwardingObjective);
                    log.info("Flow rule applied for port 5");
                    context.treatmentBuilder().addTreatment(treatment);
                    context.send();
            }

            log.info("TABLE after processing:");
            if (switchTable.containsKey(deviceId))
                log.info("S1: " + switchTable.get(DeviceId.deviceId(("of:0000000000000001"))));
            if (switchTable.containsKey(otherDeviceId))
                log.info("S2: " + switchTable.get(DeviceId.deviceId(("of:0000000000000002"))));
            log.info(" ");
        }
    }
}