package cn.heimdall.core.network.remote;

import cn.heimdall.core.config.NetworkConfig;
import cn.heimdall.core.message.Message;
import cn.heimdall.core.network.processor.RemoteProcessor;
import cn.heimdall.core.utils.exception.NetworkException;
import cn.heimdall.core.utils.spi.ServiceLoaderUtil;
import cn.heimdall.core.utils.thread.NamedThreadFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractRemoting {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRemoting.class);

    protected final ServiceLoader<RemoteHook> rpcHooks = ServiceLoaderUtil.getServiceLoader(RemoteHook.class);

    protected final ConcurrentHashMap<Integer, MessageFuture> futures = new ConcurrentHashMap<>();

    protected final ScheduledExecutorService timerExecutor = new ScheduledThreadPoolExecutor(1,
            new NamedThreadFactory("timeoutChecker", 1, true));

    /**
     * processor type {@link cn.heimdall.core.message.MessageType}
     */
    protected final HashMap<Integer/*MessageType*/, Pair<RemoteProcessor, ExecutorService>>
            processorTable = new HashMap<>(32);


    protected final ThreadPoolExecutor messageExecutor;

    protected final Object lock = new Object();

    protected volatile long nowMills = 0;

    public AbstractRemoting(ThreadPoolExecutor messageExecutor) {
        this.messageExecutor = messageExecutor;
    }

    /**
     * 注册处理器
     * @param messageType
     * @param processor
     * @param executor
     */
    protected void registerProcessor(int messageType, RemoteProcessor processor, ExecutorService executor) {
        Pair<RemoteProcessor, ExecutorService> pair = new Pair<>(processor, executor);
        this.processorTable.put(messageType, pair);
    }

    public void init() {
        timerExecutor.scheduleAtFixedRate(() -> {
                for (Map.Entry<Integer, MessageFuture> entry : futures.entrySet()) {
                    if (entry.getValue().isTimeout()) {
                        futures.remove(entry.getKey());
                        entry.getValue().setResultMessage(null);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("timeout clear future: {}", entry.getValue().getRequestMessage().getMessageBody());
                        }
                    }
                }
                nowMills = System.currentTimeMillis();
            }, 3000, 3000, TimeUnit.MILLISECONDS);
    }

    public void destroy() {
        timerExecutor.shutdown();
        messageExecutor.shutdown();
    }

    protected Object sendSync(Channel channel, Message message, long timeoutMillis) throws TimeoutException {
        if (timeoutMillis <= 0) {
            throw new NetworkException("timeout should more than 0ms");
        }
        if (channel == null) {
            LOGGER.warn("sendSync nothing, caused by null channel.");
            return null;
        }
        MessageFuture messageFuture = new MessageFuture();
        messageFuture.setRequestMessage(message);
        messageFuture.setTimeout(timeoutMillis);
        futures.put(message.getMessageId(), messageFuture);
        channelWritableCheck(channel, message.getMessageBody());
        String remoteAddr = ChannelHelper.getAddressFromChannel(channel);
        doBeforeRpcHooks(remoteAddr, message);
        channel.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                MessageFuture messageFuture1 = futures.remove(message.getMessageId());
                if (messageFuture1 != null) {
                    messageFuture1.setResultMessage(future.cause());
                }
                destroyChannel(future.channel());
            }
        });
        try {
            Object result = messageFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            doAfterRpcHooks(remoteAddr, message, result);
            return result;
        } catch (Exception exx) {
            LOGGER.error("wait response error:{},ip:{},request:{}", exx.getMessage(), channel.remoteAddress(),
                    message.getMessageBody());
            if (exx instanceof TimeoutException) {
                throw (TimeoutException) exx;
            } else {
                throw new RuntimeException(exx);
            }
        }
    }

    private void channelWritableCheck(Channel channel, Object msg) {
        int tryTimes = 0;
        synchronized (lock) {
            while (!channel.isWritable()) {
                try {
                    tryTimes++;
                    if (tryTimes > NetworkConfig.getMaxNotWriteableRetry()) {
                        destroyChannel(channel);
                        throw new NetworkException("ChannelIsNotWritable, msg:" + ((msg == null) ? "null" : msg.toString()));
                    }
                    lock.wait(NetworkConfig.getNotWriteableCheckMills());
                } catch (InterruptedException exx) {
                    LOGGER.error(exx.getMessage());
                }
            }
        }
    }

    protected void doBeforeRpcHooks(String remoteAddr, Message request) {
        for (RemoteHook remoteHook: rpcHooks) {
            remoteHook.doBeforeRequest(remoteAddr, request);
        }
    }

    protected void doAfterRpcHooks(String remoteAddr, Message request, Object response) {
        for (RemoteHook rpcHook: rpcHooks) {
            rpcHook.doAfterResponse(remoteAddr, request, response);
        }
    }

    public abstract void destroyChannel(Channel channel);

    protected void sendAsync(Channel channel, Message message) {
        //TODO 待实现
        return;
    }
}
