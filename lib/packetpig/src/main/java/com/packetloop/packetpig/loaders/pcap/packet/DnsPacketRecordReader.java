package com.packetloop.packetpig.loaders.pcap.packet;

import com.packetloop.packetpig.loaders.pcap.PcapRecordReader;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.krakenapps.pcap.decoder.ethernet.EthernetType;
import org.krakenapps.pcap.decoder.ip.InternetProtocol;
import org.krakenapps.pcap.decoder.ip.IpDecoder;
import org.krakenapps.pcap.decoder.ip.Ipv4Packet;
import org.krakenapps.pcap.decoder.udp.UdpDecoder;
import org.krakenapps.pcap.decoder.udp.UdpPacket;
import org.krakenapps.pcap.decoder.udp.UdpPortProtocolMapper;
import org.krakenapps.pcap.decoder.udp.UdpProcessor;
import org.krakenapps.pcap.packet.PcapPacket;
import org.krakenapps.pcap.util.Buffer;
import org.xbill.DNS.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class DnsPacketRecordReader extends PcapRecordReader {

    private ArrayList<Tuple> tupleQueue;

    private volatile boolean valid = false;
    private volatile Message dns;

    @Override
    public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        super.initialize(split, context);
        tupleQueue = new ArrayList<Tuple>();

        IpDecoder ipDecoder = new IpDecoder();
        UdpProcessor udpProcessor = new UdpProcessor() {
            @Override
            public void process(UdpPacket p) {
                try {
                    Buffer buf = p.getData();
                    byte[] data = new byte[buf.readableBytes()];
                    buf.gets(data);
                    dns = new Message(data);
                    valid = true;
                } catch (IOException e) {
                    e.printStackTrace();
                    clear();
                }
            }
        };

        UdpDecoder udpDecoder = new UdpDecoder(new UdpPortProtocolMapper()) {
            @Override
            public void process(Ipv4Packet packet) {
                super.process(packet);
            }
        };

        udpDecoder.registerUdpProcessor(udpProcessor);
        eth.register(EthernetType.IPV4, ipDecoder);
        ipDecoder.register(InternetProtocol.UDP, udpDecoder);
    }

    private void clear() {
        valid = false;
        dns = null;
    }

    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {

        if (tupleQueue.size() > 0) {
            tuple = tupleQueue.remove(0);
            return true;
        }

        try {
            // keep going until the decoder says it found a good one.
            PcapPacket packet;

            do {
                packet = is.getPacket();
                eth.decode(packet);
            } while (!valid);

            long tv_sec = packet.getPacketHeader().getTsSec();
            long tv_usec = packet.getPacketHeader().getTsUsec();
            long ts = tv_sec * 1000 + tv_usec / 1000;
            key = new Date(ts).getTime() / 1000;

            int id = dns.getHeader().getID();
            String mode = dns.getHeader().getFlag(Flags.QR) ? "response" : "question";

            for (Record rec : dns.getSectionArray(Section.QUESTION)) {
                int i = 0;
                Tuple t = TupleFactory.getInstance().newTuple(5);
                t.set(i++, id); // transaction id
                t.set(i++, mode); // mode ('query' or 'response')
                t.set(i++, rec.getName().toString()); // qname
                t.set(i++, null); // answer.ip OR null (for ques)
                t.set(i++, 0); // qttl
                tupleQueue.add(t);
            }

            for (Record rec : dns.getSectionArray(Section.ANSWER)) {
                int i = 0;
                Tuple t = TupleFactory.getInstance().newTuple(5);
                t.set(i++, id); // transaction id
                t.set(i++, mode); // mode ('query' or 'response')
                t.set(i++, rec.getName().toString()); // qname

                if (rec instanceof ARecord) {
                    t.set(i++, ((ARecord)rec).getAddress().getHostAddress()); // answer.ip OR null
                } else {
                    t.set(i++, null);
                }
                t.set(i++, rec.getTTL()); // qttl
                tupleQueue.add(t);
            }

            clear();
            if (tupleQueue.size() > 0) {
                tuple = tupleQueue.remove(0);
                return true;
            } else {
                clear();
                is.close();
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            clear();
            is.close();
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        is.close();
    }
}
