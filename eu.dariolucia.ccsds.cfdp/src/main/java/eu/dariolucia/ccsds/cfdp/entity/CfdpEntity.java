package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.ICfdpRequest;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.impl.FixedSizeSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpEntity implements IUtLayerSubscriber {

    private static final Logger LOG = Logger.getLogger(CfdpEntity.class.getName());

    private final Mib mib;
    private final IVirtualFilestore filestore;
    private final Map<String, IUtLayer> utLayers = new TreeMap<>();

    // Subscribers
    private final List<ICfdpEntitySubscriber> subscribers = new CopyOnWriteArrayList<>();

    // Notification thread
    private final ExecutorService subscriberNotifier;
    // Confinement thread
    private final ExecutorService entityConfiner;
    // Map of ongoing transactions
    private final Map<Long, CfdpTransaction> id2transaction = new HashMap<>();
    // Request processor map
    private final Map<Class<? extends ICfdpRequest>, Consumer<ICfdpRequest>> requestProcessors = new HashMap<>();
    // Transaction ID sequencer
    private final AtomicLong transactionIdSequencer = new AtomicLong(0); // XXX: this will need to be revised
    // File segmentation strategies
    private final List<ICfdpSegmentationStrategy> supportedSegmentationStrategies = new CopyOnWriteArrayList<>();

    // Disposed flag
    private boolean disposed;

    public CfdpEntity(Mib mib, IVirtualFilestore filestore, IUtLayer... layers) {
        this(mib, filestore, Arrays.asList(layers));
    }

    public CfdpEntity(Mib mib, IVirtualFilestore filestore,  Collection<IUtLayer> layers) {
        this.mib = mib;
        this.filestore = filestore;
        for(IUtLayer l : layers) {
            this.utLayers.put(l.getName(), l);
        }
        // 1 separate thread to notify all listeners
        this.subscriberNotifier = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "CFDP Entity " + mib.getLocalEntity().getLocalEntityId() + " - Subscribers Notifier");
            t.setDaemon(true);
            return t;
        });
        // 1 separate thread to manage the entity
        this.entityConfiner = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "CFDP Entity " + mib.getLocalEntity().getLocalEntityId() + " - Manager");
            t.setDaemon(true);
            return t;
        });
        // Register request processors
        this.requestProcessors.put(PutRequest.class, this::processPutRequest);
        // Add default segmentation strategy
        this.supportedSegmentationStrategies.add(new FixedSizeSegmentationStrategy());
        // Ready to go
        startProcessing();
    }

    public void addSegmentationStrategy(ICfdpSegmentationStrategy strategy) {
        this.supportedSegmentationStrategies.add(0, strategy);
    }

    private void startProcessing() {
        // Subscribe to the UT layers, so that in case something arrives for you (proxy, or any PDU)
        // then you can handle it
        for(IUtLayer l : this.utLayers.values()) {
            l.register(this);
        }
    }

    public Mib getMib() {
        return this.mib;
    }

    public IUtLayer getUtLayerByName(String name) {
        return this.utLayers.get(name);
    }

    public IUtLayer getUtLayerByDestinationEntity(long destinationEntityId) {
        RemoteEntityConfigurationInformation re = this.mib.getRemoteEntityById(destinationEntityId);
        return re != null ? this.utLayers.get(re.getUtLayer()) : null;
    }

    public IVirtualFilestore getFilestore() {
        return this.filestore;
    }

    public void register(ICfdpEntitySubscriber s) {
        this.subscribers.add(s);
    }

    public void deregister(ICfdpEntitySubscriber s) {
        this.subscribers.add(s);
    }

    public void request(ICfdpRequest request) {
        this.entityConfiner.submit(() -> processRequest(request));
    }

    private void processRequest(ICfdpRequest request) {
        if(disposed) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.severe(String.format("Entity %d disposed, request rejected", mib.getLocalEntity().getLocalEntityId()));
            }
            return;
        }
        Consumer<ICfdpRequest> processor = this.requestProcessors.get(request.getClass());
        if(processor != null) {
            processor.accept(request);
        } else {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.severe(String.format("Entity %d cannot handle request %s: processor not found", mib.getLocalEntity().getLocalEntityId(), request));
            }
        }
    }

    /**
     * Receipt of a Put.request primitive shall cause the source CFDP entity to initiate:
     * <ol type="a">
     * <li>a Transaction Start Notification procedure; and</li>
     * <li>a Copy File procedure, for which
     * <ol>
     * <li>any fault handler overrides shall be derived from the Put.request,</li>
     * <li>any Messages to User or filestore requests shall be derived from the
     * Put.request,</li>
     * <li>omission of source and destination filenames shall indicate that only metadata
     * will be delivered,</li>
     * <li>the transmission mode (acknowledged or unacknowledged) is defined by the
     * existing content of the MIB unless overridden by the transmission mode
     * parameter of the Put.request,</li>
     * <li>the transaction closure requested indication (true or false) is defined by the
     * existing content of the MIB unless overridden by the closure requested parameter
     * of the Put.request. </li>
     * </ol>
     * </li>
     * </ol>
     *
     * @param request the {@link PutRequest} object
     */
    private void processPutRequest(ICfdpRequest request) {
        PutRequest r = (PutRequest) request;
        // Get a new transaction ID
        long transactionId = generateTransactionId();
        // Create a new transaction object to send the specified file
        CfdpTransaction cfdpTransaction = new CfdpOutgoingTransaction(transactionId, this, r);
        // Register the transaction in the map
        this.id2transaction.put(transactionId, cfdpTransaction);
        // Start the transaction
        cfdpTransaction.activate();
    }

    /**
     * This method can be invoked also by {@link CfdpTransaction} objects.
     *
     * @param indication the indication to notify
     */
    void notifyIndication(ICfdpIndication indication) {
        this.subscriberNotifier.submit(() -> {
            for(ICfdpEntitySubscriber s : this.subscribers) {
                try {
                    s.indication(this, indication);
                } catch (Exception e) {
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("Entity %d cannot notify subscriber %s: %s", this.mib.getLocalEntity().getLocalEntityId(), s, e.getMessage()), e);
                    }
                }
            }
        });
    }

    /**
     * This method looks for a viable strategy to segment the provided file. If no such alternative is known, the
     * entity will fall back to a standard fixed-side segmenter.
     *
     * @param sourceFileName the source file name for which the segmentation strategy is requested
     * @param destinationId the destination of the transaction
     * @return a new segmenter of first segmentation strategy that can be applied to the file, or null if no suitable
     * strategy can be found
     */
    public ICfdpFileSegmenter getSegmentProvider(String sourceFileName, long destinationId) {
        for(ICfdpSegmentationStrategy s : this.supportedSegmentationStrategies) {
            try {
                if (s.support(this.mib, this.filestore, sourceFileName)) {
                    return s.newSegmenter(this.mib, this.filestore, sourceFileName, destinationId);
                }
            } catch (FilestoreException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Problem when checking segmentation strategy for file %s to entity %d on strategy instance %s: %s", sourceFileName, destinationId, s, e.getMessage()), e);
                }
            }
        }
        return null;
    }

    private long generateTransactionId() {
        return this.transactionIdSequencer.incrementAndGet();
    }

    public void dispose() {
        // TODO: delegate to confiner
            // TODO: set disposed to true
            // TODO: manage transactions
            // TODO: inform subscribers and clear

        // Shutdown the thread pools
        this.subscriberNotifier.shutdown();
        this.entityConfiner.shutdown();
    }

    /* **********************************************************************************************************
     * IUtLayerSubscriber methods
     * **********************************************************************************************************/

    @Override
    public void indication(IUtLayer layer, CfdpPdu pdu) {
        this.entityConfiner.submit(() -> processIndication(layer, pdu));
    }

    private void processIndication(IUtLayer layer, CfdpPdu pdu) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity %d: received PDU from UT layer %s: %s", mib.getLocalEntity().getLocalEntityId(), layer.getName(), pdu));
        }
        // Three possibilities: 1) the pdu is not for this entity -> discard TODO: for store-and-foward this is not appropriate
        if(pdu.getDestinationEntityId() != mib.getLocalEntity().getLocalEntityId()) {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("CFDP Entity %d: PDU from UT layer %s from entity %d not for this entity: received %d", mib.getLocalEntity().getLocalEntityId(), layer.getName(), pdu.getSourceEntityId(), pdu.getDestinationEntityId()));
            }
            return;
        }
        CfdpTransaction transaction = this.id2transaction.get(pdu.getTransactionSequenceNumber());
        if(transaction != null) {
            // 2) the PDU is for this entity and there is a transaction already running -> forward
            transaction.indication(pdu);
        } else {
            // 3) the PDU is for this entity and there is no transaction already running -> create
            createNewIncomingTransaction(pdu);
        }
    }

    private void createNewIncomingTransaction(CfdpPdu pdu) {
        // Create a new transaction object to handle the requested transaction
        CfdpTransaction cfdpTransaction = new CfdpIncomingTransaction(pdu, this);
        // Register the transaction in the map
        this.id2transaction.put(cfdpTransaction.getTransactionId(), cfdpTransaction);
        // Start the transaction
        cfdpTransaction.activate();
    }

    @Override
    public void startTxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }

    @Override
    public void endTxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }

    @Override
    public void startRxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }

    @Override
    public void endRxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }
}
