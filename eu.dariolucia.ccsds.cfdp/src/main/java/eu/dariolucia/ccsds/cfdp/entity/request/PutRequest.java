/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.entity.request;

import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreRequestTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.MessageToUserTLV;

import java.util.*;

/**
 * The Put.request primitive shall be used by the application to request delivery of a file from
 * the source filestore to a destination filestore.
 *
 * Put.request is generated by the source CFDP user at any time.
 *
 * Receipt of Put.request shall cause the CFDP entity to initiate source entity put procedures
 *
 * Ref. CCSDS 727.0-B-5, 3.4.1
 */
public class PutRequest implements ICfdpRequest {

    /**
     * PutRequest factory method to transfer two files with the default settings.
     *
     * @param destinationCfdpEntityId The destination CFDP entity ID parameter shall identify the CFDP entity to which the FDU is to be sent
     * @param sourceFileName The source file name, full path; to be set null if the FDU contains only metadata
     * @param destinationFileName The destination file name, full path; to be set null if the FDU contains only metadata
     * @param segmentationControl true if segmentation control shall be used, otherwise false // TODO check if there is a mib default
     * @param flowLabel The flow label, can be null
     * @return the request
     */
    public static PutRequest build(long destinationCfdpEntityId, String sourceFileName, String destinationFileName, boolean segmentationControl, byte[] flowLabel) {
        return new PutRequest(destinationCfdpEntityId, sourceFileName, destinationFileName, segmentationControl, flowLabel, null, null, null, null, null);
    }

    /**
     * The destination CFDP entity ID parameter shall identify the CFDP entity to which
     * the FDU is to be sent.
     */
    private final long destinationCfdpEntityId;

    /**
     * The source file name parameter:
     * a) shall contain the full path name at which the file to be copied is located at the
     * filestore associated with the source entity;
     * b) shall be omitted when the FDU to be Put contains only metadata, such as a message
     * to a user or a standalone filestore request.
     */
    private final String sourceFileName;

    /**
     * The destination file name parameter:
     * a) shall contain the full path name to which the file to be copied will be placed at the
     * filestore associated with the destination entity;
     * b) shall be omitted when the FDU to be Put contains only metadata, such as a message
     * to a user or a standalone filestore request.
     */
    private final String destinationFileName;

    /**
     * The segmentation control parameter:
     * a) shall indicate whether the file being delivered is to be segmented as an array of octets
     * or as an array of variable-length records;
     * b) shall be omitted when local and remote file names are omitted.
     */
    private final boolean segmentationControl;

    /**
     * If included, the optional fault handler overrides shall indicate the actions to be taken
     * upon detection of one or more types of fault condition. Each fault handler override shall
     * identify both a type of fault condition to be handled and the action to be taken in the
     * event that a fault of this type is detected.
     */
    private final Map<Integer, FaultHandlerStrategy.Action> faultHandlerOverrideMap = new HashMap<>();

    /**
     * The flow label parameter may optionally be used to support prioritization and
     * preemption schemes. The flow label parameter should be taken as a hint to the order in which PDUs
     * should be transmitted when the opportunity arises, but the manner in which flow
     * labels are used is strictly an implementation matter.
     */
    private final byte[] flowLabel;

    /**
     * If included, the optional transmission mode parameter shall override the default
     * transmission mode. The values of the transmission mode parameter are 'acknowledged' or
     * 'unacknowledged'.
     */
    private final Boolean acknowledgedTransmissionMode;

    /**
     * If included, the optional closure requested parameter shall override the 'transaction
     * closure requested' setting in the MIB. The values of the closure requested parameter are
     * 'true' (indicating that transaction closure is requested) or 'false' (indicating that transaction
     * closure is not requested).
     */
    private final Boolean closureRequested;

    /**
     * If included, the optional Messages to User parameter shall be transmitted at the
     * beginning of the transaction and delivered to the destination CFDP user upon receipt. Certain
     * messages are defined in the User Operations section to allow remote initiation of CFDP
     * transactions.
     */
    private final List<MessageToUserTLV> messageToUserList = new LinkedList<>();

