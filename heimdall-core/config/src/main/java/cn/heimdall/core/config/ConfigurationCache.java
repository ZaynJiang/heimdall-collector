package cn.heimdall.core.config;

import cn.heimdall.core.utils.common.StringUtil;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigurationCache extends ConfigurationChangeListener{

    private static final String METHOD_PREFIX = "get";

    private static final String METHOD_LATEST_CONFIG = METHOD_PREFIX + "LatestConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationCache.class);

    private static final ConcurrentHashMap<String, ObjectWrapper> CONFIG_CACHE = new ConcurrentHashMap<>();

    private Map<String, HashSet<ConfigurationChangeListener>> configListenersMap = new HashMap<>();

    //获取配置对象的cache代理对象
    public Configuration getProxyConfiguration(Configuration originalConfiguration) {
        return (Configuration) Enhancer.create(Configuration.class,
                (MethodInterceptor)(proxy, method, args, methodProxy) -> {
                    if (method.getName().startsWith(METHOD_PREFIX)
                            && !method.getName().equalsIgnoreCase(METHOD_LATEST_CONFIG)) {
                        String rawConfigId = (String)args[0];
                        ObjectWrapper wrapper = CONFIG_CACHE.get(rawConfigId);
                        String type = method.getName().substring(METHOD_PREFIX.length());
                        if (!ObjectWrapper.supportType(type)) {
                            type = null;
                        }
                        if (null == wrapper) {
                            Object result = method.invoke(originalConfiguration, args);
                            if (result != null) {
                                wrapper = new ObjectWrapper(result, type);
                                CONFIG_CACHE.put(rawConfigId, wrapper);
                            }
                        }
                        //如果缓存有，就走缓存
                        return wrapper == null ? null : wrapper.convertData(type);
                    }
                    return method.invoke(originalConfiguration, args);
                });
    }


    public static void addConfigListener(String dataId, ConfigurationChangeListener... listeners) {
        if (StringUtil.isBlank(dataId)) {
            LOGGER.warn("addConfigListener, dataId is null");
            return;
        }
        synchronized (ConfigurationCache.class) {
            HashSet<ConfigurationChangeListener> listenerHashSet =
                    getInstance().configListenersMap.computeIfAbsent(dataId, key -> new HashSet<>());
            if (!listenerHashSet.contains(getInstance())) {
                ConfigurationFactory.getInstance().addConfigListener(dataId, getInstance());
                listenerHashSet.add(getInstance());
            }
            if (null != listeners && listeners.length > 0) {
                for (ConfigurationChangeListener listener : listeners) {
                    if (!listenerHashSet.contains(listener)) {
                        listenerHashSet.add(listener);
                        ConfigurationFactory.getInstance().addConfigListener(dataId, listener);
                    }
                }
            }
        }
    }

    public static ConfigurationCache getInstance() {
        return ConfigurationCacheInstance.INSTANCE;
    }

    private static class ConfigurationCacheInstance {
        private static final ConfigurationCache INSTANCE = new ConfigurationCache();
    }

    public void clear() {
        CONFIG_CACHE.clear();
    }

    @Override
    public void onChangeEvent(ConfigurationChangeEvent event) {
        ObjectWrapper wrapper = CONFIG_CACHE.get(event.getConfigId());
        // The wrapper.data only exists in the cache when it is not null.
        if (StringUtil.isNotBlank(event.getNewValue())) {
            if (wrapper == null) {
                CONFIG_CACHE.put(event.getConfigId(), new ObjectWrapper(event.getNewValue(), null));
            } else {
                Object newValue = new ObjectWrapper(event.getNewValue(), null).convertData(wrapper.getType());
                if (!Objects.equals(wrapper.getData(), newValue)) {
                    CONFIG_CACHE.put(event.getConfigId(), new ObjectWrapper(newValue, wrapper.getType()));
                }
            }
        } else {
            CONFIG_CACHE.remove(event.getConfigId());
        }
    }

    private static class ObjectWrapper {

        static final String INT = "Int";
        static final String BOOLEAN = "Boolean";
        static final String LONG = "Long";
        static final String SHORT = "Short";

        private final Object data;
        private final String type;

        ObjectWrapper(Object data, String type) {
            this.data = data;
            this.type = type;
        }

        public Object getData() {
            return data;
        }

        public String getType() {
            return type;
        }

        public Object convertData(String aType) {
            if (data != null && Objects.equals(type, aType)) {
                return data;
            }
            if (data != null) {
                if (INT.equals(aType)) {
                    return Integer.parseInt(data.toString());
                } else if (BOOLEAN.equals(aType)) {
                    return Boolean.parseBoolean(data.toString());
                } else if (LONG.equals(aType)) {
                    return Long.parseLong(data.toString());
                } else if (SHORT.equals(aType)) {
                    return Short.parseShort(data.toString());
                }
                return String.valueOf(data);
            }
            return null;
        }

        public static boolean supportType(String type) {
            return INT.equalsIgnoreCase(type)
                    || BOOLEAN.equalsIgnoreCase(type)
                    || LONG.equalsIgnoreCase(type)
                    || SHORT.equalsIgnoreCase(type);
        }
    }
}
