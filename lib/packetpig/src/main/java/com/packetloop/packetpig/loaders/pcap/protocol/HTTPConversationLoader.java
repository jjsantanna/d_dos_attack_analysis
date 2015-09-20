package com.packetloop.packetpig.loaders.pcap.protocol;

import com.packetloop.packetpig.loaders.pcap.PcapLoader;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;

import java.io.IOException;

public class HTTPConversationLoader extends PcapLoader {
    public String field;
    private String pathToTcp;

    public HTTPConversationLoader(String field) {
        this.pathToTcp = "lib/scripts/tcp.py";
        this.field = field;
    }

    public HTTPConversationLoader(String field, String pathToTcp) {
        this.pathToTcp = pathToTcp;
        this.field = field;
    }

    @Override
    public InputFormat getInputFormat() throws IOException {
        return new FileInputFormat() {

            @Override
            public RecordReader createRecordReader(InputSplit split, TaskAttemptContext context) {
                return new HTTPConversationRecordReader(pathToTcp, field);
            }
        };
    }
}
