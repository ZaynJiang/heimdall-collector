package cn.heimdall.client;

import cn.heimdall.core.cluster.NodeInfo;
import cn.heimdall.core.cluster.NodeInfoManager;
import cn.heimdall.core.config.NetworkConfig;
import cn.heimdall.core.config.NetworkManageConfig;
import cn.heimdall.core.message.MessageType;
import cn.heimdall.core.message.NodeRole;
import cn.heimdall.core.network.processor.client.NodeHeartbeatProcessor;
import cn.heimdall.core.network.remote.AbstractRemotingClient;
import cn.heimdall.core.network.remote.ClientPoolKey;
import cn.heimdall.core.utils.thread.NamedThreadFactory;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 数据类消息客户端
 */
public class TransportRemotingClient extends AbstractRemotingClient {

    private static volatile TransportRemotingClient instance;

    private NodeInfo nodeInfo;

    public TransportRemotingClient(NetworkConfig networkConfig, ThreadPoolExecutor executor) {
        //TODO
        super(networkConfig, executor, null);
    }

    @Override
    public void init() {
        nodeInfo = NodeInfoManager.getInstance().getNodeInfo();
        super.init();
        this.registerProcessor();
    }

    @Override
    protected Set<InetSocketAddress> getAvailableAddress() {
        return null;
    }

    @Override
    protected long getResourceExpireTime() {
        return 0;
    }

    @Override
    protected NodeRole getRemoteRole() {
        return null;
    }

    private void registerProcessor() {
        NodeHeartbeatProcessor nodeHeartbeatProcessor = new NodeHeartbeatProcessor();
        super.registerProcessor(MessageType.TYPE_NODE_REGISTER_REQUEST, nodeHeartbeatProcessor, messageExecutor);
        super.registerProcessor(MessageType.TYPE_NODE_HEARTBEAT_REQUEST, nodeHeartbeatProcessor, messageExecutor);
    }

    private static final long KEEP_ALIVE_TIME = Integer.MAX_VALUE;

    private static final int MAX_QUEUE_SIZE = 20000;

    public static TransportRemotingClient getInstance() {
        if (instance == null) {
            synchronized (TransportRemotingClient.class) {
                if (instance == null) {
                    NetworkManageConfig networkManageConfig = new NetworkManageConfig();
                    //发送消息线程池
                    final ThreadPoolExecutor messageExecutor = new ThreadPoolExecutor(
                            NetworkManageConfig.MANAGE_WORK_THREAD_SIZE, NetworkManageConfig.MANAGE_WORK_THREAD_SIZE,
                            KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                            new NamedThreadFactory("manage:", true),
                            new ThreadPoolExecutor.CallerRunsPolicy());
                    instance = new TransportRemotingClient(networkManageConfig,  messageExecutor);
                }
            }
        }
        return instance;
    }

    @Override
    public String loadBalance() {
        return null;
    }

    @Override
    protected Function<String, ClientPoolKey> getPoolKeyFunction() {
        return addressIp -> new ClientPoolKey(nodeInfo.getNodeRoles(), addressIp,null);
    }

    @Override
    public void onRegisterMsgSuccess(String serverAddress, Channel channel) {

    }

    @Override
    public void onRegisterMsgFail(String serverAddress, Channel channel) {

    }
}