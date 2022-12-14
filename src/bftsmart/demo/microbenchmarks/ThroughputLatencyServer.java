/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.demo.microbenchmarks;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;
import bftsmart.tom.util.Storage;


/**
 * Simple server that just acknowledge the reception of a request.
 */
public final class ThroughputLatencyServer extends DefaultRecoverable{
    
    private int interval;
    private int replySize;
    private float maxTp = -1;
    private boolean context;
    private long lastExecuteBatchTime = 0;
    private int nExecutedRequest = 0;
    private byte[] state;
    private int id;

    private int iterations = 0;
    private long throughputMeasurementStartTime = System.currentTimeMillis();
    private Storage totalLatency = null;
    private Storage consensusLatency = null;
    private Storage preConsLatency = null;
    private Storage posConsLatency = null;
    private Storage proposeLatency = null;
    private Storage writeLatency = null;
    private Storage acceptLatency = null;
    private ServiceReplica replica;
    private int totaltxs = 0;

    public ThroughputLatencyServer(int id, int interval, int replySize, int stateSize, boolean context, int totaltxs) {

        this.interval = interval;
        this.replySize = replySize;
        this.context = context;
        this.totaltxs = totaltxs;
        this.id = id;
        
        this.state = new byte[stateSize];
        
        for (int i = 0; i < stateSize ;i++)
            state[i] = (byte) i;

        totalLatency = new Storage(interval);
        consensusLatency = new Storage(interval);
        preConsLatency = new Storage(interval);
        posConsLatency = new Storage(interval);
        proposeLatency = new Storage(interval);
        writeLatency = new Storage(interval);
        acceptLatency = new Storage(interval);

        replica = new ServiceReplica(id, this, this);
    }
    
    @Override
    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {
        
        byte[][] replies = new byte[commands.length][];
        this.interval = msgCtxs.length;
        this.iterations = 0;
        for (int i = 0; i < commands.length; i++) {
            
            replies[i] = execute(commands[i],msgCtxs[i]);
            
        }
        long now = System.currentTimeMillis();
        long diff = (now - lastExecuteBatchTime);
        nExecutedRequest += msgCtxs.length;
        System.out.printf("Node %d execute a Batch at time %d, execute interval %d ms, batch txes num: %d, TPS: %d, total txes num: %d\n", replica.getId(), now, diff, msgCtxs.length, (msgCtxs.length*1000)/(diff), nExecutedRequest);
        lastExecuteBatchTime = now;
        if(nExecutedRequest == this.totaltxs) {
            replica.outputData(this.id, this.totaltxs);
        }
        return replies;
    }
    
    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        return execute(command,msgCtx);
    }
    
    public byte[] execute(byte[] command, MessageContext msgCtx) {        
        boolean readOnly = false;
        
        iterations++;

        if (msgCtx != null && msgCtx.getFirstInBatch() != null) {
            

            readOnly = msgCtx.readOnly;
                    
            msgCtx.getFirstInBatch().executedTime = System.currentTimeMillis();
                        
            totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);

            if (readOnly == false) {

                consensusLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime);
                long temp = msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime;
                preConsLatency.store(temp > 0 ? temp : 0);
                posConsLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime);            
                proposeLatency.store(msgCtx.getFirstInBatch().writeSentTime - msgCtx.getFirstInBatch().consensusStartTime);
                writeLatency.store(msgCtx.getFirstInBatch().acceptSentTime - msgCtx.getFirstInBatch().writeSentTime);
                acceptLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().acceptSentTime);
                // System.out.println("latencyana: "+msgCtx.getFirstInBatch().getSequence()+" "+msgCtx.getFirstInBatch().getSender()+" "+msgCtx.getFirstInBatch().receptionTime+" "+msgCtx.getFirstInBatch().executedTime);
                

            } else {
            
           
                consensusLatency.store(0);
                preConsLatency.store(0);
                posConsLatency.store(0);            
                proposeLatency.store(0);
                writeLatency.store(0);
                acceptLatency.store(0);
                
                
            }
            
        } else {
            
            
                consensusLatency.store(0);
                preConsLatency.store(0);
                posConsLatency.store(0);            
                proposeLatency.store(0);
                writeLatency.store(0);
                acceptLatency.store(0);
                
               
        }
        
        float tp = -1;
        if(iterations % interval == 0) {
            if (context) System.out.println("--- (Context)  iterations: "+ iterations + " // regency: " + msgCtx.getRegency() + " // consensus: " + msgCtx.getConsensusId() + " ---");
            
            System.out.println("--- Measurements after "+ iterations+" ops ("+interval+" samples) ---");
            
            tp = (float)(interval*1000/(float)(System.currentTimeMillis()-throughputMeasurementStartTime));
            
            if (tp > maxTp) maxTp = tp;
            
            System.out.println("Throughput = " + tp +" operations/sec (Maximum observed: " + maxTp + " ops/sec)");            
            
            System.out.println("Total latency = " + totalLatency.getAverage(false) + " (+/- "+ (long)totalLatency.getDP(false) +") us ");
            totalLatency.reset();
            System.out.println("Consensus latency = " + consensusLatency.getAverage(false) + " (+/- "+ (long)consensusLatency.getDP(false)  +") ms ");
            consensusLatency.reset();
            System.out.println("Pre-consensus latency = " + preConsLatency.getAverage(false) + " (+/- "+ (long)preConsLatency.getDP(false) +") ms ");
            preConsLatency.reset();
            System.out.println("Pos-consensus latency = " + posConsLatency.getAverage(false) + " (+/- "+ (long)posConsLatency.getDP(false) +") ms ");
            posConsLatency.reset();
            System.out.println("Propose latency = " + proposeLatency.getAverage(false)  + " (+/- "+ (long)proposeLatency.getDP(false)  +") ms ");
            proposeLatency.reset();
            System.out.println("Write latency = " + writeLatency.getAverage(false)  + " (+/- "+ (long)writeLatency.getDP(false)   +") ms ");
            writeLatency.reset();
            System.out.println("Accept latency = " + acceptLatency.getAverage(false) + " (+/- "+ (long)acceptLatency.getDP(false)  +") ms ");
            acceptLatency.reset();
            
            throughputMeasurementStartTime = System.currentTimeMillis();
        }

        return new byte[replySize];
    }

    public static void main(String[] args){
        if(args.length < 5) {
            System.out.println("Usage: ... ThroughputLatencyServer <processId> <measurement interval> <reply size> <state size> <context?>");
            System.exit(-1);
        }

        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
        int replySize = Integer.parseInt(args[2]);
        int stateSize = Integer.parseInt(args[3]);
        boolean context = Boolean.parseBoolean(args[4]);
        int totaltxs = Integer.parseInt(args[5]);

        new ThroughputLatencyServer(processId,interval,replySize, stateSize, context, totaltxs);        
    }

    @Override
    public void installSnapshot(byte[] state) {
        //nothing
    }

    @Override
    public byte[] getSnapshot() {
        return this.state;
    }

   
}