    /**
     * If included, the optional filestore requests shall be transmitted at the beginning of the
     * transaction and shall be acted upon by the destination entity when all data transfer
     * activities of the transaction are completed;
     */
    private final List<FilestoreRequestTLV> fileStoreRequestList = new LinkedList<>();

    /**
     * PutRequest full constructor.
     *
     * @param destinationCfdpEntityId The destination CFDP entity ID parameter shall identify the CFDP entity to which the FDU is to be sent
     * @param sourceFileName The source file name, full path; to be set null if the FDU contains only metadata
     * @param destinationFileName The destination file name, full path; to be set null if the FDU contains only metadata
     * @param segmentationControl Overrides the segmentation control specified in the MIB, can be null; not considered if source and destination filenames are omitted
     * @param flowLabel The flow label, can be null
     * @param acknowledgedTransmissionMode Overrides the transmission mode specified in the MIB, can be null
     * @param closureRequested Overrides the closure mode specified in the MIB, can be null
     * @param messageToUserList A list of messages to user, can be null
     * @param faultHandlerOverrideMap A map to override the fault handlers per fault condition specified in the MIB, can be null
     * @param fileStoreRequestList A list of file store requests, acted upon by the destination entity when all data transfer activities of the transaction are completed, can be null
     */
    public PutRequest(long destinationCfdpEntityId, String sourceFileName, String destinationFileName, boolean segmentationControl, byte[] flowLabel, Boolean acknowledgedTransmissionMode, Boolean closureRequested, List<MessageToUserTLV> messageToUserList, Map<Integer, FaultHandlerStrategy.Action> faultHandlerOverrideMap, List<FilestoreRequestTLV> fileStoreRequestList) { // NOSONAR due to protocol, builder pattern not design for this type of objects
        this.destinationCfdpEntityId = destinationCfdpEntityId;
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
        this.segmentationControl = segmentationControl;
        this.flowLabel = flowLabel;
        this.acknowledgedTransmissionMode = acknowledgedTransmissionMode;
        this.closureRequested = closureRequested;
        if(messageToUserList != null) {
            this.messageToUserList.addAll(messageToUserList);
        }
        if(faultHandlerOverrideMap != null) {
            this.faultHandlerOverrideMap.putAll(faultHandlerOverrideMap);
        }
        if(fileStoreRequestList != null) {
            this.fileStoreRequestList.addAll(fileStoreRequestList);
        }
    }

    public long getDestinationCfdpEntityId() {
        return destinationCfdpEntityId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }

    public boolean isSegmentationControl() {
        return segmentationControl;
    }

    public Map<Integer, FaultHandlerStrategy.Action> getFaultHandlerOverrideMap() {
        return Collections.unmodifiableMap(faultHandlerOverrideMap);
    }

    public byte[] getFlowLabel() {
        return flowLabel;
    }

    public Boolean getAcknowledgedTransmissionMode() {
        return acknowledgedTransmissionMode;
    }

    public Boolean getClosureRequested() {
        return closureRequested;
    }

    public List<MessageToUserTLV> getMessageToUserList() {
        return Collections.unmodifiableList(messageToUserList);
    }

    public List<FilestoreRequestTLV> getFileStoreRequestList() {
        return Collections.unmodifiableList(fileStoreRequestList);
    }

    @Override
    public String toString() {
        return "PutRequest{" +
                "destinationCfdpEntityId=" + destinationCfdpEntityId +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", destinationFileName='" + destinationFileName + '\'' +
                ", segmentationControl=" + segmentationControl +
                ", faultHandlerOverrideMap=" + faultHandlerOverrideMap +
                ", flowLabel=" + Arrays.toString(flowLabel) +
                ", acknowledgedTransmissionMode=" + acknowledgedTransmissionMode +
                ", closureRequested=" + closureRequested +
                ", messageToUserList=" + messageToUserList +
                ", fileStoreRequestList=" + fileStoreRequestList +
                '}';
    }
}
