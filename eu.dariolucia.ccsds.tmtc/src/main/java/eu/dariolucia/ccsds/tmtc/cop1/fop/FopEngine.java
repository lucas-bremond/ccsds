/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.cop1.fop;

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * This class implements the FOP side of the COP-1 protocol, as defined by CCSDS 232.1-B-2 Cor. 1.
 */
public class FopEngine implements IVirtualChannelSenderOutput<TcTransferFrame> {

    private final TcSenderVirtualChannel tcVc;

    private final ExecutorService fopExecutor;

    private final ExecutorService lowLevelExecutor;

    private final List<IFopObserver> observers = new CopyOnWriteArrayList<>();

    private Function<TcTransferFrame, Boolean> output;

    // FOP variables as per CCSDS 232.1-B-2 Cor. 1, section 5.1

    /**
     * This variable represents the state of FOP-1 for the specific Virtual Channel.
     */
    private AbstractFopState state;
    /**
     * The Transmitter_Frame_Sequence_Number, V(S), contains the value of the Frame Sequence
     * Number, N(S), to be put in the Transfer Frame Primary Header of the next Type-AD
     * Transfer Frame to be transmitted.
     * In this implementation, this information is read from the transmitted frame of Type-AD
     */
    private int transmitterFrameSequenceNumber; // V(S)
    /**
     * When Type-AD FDUs are received from the Higher Procedures, they shall be held in the
     * Wait_Queue until they can be accepted by FOP-1. The Wait_Queue has a maximum
     * capacity of one FDU.
     * The Wait_Queue and 'Accept Response to Request to Transfer FDU' form the primary
     * mechanism by which flow control as seen by the Higher Procedures is governed. When an
     * FDU is on the Wait_Queue, this means that the Higher Procedures have not yet received an
     * 'Accept Response' for the corresponding 'Request to Transfer FDU'.
     */
    private AtomicReference<TcTransferFrame> waitQueue = new AtomicReference<>(null);
    /**
     * Whether or not a ‘Transmit Request for Frame’ is outstanding for AD.
     */
    private boolean adOutReadyFlag = false;
    /**
     * Whether or not a ‘Transmit Request for Frame’ is outstanding for BD.
     */
    private boolean bdOutReadyFlag = false;
    /**
     * Whether or not a ‘Transmit Request for Frame’ is outstanding for BC.
     */
    private boolean bcOutReadyFlag = false;
    /**
     * The Sent_Queue is a Virtual Channel data structure in which the master copy of all Type-AD
     * and Type-BC Transfer Frames on a Virtual Channel is held between the time a copy of the
     * Transfer Frame is first passed to the Lower Procedures for transmission, and the time the
     * FOP-1 has finished processing the Transfer Frame.
     */
    private Queue<TransferFrameStatus> sentQueue = new LinkedList<>();
    /**
     * The Expected_Acknowledgement_Frame_Sequence_Number, NN(R), contains the Frame
     * Sequence Number of the oldest unacknowledged AD Frame, which is on the Sent_Queue.
     * This value is often equal to the value of N(R) from the previous CLCW on that Virtual
     * Channel.
     */
    private int expectedAckFrameSequenceNumber; // NN(R)
    /**
     * Whenever a Type-AD or Type-BC Transfer Frame is transmitted, the Timer shall be started
     * or restarted with an initial value of Timer_Initial_Value (T1_Initial).
     */
    private int timerInitialValue;
    /**
     * The Transmission_Limit holds a value which represents the maximum number of times the
     * first Transfer Frame on the Sent_Queue may be transmitted. This includes the first
     * 'transmission' and any subsequent 'retransmissions' of the Transfer Frame.
     */
    private int transmissionLimit;
    /**
     * The Timeout_Type variable may take one of two values, '0' or '1'.
     * It specifies the action to be performed when both the Timer expires and the
     * Transmission_Count has reached the Transmission_Limit.
     */
    private int timeoutType;
    /**
     * The Transmission_Count variable is used to count the number of transmissions of the first
     * Transfer Frame on the Sent_Queue. The Transmission_Count shall be incremented each
     * time the first Transfer Frame is retransmitted.
     */
    private int transmissionCount;
    /**
     * The Suspend_State variable may take one of five values, from '0' to
     * '4'. It records the state that FOP-1 was in when the AD Service was suspended. This is the state to
     * which FOP-1 will return should the AD Service be resumed. If SS = 0, the AD Service is deemed not suspended.
     */
    private int suspendState;
    /**
     * The FOP Sliding Window is a mechanism which limits the number of Transfer Frames which
     * can be transmitted ahead of the last acknowledged Transfer Frame, i.e., before a CLCW
     * report is received which updates the status of acknowledged Transfer Frames. This is done
     * to prevent sending a new Transfer Frame with the same sequence number as a rejected
     * Transfer Frame.
     */
    private int fopSlidingWindow;

