package com.example.observabilitytest.config.observability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.observabilitytest.ObservabilityEndpoint;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.observability.event.Recorder;
import org.springframework.core.observability.event.SimpleRecorder;
import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.event.listener.composite.AllMatchingCompositeRecordingListener;
import org.springframework.core.observability.event.listener.composite.CompositeRecordingListener;
import org.springframework.core.observability.event.listener.composite.FirstMatchingCompositeRecordingListener;
import org.springframework.core.observability.listener.metrics.MetricsRecordingListener;
import org.springframework.core.observability.listener.metrics.MicrometerRecordingListener;
import org.springframework.core.observability.listener.tracing.DefaultTracingRecordingListener;
import org.springframework.core.observability.listener.tracing.HttpClientTracingRecordingListener;
import org.springframework.core.observability.listener.tracing.HttpServerTracingRecordingListener;
import org.springframework.core.observability.listener.tracing.TracingRecordingListener;
import org.springframework.core.observability.time.Clock;
import org.springframework.core.observability.tracing.Tracer;
import org.springframework.core.observability.tracing.http.HttpClientHandler;
import org.springframework.core.observability.tracing.http.HttpServerHandler;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

	@Bean
	@ConditionalOnBean(BufferingApplicationStartup.class)
	ObservabilityEndpoint observabilityEndpoint(BufferingApplicationStartup startup, Recorder<?> recorder) {
		return new ObservabilityEndpoint(startup, recorder);
	}

	@Bean
	Recorder<?> simpleRecorder(CompositeRecordingListener compositeRecordingListener) {
		return new SimpleRecorder<>(compositeRecordingListener, Clock.SYSTEM);
	}

	@Bean
	CompositeRecordingListener compositeRecordingListener(List<RecordingListener<?>> listeners) {
		return new AllMatchingCompositeRecordingListener(listenersWithoutDuplicates(listeners));
	}

	private List<RecordingListener<?>> listenersWithoutDuplicates(List<RecordingListener<?>> listeners) {
		Set<RecordingListener<?>> recordingListeners = new HashSet<>();
		listeners.forEach(recordingListener -> {
			if (recordingListener instanceof CompositeRecordingListener) {
				List<? extends RecordingListener<?>> compositeListeners = ((CompositeRecordingListener) recordingListener).getListeners();
				compositeListeners.forEach(recordingListeners::remove);
				recordingListeners.add(recordingListener);
			}
			else {
				recordingListeners.add(recordingListener);
			}
		});
		return new ArrayList<>(recordingListeners);
	}

	@Bean
	@Order
	DefaultTracingRecordingListener defaultTracingRecordingListener(Tracer tracer) {
		return new DefaultTracingRecordingListener(tracer);
	}

	@Bean
	@Order(value = Ordered.LOWEST_PRECEDENCE - 10)
	HttpClientTracingRecordingListener httpClientTracingRecordingListener(Tracer tracer, HttpClientHandler httpClientHandler) {
		return new HttpClientTracingRecordingListener(tracer, httpClientHandler);
	}

	@Bean
	@Order(value = Ordered.LOWEST_PRECEDENCE - 10)
	HttpServerTracingRecordingListener httpServerTracingRecordingListener(Tracer tracer, HttpServerHandler httpServerHandler) {
		return new HttpServerTracingRecordingListener(tracer, httpServerHandler);
	}

	@Bean
	FirstMatchingCompositeRecordingListener tracingFirstMatchingRecordingListeners(List<TracingRecordingListener> tracingRecordingListeners) {
		return new FirstMatchingCompositeRecordingListener(tracingRecordingListeners);
	}

	@Bean
	FirstMatchingCompositeRecordingListener metricsFirstMatchingRecordingListeners(List<MetricsRecordingListener<?>> metricsRecordingListeners) {
		return new FirstMatchingCompositeRecordingListener(metricsRecordingListeners);
	}

	@Bean
	@Order
	MicrometerRecordingListener micrometerRecordingListener(MeterRegistry meterRegistry) {
		return new MicrometerRecordingListener(meterRegistry);
	}


	// FirstMatching -> Tracing
	// HttpClient
	// HttpServer
	// Default
	// FirstMatching -> Metrics
	// OpenFeignMetrics
	// Default
}

