package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.Ack;
import dev.pulsermm.proto.v1.MetricBatch;
import dev.pulsermm.proto.v1.MetricServiceGrpc;
import dev.pulsermm.proto.v1.SystemInfo;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GrpcService
public class MetricGrpcService extends MetricServiceGrpc.MetricServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MetricGrpcService.class);

    private final RestClient metricClient;

    public MetricGrpcService(@Value("${METRIC_SERVICE_URL:http://localhost:8082}") String metricUrl) {
        this.metricClient = RestClient.builder().baseUrl(metricUrl).build();
    }

    @Override
    public void pushMetrics(MetricBatch request, StreamObserver<Ack> responseObserver) {
        try {
            List<Map<String, Object>> samples = request.getSamplesList().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", s.getType());
                    m.put("value", s.getValue());
                    m.put("collectedAt", s.getCollectedAt());
                    m.put("labels", s.getLabelsMap());
                    return m;
                })
                .toList();

            metricClient.post()
                .uri("/internal/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("endpointId", request.getEndpointId(), "samples", samples))
                .retrieve()
                .toBodilessEntity();

            responseObserver.onNext(Ack.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("pushMetrics failed for {}: {}", request.getEndpointId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("push failed").asRuntimeException());
        }
    }

    @Override
    public void reportSystemInfo(SystemInfo request, StreamObserver<Ack> responseObserver) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("endpointId", request.getEndpointId());
            body.put("cpuModel", request.getCpu().getModel());
            body.put("cpuPhysical", request.getCpu().getPhysicalCores());
            body.put("cpuLogical", request.getCpu().getLogicalCores());
            body.put("cpuFreqMhz", request.getCpu().getFrequencyMhz());
            body.put("ramTotal", request.getMemory().getTotalBytes());
            body.put("swapTotal", request.getMemory().getSwapTotalBytes());
            body.put("collectedAt", request.getCollectedAt());

            List<Map<String, Object>> disks = request.getDisksList().stream()
                .map(d -> Map.<String, Object>of(
                    "device", d.getDevice(),
                    "mountpoint", d.getMountpoint(),
                    "fstype", d.getFstype(),
                    "totalBytes", d.getTotalBytes()
                ))
                .toList();
            body.put("disks", disks);

            List<Map<String, Object>> nics = request.getNicsList().stream()
                .map(n -> Map.<String, Object>of(
                    "name", n.getName(),
                    "mac", n.getMac(),
                    "addresses", n.getAddressesList(),
                    "mtu", n.getMtu()
                ))
                .toList();
            body.put("nics", nics);

            metricClient.post()
                .uri("/internal/system-info")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

            responseObserver.onNext(Ack.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("reportSystemInfo failed for {}: {}", request.getEndpointId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("system-info failed").asRuntimeException());
        }
    }
}
