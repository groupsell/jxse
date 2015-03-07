package net.jxta.impl.endpoint.netty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jxta.endpoint.EndpointAddress;
import net.jxta.endpoint.EndpointService;
import net.jxta.endpoint.MessageSender;
import net.jxta.endpoint.Messenger;
import net.jxta.endpoint.MessengerEventListener;
import net.jxta.logging.Logger;
import net.jxta.logging.Logging;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.util.HashedWheelTimer;

/**
 * Client side of a netty based transport. Responsible for initiating outgoing connections.
 * 
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class NettyTransportClient implements MessageSender, TransportClientComponent {

    private final class ClientConnectionRegistrationHandler implements NettyChannelRegistry {
        public EndpointAddress directedAt;
        public EndpointAddress logicalEndpointAddress;
        public CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void newConnection(Channel channel, EndpointAddress directedAt, EndpointAddress logicalEndpointAddress) {
            this.directedAt = directedAt;
            this.logicalEndpointAddress = logicalEndpointAddress;
            latch.countDown();
        }
    }

    private static final Logger LOG = Logging.getLogger(NettyTransportClient.class.getName());
    
    private final EndpointAddress localAddress;
    
    private final PeerGroup group;
    private final PeerGroupID homeGroupID;
    private final PeerID localPeerID;
    private EndpointService endpointService;
    private MessengerEventListener messageEventListener;
    private final AddressTranslator addrTranslator;
    
    private final ChannelGroup channels;
    private final HashedWheelTimer timeoutTimer;
    private ChannelGroupFuture closeChannelsFuture;
    private final AtomicBoolean started;
    private final AtomicBoolean stopping;
    
    private final ChannelFactory clientFactory;
    private final EndpointAddress returnAddress;
    
    public NettyTransportClient(ChannelFactory clientFactory, AddressTranslator addrTranslator, PeerGroup group, EndpointAddress returnAddress) {
        this.started = new AtomicBoolean(false);
        this.stopping = new AtomicBoolean(false);
        this.channels = new DefaultChannelGroup();
        this.addrTranslator = addrTranslator;
        this.clientFactory = clientFactory;
        this.returnAddress = returnAddress;
        
        localAddress = returnAddress;
        
        this.group = group;
        this.homeGroupID = group.getPeerGroupID();
        this.localPeerID = group.getPeerID();
        
        timeoutTimer = new HashedWheelTimer();
    }

    @Override
    public boolean start(EndpointService endpointService) {
        this.endpointService = endpointService;
        messageEventListener = endpointService.addMessageTransport(this);
        
        if(messageEventListener == null) {
            return false;
        }
        
        started.set(true);
        return true;
    }
    
    @Override
    public void beginStop() {
        
        if(!started.get()) {

            Logging.logCheckedWarning(LOG, "Netty transport server for protocol ", addrTranslator.getProtocolName(), " already stopped or never started!");
            return;

        }

        closeChannelsFuture = channels.close();
        stopping.set(true);

    }
    
    @Override
    public void stop() {

        if(!stopping.get()) {

            Logging.logCheckedWarning(LOG, "Netty transport server for protocol ", addrTranslator.getProtocolName(), " already stopped or never started!");
            return;

        }

        closeChannelsFuture.awaitUninterruptibly();
        clientFactory.releaseExternalResources();
        timeoutTimer.stop();
        
        endpointService.removeMessageTransport(this);
        endpointService = null;

    }

    /**
     * {@inheritDoc }
     * @param dest     
     * @return
     */
    @Override
    public Messenger getMessenger(EndpointAddress dest) {
//    public Messenger getMessenger(EndpointAddress dest, Object hint) {
        
        if(!started.get()) {

            Logging.logCheckedWarning(LOG, "Request to get messenger for ", dest.toString(), " when netty transport client stopped or never started");
            return null;

        }
        
        Logging.logCheckedInfo(LOG, "processing request to open connection to ", dest);
        
        ClientConnectionRegistrationHandler clientRegistry = new ClientConnectionRegistrationHandler();
        
        ClientBootstrap bootstrap = new ClientBootstrap(clientFactory);
        bootstrap.setPipelineFactory(new NettyTransportChannelPipelineFactory(group, localPeerID, timeoutTimer, clientRegistry, addrTranslator, dest, returnAddress));
        
        ChannelFuture connectFuture = bootstrap.connect(addrTranslator.toSocketAddress(dest));
        
        try {

            if(!connectFuture.await(5000L, TimeUnit.MILLISECONDS)) {
                if(Logging.SHOW_INFO && LOG.isInfoEnabled()) {
                    LOG.infoParams("Netty transport for protocol {} failed to connect to {} within acceptable time", 
                            new Object[] { addrTranslator.getProtocolName(), dest });
                }
                return null;
            }
        } catch(InterruptedException e) {

            Logging.logCheckedWarning(LOG, "Interrupted while waiting for connection to ", dest, " to be established");
            connectFuture.cancel();
            return null;
        }
        
        if(!connectFuture.isSuccess()) {

            if(Logging.SHOW_INFO && LOG.isInfoEnabled()) {
                Throwable cause = connectFuture.getCause();
                String causeString = (cause != null) ? cause.getMessage() : "cause unknown";
				String message = String.format("Netty transport for protocol %s failed to connect to %s - %s", addrTranslator.getProtocolName(), dest, causeString);
                LOG.info(message);
            }

            return null;

        }
        
        boolean established = false;

        try {

            established = clientRegistry.latch.await(15L, TimeUnit.SECONDS);

        } catch(InterruptedException e) {

            Logging.logCheckedWarning(LOG, "Interrupted while waiting for connection handover\n", e);
            
        }
        
        if(!established) {

            Logging.logCheckedWarning(LOG, "Connection handover timed out - either remote host was not a valid JXTA peer or did not respond on time");
            connectFuture.getChannel().close();
            return null;

        }
        
        if(Logging.SHOW_INFO && LOG.isInfoEnabled()) {
            LOG.infoParams("succeeded in connecting to {}, remote peer has logical address {}", new Object[] { dest, clientRegistry.logicalEndpointAddress });
        }
        
        channels.add(connectFuture.getChannel());
        // return new NettyMessenger(connectFuture.getChannel(), homeGroupID, localPeerID, clientRegistry.directedAt, clientRegistry.logicalEndpointAddress, endpointService);
        return new AsynchronousNettyMessenger(connectFuture.getChannel(), homeGroupID, localPeerID, clientRegistry.directedAt, clientRegistry.logicalEndpointAddress, endpointService);
    }
    
    @Override
    public boolean allowsRouting() {
        return true;
    }

    @Override
    public EndpointAddress getPublicAddress() {
        return localAddress;
    }

    @Override
    public boolean isConnectionOriented() {
        return true;
    }

    @Deprecated
    public boolean ping(EndpointAddress addr) {
        throw new RuntimeException("ping is deprecated, do not use!");
    }

    @Override
    public EndpointService getEndpointService() {
        return endpointService;
    }

    @Override
    public String getProtocolName() {
        return addrTranslator.getProtocolName();
    }
}
