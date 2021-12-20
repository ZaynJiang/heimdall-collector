package cn.heimdall.compute.analyzer.compute;

import cn.heimdall.core.message.MessageBody;
import cn.heimdall.core.message.body.origin.MessageTreeRequest;
import cn.heimdall.core.config.constants.MessageConstants;
import cn.heimdall.compute.metric.DefaultMetricKey;
import cn.heimdall.compute.metric.Metric;
import cn.heimdall.compute.metric.MetricKey;
import cn.heimdall.compute.metric.SpanMetric;
import cn.heimdall.compute.metric.SpanMetricInvoker;
import cn.heimdall.core.message.trace.SpanLog;
import cn.heimdall.core.message.trace.TraceLog;
import cn.heimdall.core.utils.annotation.LoadLevel;
import cn.heimdall.core.utils.common.CollectionUtil;
import cn.heimdall.core.utils.constants.LoadLevelConstants;

import java.util.List;

@LoadLevel(name = LoadLevelConstants.SPAN_COMPUTE)
public class SpanLogCompute extends AbstractMetricCompute {

    public SpanLogCompute() {
        super();
    }

    @Override
    public void compute(MessageBody messageBody) {
        MessageTreeRequest treeBody = (MessageTreeRequest) messageBody;
        List<SpanLog> childSpanLogs = treeBody.getSpanLogs();
        if (CollectionUtil.isEmpty(childSpanLogs)) {
            childSpanLogs.stream().forEach(this::doInvokeMetric);
        }
    }

    @Override
    protected void doInvokeMetric(TraceLog tracelog) {
        MetricKey metricKey = wrapMetricKey(tracelog);
        SpanMetric spanMetric = (SpanMetric) getMetricInvoker(metricKey);
        SpanLog spanLog = (SpanLog) tracelog;
        spanMetric.addRT(spanLog.getCostInMillis());
        spanMetric.addCount(1);
        if (spanLog.isErrorTag()) {
            spanMetric.addException(1);
        }
    }

    @Override
    protected Metric newMetric() {
        return new SpanMetricInvoker(MessageConstants.METRIC_SPAN_WINDOW_INTERVAL,
                MessageConstants.METRIC_SPAN_WINDOW_COUNT);
    }

    @Override
    protected MetricKey wrapMetricKey(TraceLog tracelog) {
        SpanLog spanLog = (SpanLog) tracelog;
        return new DefaultMetricKey(spanLog.getDomain(), spanLog.getIpAddress(),
                spanLog.getType(), spanLog.getName());
    }

}
