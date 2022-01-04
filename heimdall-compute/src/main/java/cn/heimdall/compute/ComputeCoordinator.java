package cn.heimdall.compute;

import cn.heimdall.compute.analyzer.AbstractMessageAnalyzer;
import cn.heimdall.compute.processor.client.StoreAppStateResponseProcessor;
import cn.heimdall.compute.processor.client.StoreMetricResponseProcessor;
import cn.heimdall.compute.processor.server.AppStateProcessor;
import cn.heimdall.compute.processor.server.MessageTreeProcessor;
import cn.heimdall.core.message.MessageBody;
import cn.heimdall.core.message.MessageDoorway;
import cn.heimdall.core.message.MessageType;
import cn.heimdall.core.message.body.ComputeMessageRequest;
import cn.heimdall.core.message.body.MessageRequest;
import cn.heimdall.core.message.body.origin.AppStateRequest;
import cn.heimdall.core.message.body.origin.AppStateResponse;
import cn.heimdall.core.message.body.origin.MessageTreeRequest;
import cn.heimdall.core.message.body.origin.MessageTreeResponse;
import cn.heimdall.core.message.hander.ComputeInboundHandler;
import cn.heimdall.core.network.client.GuarderRemotingClient;
import cn.heimdall.core.network.client.StorageRemotingClient;
import cn.heimdall.core.network.coordinator.Coordinator;
import cn.heimdall.core.network.processor.client.NodeHeartbeatResponseProcessor;
import cn.heimdall.core.network.processor.client.NodeRegisterResponseProcessor;
import cn.heimdall.core.network.remote.AbstractRemotingServer;
import cn.heimdall.core.network.remote.RemotingInstanceFactory;
import cn.heimdall.core.utils.annotation.LoadLevel;
import cn.heimdall.core.utils.constants.LoadLevelConstants;
import cn.heimdall.core.utils.enums.NettyServerType;
import cn.heimdall.core.utils.event.EventBus;
import cn.heimdall.core.utils.event.EventBusManager;
import cn.heimdall.core.utils.spi.EnhancedServiceLoader;
import cn.heimdall.core.utils.spi.Initialize;
import cn.heimdall.core.utils.thread.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;


/**
 * 处理计算请求协调器
 */
@LoadLevel(name = LoadLevelConstants.COMPUTE_COORDINATOR)
public final class ComputeCoordinator implements MessageDoorway, Coordinator, ComputeInboundHandler, Initialize {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeCoordinator.class);

    private EventBus eventBus = EventBusManager.get();

    private Map<MessageType, AbstractMessageAnalyzer> analyzerMap;

    private ScheduledThreadPoolExecutor metricUploader = new ScheduledThreadPoolExecutor(1,
            new NamedThreadFactory("metricUploader", 1));
    private ScheduledThreadPoolExecutor traceLogUploader = new ScheduledThreadPoolExecutor(1,
            new NamedThreadFactory("traceLogUploader", 1));
    private ScheduledThreadPoolExecutor appStateUploader = new ScheduledThreadPoolExecutor(1,
            new NamedThreadFactory("appStateUploader", 1));

    @Override
    public void init() {
        List<AbstractMessageAnalyzer> allAnalyzers = EnhancedServiceLoader.loadAll(AbstractMessageAnalyzer.class);
        analyzerMap = allAnalyzers.stream().collect(Collectors.
                toMap(AbstractMessageAnalyzer::getMessageType, a -> a, (k1, k2) -> k1));

    }

    @Override
    public MessageBody onRequest(MessageBody request) {
        //如果不是发往服务端的消息
        if (!(request instanceof MessageRequest)) {
            throw new IllegalArgumentException();
        }
        ComputeMessageRequest clientMessage = (ComputeMessageRequest) request;
        clientMessage.setComputeInboundHandler(this);
        //执行inbound的方法
        return clientMessage.handle();
    }

    @Override
    public void onResponse(MessageBody response) {
        return;
    }

    @Override
    public AppStateResponse handle(AppStateRequest request) {
        analyzerMap.get(request.getMessageType()).distribute(request);
        AppStateResponse response = new AppStateResponse();
        return response;
    }

    @Override
    public MessageTreeResponse handle(MessageTreeRequest request) {
        analyzerMap.get(request.getMessageType()).distribute(request);
        MessageTreeResponse response = new MessageTreeResponse();
        return response;
    }

    @Override
    public NettyServerType getNettyServerType() {
        return NettyServerType.TRANSPORT;
    }

    @Override
    public AbstractRemotingServer generateServerRemoteInstance() {
        AbstractRemotingServer remotingServer = RemotingInstanceFactory.generateRemotingServer(getNettyServerType());
        remotingServer.doRegisterProcessor(MessageType.TYPE_CLIENT_APP_STATE_REQUEST, new AppStateProcessor(remotingServer, this));
        remotingServer.doRegisterProcessor(MessageType.TYPE_CLIENT_MESSAGE_TREE_REQUEST, new MessageTreeProcessor(remotingServer, this));
        return remotingServer;
    }

    @Override
    public void initClientRemoteInstance() {
        //init guarder client
        GuarderRemotingClient guarder = GuarderRemotingClient.getInstance();
        guarder.doRegisterProcessor(MessageType.TYPE_NODE_HEARTBEAT_REQUEST,  new NodeHeartbeatResponseProcessor());
        guarder.doRegisterProcessor(MessageType.TYPE_NODE_REGISTER_REQUEST, new NodeRegisterResponseProcessor());
        //init storage client
        StorageRemotingClient storage = StorageRemotingClient.getInstance();
        storage.doRegisterProcessor(MessageType.TYPE_STORE_TRANCE_LOG_REQUEST, new StoreAppStateResponseProcessor());
        storage.doRegisterProcessor(MessageType.TYPE_STORE_METRIC_REQUEST, new StoreMetricResponseProcessor());
        storage.doRegisterProcessor(MessageType.TYPE_STORE_APP_STATE_REQUEST, new StoreAppStateResponseProcessor());
    }

}
