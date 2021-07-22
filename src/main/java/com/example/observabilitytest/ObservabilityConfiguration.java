package com.example.observabilitytest;

import java.util.List;

import javax.annotation.PreDestroy;

import brave.Tracing;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.observability.event.Recorder;
import org.springframework.observability.event.SimpleRecorder;
import org.springframework.observability.event.listener.RecordingListener;
import org.springframework.observability.event.listener.composite.CompositeRecordingListener;
import org.springframework.observability.micrometer.listener.MicrometerRecordingListener;
import org.springframework.observability.time.Clock;
import org.springframework.observability.tracing.Tracer;
import org.springframework.observability.tracing.brave.bridge.BraveTracer;
import org.springframework.observability.tracing.listener.TracingRecordingListener;
import org.springframework.observability.tracing.reporter.zipkin.RestTemplateSender;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

	@Bean
	ObservabilityEndpoint observabilityEndpoint(BufferingApplicationStartup startup, Recorder<?> recorder) {
		return new ObservabilityEndpoint(startup, recorder);
	}

	@Bean
	Recorder<?> simpleRecorder(CompositeRecordingListener compositeRecordingListener) {
		return new SimpleRecorder<>(compositeRecordingListener, Clock.SYSTEM);
	}

	@Bean
	CompositeRecordingListener compositeRecordingListener(List<RecordingListener<?>> listeners) {
		return new CompositeRecordingListener(listeners);
	}

	@Bean
	TracingRecordingListener tracingRecordingListener(Tracer tracer) {
		return new TracingRecordingListener(tracer);
	}

	@Bean
	MicrometerRecordingListener micrometerRecordingListener(MeterRegistry meterRegistry) {
		return new MicrometerRecordingListener(meterRegistry);
	}

	@Bean
	@PreDestroy()
	AsyncReporter<Span> reporter() {
		return AsyncReporter
				.builder(new RestTemplateSender(new RestTemplate(), "http://localhost:9411/", null, SpanBytesEncoder.JSON_V2))
				.build();
	}

	@Bean
	Tracer tracer(AsyncReporter<Span> reporter) {
		Tracing tracing = Tracing.newBuilder()
				.addSpanHandler(ZipkinSpanHandler.newBuilder(reporter).build())
				.sampler(Sampler.ALWAYS_SAMPLE)
				.build();
		return new BraveTracer(tracing.tracer());
	}
}
