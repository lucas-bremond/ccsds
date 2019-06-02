/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.builder.ITransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractSenderVirtualChannel<T extends AbstractTransferFrame> {

    private final List<IVirtualChannelSenderOutput> listeners = new CopyOnWriteArrayList<>();

    private final int spacecraftId;

    private final int virtualChannelId;

    private final VirtualChannelAccessMode mode;

    private final boolean fecfPresent;

    private final AtomicInteger virtualChannelFrameCounter = new AtomicInteger(0);

    private final IVirtualChannelDataProvider dataProvider;

    protected ITransferFrameBuilder<T> currentFrame;

    protected AbstractSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent) {
        this(spacecraftId, virtualChannelId, mode, fecfPresent, null);
    }

    protected AbstractSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, IVirtualChannelDataProvider dataProvider) {
        this.spacecraftId = spacecraftId;
        this.virtualChannelId = virtualChannelId;
        this.mode = mode;
        this.fecfPresent = fecfPresent;
        this.dataProvider = dataProvider;
    }

    /**
     * Depending on the frame size and access mode, request either space packets, bit stream or user data to
     * fill up a frame. It is responsibility of the data provider to deliver the data if available. The
     * post-condition of this method is to generate and emit at most one frame:
     * - if the data provider delivers not enough data, the method returns false: the caller can decide what to do
     *   (i.e. generate an idle frame from another virtual channel, force the dispatch of the frame, generate an idle
     *   packet to fill up the frame);
     * - if the data provider delivers too much data, the method returns true and a frame is emitted: the remaining
     *   data is used to fill up the next frame. If the data completes or exceeds also the next frame, an exception
     *   is thrown.
     *
     * @return true if the frame is emitted, false otherwise (not enough data)
     */
    public boolean pullNextFrame() {
        if(dataProvider == null) {
            throw new IllegalStateException("Virtual channel not instantiated in pull mode");
        }
        // Calculate how much free space we have to fill up the frame, and the maximum amount of data that this
        // channel can handle to avoid violating the constraint on the generation of at most one frame
        int availableSpaceInCurrentFrame = getRemainingFreeSpace();
        if(mode == VirtualChannelAccessMode.Packet) {
            List<SpacePacket> packets = this.dataProvider.generateSpacePackets(getVirtualChannelId(), availableSpaceInCurrentFrame, availableSpaceInCurrentFrame + getMaxUserDataLength() - 1);
            // Compute if a frame will be emitted or not
            int newDataSize = packets == null ? 0 : packets.stream().map(SpacePacket::getLength).reduce(0, Integer::sum);
            if(newDataSize >= availableSpaceInCurrentFrame + getMaxUserDataLength()) {
                // Two frames or more would be generated: error by the data provider
                throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " requested max " + (availableSpaceInCurrentFrame + getMaxUserDataLength() - 1) + " bytes to data provider " +
                        "but " + newDataSize + " bytes were received (as space packets), cannot process");
            } else {
                if(packets != null && !packets.isEmpty()) {
                    dispatch(packets);
                }
                // If the amount of received data is less than availableSpaceInCurrentFrame, then no frame was emitted
                // but the data has been stored for the next pull request
                if(newDataSize < availableSpaceInCurrentFrame) {
                    return false;
                } else {
                    // Received data is equal or more than availableSpaceInCurrentFrame, then one frame was emitted
                    // and the remaining data has been stored for the next pull request
                    return true;
                }
            }
        } else if(mode == VirtualChannelAccessMode.Bitstream) {
            BitstreamData data = this.dataProvider.generateBitstreamData(getVirtualChannelId(), availableSpaceInCurrentFrame);
            // Compute if a frame will be emitted or not
            int newDataSize = data == null ? 0 : data.getNumBits()/8 + (data.getNumBits() % 8 == 0 ? 0 : 1);
            // For bitstream data, no segmentation is possible, so data must be less than or equal the requested amount
            if(newDataSize > availableSpaceInCurrentFrame) {
                // Constraint violation
                throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " requested max " + (availableSpaceInCurrentFrame) + " bytes to data provider " +
                        "but " + newDataSize + " bytes were received (as bitstream), cannot process");
            } else {
                if(data != null) {
                    dispatch(data);
                }
                return true;
            }
        } else if(mode == VirtualChannelAccessMode.Data) {
            byte[] data = this.dataProvider.generateData(getVirtualChannelId(), availableSpaceInCurrentFrame);
            // Compute if a frame will be emitted or not
            int newDataSize = data == null ? 0 : data.length;
            // For user data, no segmentation is possible, so data must be less than or equal the requested amount
            if(newDataSize > availableSpaceInCurrentFrame) {
                // Constraint violation
                throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " requested max " + (availableSpaceInCurrentFrame) + " bytes to data provider " +
                        "but " + newDataSize + " bytes were received (as user data), cannot process");
            } else {
                if(data != null) {
                    dispatch(data);
                }
                return true;
            }
        } else {
            throw new IllegalStateException("Virtual channel access mode " + mode + " not supported, cannot generate frame for virtual channel " + getVirtualChannelId());
        }
    }

    public int getSpacecraftId() {
        return spacecraftId;
    }

    public int getVirtualChannelId() {
        return virtualChannelId;
    }

    public VirtualChannelAccessMode getMode() {
        return mode;
    }

    public boolean isFecfPresent() {
        return fecfPresent;
    }

    public int getNextVirtualChannelFrameCounter() {
        return this.virtualChannelFrameCounter.get();
    }

    public void setVirtualChannelFrameCounter(int number) {
        this.virtualChannelFrameCounter.set(number);
    }

    protected int incrementVirtualChannelFrameCounter(int modulus) {
        int toReturn = this.virtualChannelFrameCounter.get() % modulus;
        if (toReturn == 0) {
            this.virtualChannelFrameCounter.set(1);
        } else {
            this.virtualChannelFrameCounter.set(toReturn + 1);
        }
        return toReturn;
    }

    public final void register(IVirtualChannelSenderOutput listener) {
        this.listeners.add(listener);
    }

    public final void deregister(IVirtualChannelSenderOutput listener) {
        this.listeners.remove(listener);
    }

    protected final void notifyTransferFrameGenerated(T frame, int currentBufferedData) {
        this.listeners.forEach(o -> o.transferFrameGenerated(this, frame, currentBufferedData));
    }

    protected int calculateRemainingData(List<SpacePacket> packets, int i) {
        int bytes = 0;
        for (; i < packets.size(); ++i) {
            bytes += packets.get(i).getLength();
        }
        return bytes;
    }

    /**
     * This method requests the generation of one or more transfer frames, which contain the provided
     * packets. The method returns the amount of free bytes that are still available in the last generated but not emitted
     * frame.
     *
     * For TM-based VCs, the VC will wait to receive a sufficient amount of packets to emit its last frame, i.e. frames
     * are emitted once they are full. Depending on the amount and size of space packets, this method can result in zero, one or
     * multiple frames being emitted.
     *
     * For TC-based VCs, the VC will always emit at least one frame. The VC will try to pack TC packets inside a single
     * frame and will avoid segmentation if possible. For space packets larger than the maximum frame size, the VC ensures
     * that the large space packets is segmented across frames. Subsequent packets will use different frames.
     *
     * @param packets the packets to be encapsulated inside transfer frames
     * @return the amount of free bytes that are still available in the last generated but not emitted frame
     */
    public abstract int dispatch(Collection<SpacePacket> packets);

    public abstract int dispatch(SpacePacket... packets);

    public abstract int dispatch(SpacePacket packet);

    public abstract int dispatch(BitstreamData bitstreamData);

    public abstract int dispatch(byte[] userData);

    public abstract void dispatchIdle(byte[] idlePattern);

    public abstract int getMaxUserDataLength();

    public boolean isPendingFramePresent() {
        return this.currentFrame != null;
    }

    public int getRemainingFreeSpace() {
        if (this.currentFrame != null) {
            return this.currentFrame.getFreeUserDataLength();
        } else {
            return getMaxUserDataLength();
        }
    }

    public void forceDispatch() {
        if (this.currentFrame != null) {
            T frame = this.currentFrame.build();
            this.currentFrame = null;
            notifyTransferFrameGenerated(frame, 0);
        }
    }
}