    public FopEngine(TcSenderVirtualChannel linkedTcVc) {
        this.tcVc = linkedTcVc;
        this.tcVc.register(this);
        this.fopExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FOP Entity Processor for TC VC " + this.tcVc.getVirtualChannelId());
            return t;
        });
        this.lowLevelExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FOP Entity Low Level for TC VC " + this.tcVc.getVirtualChannelId());
            return t;
        });
        //
        this.state = new S6FopState(this); // In principle, the ‘Initial’ State is the first state entered by the state machine for a particular Virtual Channel.
    }

    public void setOutput(Function<TcTransferFrame, Boolean> output) {
        this.output = output;
    }

    public void register(IFopObserver observer) {
        this.observers.add(observer);
    }

    public void deregister(IFopObserver observer) {
        this.observers.remove(observer);
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 public operations as per CCSDS definition for event injection
    // ---------------------------------------------------------------------------------------------------------

    public void directive(Object tag, FopDirective directive, int qualifier) {
        fopExecutor.execute(() -> processDirective(tag, directive, qualifier));
    }

    public void transmit(TcTransferFrame frame) {
        switch(frame.getFrameType()) {
            case AD: {
                fopExecutor.execute(() -> processAdFrame(frame));
            }
            break;
            case BC: {
                // Direct output, as it is supposed to be generated by this entity
                lowLevelExecutor.execute(() -> forwardToOutput(frame));
            }
            break;
            case BD: {
                fopExecutor.execute(() -> processBdFrame(frame));
            }
            break;
            default:
                throw new IllegalArgumentException("TC Transfer Frame has unsupported type: " + frame.getFrameType());
        }
    }

    public void lowerLayer(TcTransferFrame frame, boolean accepted) {
        fopExecutor.execute(() -> processLowerLayer(frame, accepted));
    }

    public void timerExpired() {
        fopExecutor.execute(this::processTimerExpired);
    }

    public void clcw(Clcw clcw) {
        if(clcw.getCopInEffect() == Clcw.CopEffectType.COP1 && clcw.getVirtualChannelId() == tcVc.getVirtualChannelId()) {
            fopExecutor.execute(() -> processClcw(clcw));
        }
    }

    public void abort() {
        // TODO
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 actions defined as per CCSDS definition
    // ---------------------------------------------------------------------------------------------------------

    /**
     * This action includes clearing the Sent_Queue by generating a 'Negative Confirm Response to Request to
     * Transfer FDU' for each Transfer Frame on the queue and deleting the Transfer Frame.
     */
    void purgeSentQueue() {
        for(TransferFrameStatus tfs : this.sentQueue) {
            observers.forEach(o -> o.transferNotification(FopOperationStatus.NEGATIVE_CONFIRM, tfs.getFrame()));
        }
        this.sentQueue.clear();
    }

    /**
     * This action includes clearing the Wait_Queue and generating a 'Reject Response to Request to Transfer
     * FDU' for the queued FDU.
     */
    void purgeWaitQueue() {
        if(this.waitQueue.get() != null) {
            observers.forEach(o -> o.transferNotification(FopOperationStatus.REJECT_RESPONSE, this.waitQueue.get()));
        }
        this.waitQueue.set(null);
    }

    /**
     * This action includes all the functions necessary to prepare a Type-AD Transfer Frame for
     * transmission.
     *
     * @param frame the frame to send
     */
    void transmitTypeAdFrame(TcTransferFrame frame) {
        this.transmitterFrameSequenceNumber = frame.getTransferFrameVersionNumber(); // a)
        boolean sentQueueWasEmpty = this.sentQueue.isEmpty(); // in preparation for c)
        this.sentQueue.add(new TransferFrameStatus(frame)); // b)
        if(sentQueueWasEmpty) {
            this.transmissionCount = 1; // c)
        }
        restartTimer(); // d)
        this.adOutReadyFlag = false; // e)
        lowLevelExecutor.execute(() -> forwardToOutput(frame)); // f)
    }

    /**
     * This action includes all the functions necessary to prepare a Type-BC Transfer Frame for
     * transmission.
     *
     * @param frame the frame to send
     */
    void transmitTypeBcFrame(TcTransferFrame frame) {
        this.sentQueue.add(new TransferFrameStatus(frame)); // a)
        this.transmissionCount = 1; // b)
        restartTimer(); // c)
        this.bcOutReadyFlag = false; // d)
        lowLevelExecutor.execute(() -> forwardToOutput(frame)); // e)
    }

    /**
     * This action includes all the functions necessary to prepare a Type-BD Transfer Frame for
     * transmission.
     *
     * @param frame the frame to send
     */
    void transmitTypeBdFrame(TcTransferFrame frame) {
        this.bdOutReadyFlag = false; // a)
        lowLevelExecutor.execute(() -> forwardToOutput(frame)); // b)
    }

    /**
     *
     */
    void initiateRetransmission() {
        // a) Abort request to lower layer not provided
        this.transmissionCount++; // b)
        restartTimer(); // c)
        this.sentQueue.forEach(o -> o.setToBeRetransmitted(true)); // d)
    }

    void removeAckFramesFromSentQueue() {
        // TODO
    }

    void lookForDirective() {
        if(!bcOutReadyFlag) { // a)
            // If not, no further processing can be performed for retransmitting the Type-BC Transfer Frame until
            // a 'BC_Accept' Response is received from the Lower Procedures for the outstanding 'Transmit
            // Request for (BC) Frame', setting the BC_Out_Flag to 'Ready'.
        } else { // b)
            Optional<TransferFrameStatus> optBcFrame = this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.BC).findFirst();
            if(optBcFrame.isPresent() && optBcFrame.get().isToBeRetransmitted()) {
                this.bcOutReadyFlag = false;
                lowLevelExecutor.execute(() -> forwardToOutput(optBcFrame.get().getFrame()));
            }
        }
    }

    void lookForFrame(FopEvent fopEvent) {
        if(!adOutReadyFlag) { // a)
            // If not, no further processing can be performed for transmitting Type-AD Transfer Frames. (When an 'AD_Accept'
            // Response is received from the Lower Procedures for the outstanding 'Transmit Request for (AD) Frame',
            // FOP-1 will set the AD_Out_Flag to ‘Ready’ and execute a new 'Look for FDU'.)
        } else {
            // Checking if a Type-AD Transfer Frame on the Sent_Queue is flagged 'To_Be_Retransmitted'. If so, the flag
            // is set to 'Not_Ready' and a copy of the first such AD Transfer Frame is passed to the Lower Procedures as
            // a parameter of a 'Transmit Request for (AD) Frame' and the To_Be_Retransmitted_Flag for that Transfer Frame is reset
            Optional<TransferFrameStatus> optAdFrame = this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.AD).findFirst();
            // if(optBcFrame.isPresent() && optBcFrame.get().isToBeRetransmitted()) {
            //    this.bcOutReadyFlag = false;
            //    lowLevelExecutor.execute(() -> forwardToOutput(optBcFrame.get().getFrame()));
            // }
            // TODO continue from here
        }
    }

    void addToWaitQueue(FopEvent fopEvent) {

    }

    void confirm(FopOperationStatus status, FopEvent event) {

    }

    void initialise() {

    }

    void alert(FopAlertCode code) {

    }

    void suspend(int suspendState) {

    }

    void resume() {

    }

    void restartTimer() {

    }

    void cancelTimer() {

    }

    void reject(FopEvent event) {

    }

    void setFopSlidingWindow(int fopSlidingWindow) {
        this.fopSlidingWindow = fopSlidingWindow;
    }


    public void setT1Initial(int t1initial) {
        this.timerInitialValue = t1initial;
    }


    public void setTransmissionLimit(int limit) {
        this.transmissionLimit = limit;
    }

    public void setTimeoutType(int type) {
        this.timeoutType = type;
    }


    public void accept(FopEvent fopEvent) {

    }

    public void setAdOutReadyFlag(boolean flag) {
        this.adOutReadyFlag = flag;
    }

    public void setBcOutReadyFlag(boolean flag) {
        this.bcOutReadyFlag = flag;
    }

    public void setBdOutReadyFlag(boolean flag) {
        this.bdOutReadyFlag = flag;
    }

    public void transmitTypeBcFrameUnlock() {
        this.tcVc.dispatchUnlock();
    }

    public void transmitTypeBcFrameSetVr(int vr) {
        this.tcVc.dispatchSetVr(vr);
    }

    public void prepareForSetVr(int vr) {

    }

    public void setVs(int vs) {

    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 class methods performed by the fopExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void processClcw(Clcw clcw) {
        FopEvent event;
        if(!clcw.isLockoutFlag()) {
            // Lockout == 0
            if(clcw.getReportValue() == this.transmitterFrameSequenceNumber) {
                // Valid N(R) and all outstanding type AD frames acknowledged
                if(!clcw.isRetransmitFlag()) {
                    // Retransmit == 0
                    if(!clcw.isWaitFlag()) {
                        // Wait == 0
                        if(clcw.getReportValue() == this.expectedAckFrameSequenceNumber) {
                            // N(R) == NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E1, clcw, this.suspendState);
                        } else {
                            // N(R) != NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E2, clcw, this.suspendState);
                        }
                    } else {
                        // Wait == 1
                        event = new FopEvent(FopEvent.EventNumber.E3, clcw, this.suspendState);
                    }
                } else {
                    // Retransmit == 1
                    event = new FopEvent(FopEvent.EventNumber.E4, clcw, this.suspendState);
                }
            } else if(clcw.getReportValue() < this.transmitterFrameSequenceNumber && clcw.getReportValue() >= this.expectedAckFrameSequenceNumber) {
                // Valid N(R) and some outstanding type AD frames not yet acknowledged
                if(!clcw.isRetransmitFlag()) {
                    // Retransmit == 0
                    if(!clcw.isWaitFlag()) {
                        // Wait == 0
                        if(clcw.getReportValue() == this.expectedAckFrameSequenceNumber) {
                            // N(R) == NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E5, clcw, this.suspendState);
                        } else {
                            // N(R) != NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E6, clcw, this.suspendState);
                        }
                    } else {
                        // Wait == 1
                        event = new FopEvent(FopEvent.EventNumber.E7, clcw, this.suspendState);
                    }
                } else {
                    // Retransmit == 1
                    if(this.transmissionLimit == 1) {
                        // Transmission limit == 1
                        if(clcw.getReportValue() != this.expectedAckFrameSequenceNumber) {
                            // N(R) != NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E101, clcw, this.suspendState);
                        } else {
                            // N(R) == NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E102, clcw, this.suspendState);
                        }
                    } else {
                        // Transmission limit > 1 (cannot be <= 0)
                        if(clcw.getReportValue() != this.expectedAckFrameSequenceNumber) {
                            // N(R) != NN(R)
                            event = new FopEvent(!clcw.isWaitFlag() ? FopEvent.EventNumber.E8 : FopEvent.EventNumber.E9, clcw, this.suspendState);
                        } else {
                            // N(R) == NN(R)
                            if(this.transmissionCount < this.transmissionLimit) {
                                // Transmission count < Transmission limit
                                event = new FopEvent(!clcw.isWaitFlag() ? FopEvent.EventNumber.E10 : FopEvent.EventNumber.E11, clcw, this.suspendState);
                            } else {
                                // Transmission count >= Transmission limit
                                event = new FopEvent(!clcw.isWaitFlag() ? FopEvent.EventNumber.E12 : FopEvent.EventNumber.E103, clcw, this.suspendState);
                            }
                        }
                    }
                }
            } else {
                // Invalid N(R)
                event = new FopEvent(FopEvent.EventNumber.E13, clcw, this.suspendState);
            }
        } else {
            // Lockout == 1
            event = new FopEvent(FopEvent.EventNumber.E14, clcw, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processTimerExpired() {
        FopEvent event;
        if(this.transmissionCount < this.transmissionLimit) {
            // Transmission count < Transmission limit
            event = new FopEvent(timeoutType == 0 ? FopEvent.EventNumber.E16 : FopEvent.EventNumber.E104, true, this.suspendState);
        } else {
            // Transmission count >= Transmission limit
            event = new FopEvent(timeoutType == 0 ? FopEvent.EventNumber.E17 : FopEvent.EventNumber.E18, true, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processBdFrame(TcTransferFrame frame) {
        FopEvent event;
        if(bdOutReadyFlag) {
            //
            event = new FopEvent(FopEvent.EventNumber.E21, frame, this.suspendState);
        } else {
            //
            event = new FopEvent(FopEvent.EventNumber.E22, frame, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processAdFrame(TcTransferFrame frame) {
        FopEvent event;
        if(waitQueue.get() == null) {
            // Wait queue empty
            event = new FopEvent(FopEvent.EventNumber.E19, frame, this.suspendState);
        } else {
            // Wait queue not empty
            event = new FopEvent(FopEvent.EventNumber.E20, frame, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processDirective(Object tag, FopDirective directive, int qualifier) {
        FopEvent event;
        switch(directive) {
            case INIT_AD_WITHOUT_CLCW:
                event = new FopEvent(FopEvent.EventNumber.E23, tag, directive, qualifier, this.suspendState);
                break;
            case INIT_AD_WITH_CLCW:
                event = new FopEvent(FopEvent.EventNumber.E24, tag, directive, qualifier, this.suspendState);
                break;
            case INIT_AD_WITH_UNLOCK:
                event = new FopEvent(bcOutReadyFlag ? FopEvent.EventNumber.E25 : FopEvent.EventNumber.E26, tag, directive, qualifier, this.suspendState);
                break;
            case INIT_AD_WITH_SET_V_R:
                event = new FopEvent(bcOutReadyFlag ? FopEvent.EventNumber.E27 : FopEvent.EventNumber.E28, tag, directive, qualifier, this.suspendState);
                break;
            case TERMINATE:
                event = new FopEvent(FopEvent.EventNumber.E29, tag, directive, qualifier, this.suspendState);
                break;
            case RESUME: {
                switch (this.suspendState) {
                    case 0:
                        event = new FopEvent(FopEvent.EventNumber.E30, tag, directive, qualifier, this.suspendState);
                        break;
                    case 1:
                        event = new FopEvent(FopEvent.EventNumber.E31, tag, directive, qualifier, this.suspendState);
                        break;
                    case 2:
                        event = new FopEvent(FopEvent.EventNumber.E32, tag, directive, qualifier, this.suspendState);
                        break;
                    case 3:
                        event = new FopEvent(FopEvent.EventNumber.E33, tag, directive, qualifier, this.suspendState);
                        break;
                    case 4:
                        event = new FopEvent(FopEvent.EventNumber.E34, tag, directive, qualifier, this.suspendState);
                        break;
                    default:
                        throw new IllegalStateException("Suspend state for TC VC " + tcVc.getVirtualChannelId() + " not supported: " + this.suspendState);
                }
            }
            break;
            case SET_V_S:
                event = new FopEvent(FopEvent.EventNumber.E35, tag, directive, qualifier, this.suspendState);
                break;
            case SET_FOP_SLIDING_WINDOW:
                event = new FopEvent(FopEvent.EventNumber.E36, tag, directive, qualifier, this.suspendState);
                break;
            case SET_T1_INITIAL:
                event = new FopEvent(FopEvent.EventNumber.E37, tag, directive, qualifier, this.suspendState);
                break;
            case SET_TRANSMISSION_LIMIT:
                event = new FopEvent(FopEvent.EventNumber.E38, tag, directive, qualifier, this.suspendState);
                break;
            case SET_TIMEOUT_TYPE:
                event = new FopEvent(FopEvent.EventNumber.E39, tag, directive, qualifier, this.suspendState);
                break;
            default:
                event = new FopEvent(FopEvent.EventNumber.E40, tag, directive, qualifier, this.suspendState);
                break;
        }
        applyStateTransition(event);
    }

    private void processLowerLayer(TcTransferFrame frame, boolean accepted) {
        FopEvent event;
        switch(frame.getFrameType()) {
            case AD:
                event = new FopEvent(accepted ? FopEvent.EventNumber.E41 : FopEvent.EventNumber.E42, frame, this.suspendState);
                break;
            case BC:
                event = new FopEvent(accepted ? FopEvent.EventNumber.E43 : FopEvent.EventNumber.E44, frame, this.suspendState);
                break;
            case BD:
                event = new FopEvent(accepted ? FopEvent.EventNumber.E45 : FopEvent.EventNumber.E46, frame, this.suspendState);
                break;
            default:
                throw new IllegalArgumentException("Frame type " + frame.getFrameType() + " not supported");
        }
        applyStateTransition(event);
    }

    private void applyStateTransition(FopEvent event) {
        this.state = this.state.event(event);
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP members to interact with low level output executed by the lowLevelExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void forwardToOutput(TcTransferFrame frame) {
        boolean result = false;
        if(output != null) {
            result = output.apply(frame);
        }
        lowerLayer(frame, result);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Other members
    // ---------------------------------------------------------------------------------------------------------

    @Override
    public void transferFrameGenerated(AbstractSenderVirtualChannel<TcTransferFrame> vc, TcTransferFrame generatedFrame, int bufferedBytes) {
        transmit(generatedFrame);
    }

    public void dispose() {
        this.tcVc.deregister(this);
        this.fopExecutor.shutdownNow();
    }

    private static class TransferFrameStatus {
        private final TcTransferFrame frame;
        private boolean toBeRetransmitted;

        public TransferFrameStatus(TcTransferFrame frame) {
            this.frame = frame;
            this.toBeRetransmitted = false;
        }

        public void setToBeRetransmitted(boolean toBeRetransmitted) {
            this.toBeRetransmitted = toBeRetransmitted;
        }

        public boolean isToBeRetransmitted() {
            return toBeRetransmitted;
        }

        public TcTransferFrame getFrame() {
            return frame;
        }
    }
}
