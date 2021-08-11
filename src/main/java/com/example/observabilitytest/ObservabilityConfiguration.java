package com.example.observabilitytest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.observability.event.Recorder;
import org.springframework.observability.event.SimpleRecorder;
import org.springframework.observability.event.listener.RecordingListener;
import org.springframework.observability.event.listener.composite.CompositeRecordingListener;
import org.springframework.observability.event.listener.composite.FirstMatchingRecordingListener;
import org.springframework.observability.event.listener.composite.RunAllCompositeRecordingListener;
import org.springframework.observability.event.tag.Cardinality;
import org.springframework.observability.event.tag.Tag;
import org.springframework.observability.micrometer.listener.MicrometerRecordingListener;
import org.springframework.observability.time.Clock;
import org.springframework.observability.tracing.CurrentTraceContext;
import org.springframework.observability.tracing.Tracer;
import org.springframework.observability.tracing.brave.bridge.BraveCurrentTraceContext;
import org.springframework.observability.tracing.brave.bridge.BraveHttpClientHandler;
import org.springframework.observability.tracing.brave.bridge.BraveTracer;
import org.springframework.observability.tracing.http.HttpClientHandler;
import org.springframework.observability.tracing.listener.DefaultTracingRecordingListener;
import org.springframework.observability.tracing.listener.HttpClientTracingRecordingListener;
import org.springframework.observability.tracing.listener.TracingRecordingListener;
import org.springframework.observability.tracing.reporter.zipkin.RestTemplateSender;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.observability.ObservabilityClientHttpRequestInterceptor;
import org.springframework.web.client.observability.ObservabilityClientHttpRequestInterceptorTagsProvider;

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
		return new RunAllCompositeRecordingListener(listenersWithoutDuplicates(listeners));
	}

	private List<RecordingListener<?>> listenersWithoutDuplicates(List<RecordingListener<?>> listeners) {
		Set<RecordingListener<?>> recordingListeners = new HashSet<>();
		listeners.forEach(recordingListener -> {
			if (recordingListener instanceof CompositeRecordingListener) {
				List<? extends RecordingListener<?>> compositeListeners = ((CompositeRecordingListener) recordingListener).listeners();
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
	HttpClientTracingRecordingListener httpClientTracingRecordingListener(Tracer tracer, CurrentTraceContext currentTraceContext, HttpClientHandler httpClientHandler) {
		return new HttpClientTracingRecordingListener(tracer, currentTraceContext, httpClientHandler);
	}

	@Bean
	FirstMatchingRecordingListener tracingFirstMatchingRecordingListeners(List<TracingRecordingListener<?>> tracingRecordingListeners) {
		return new FirstMatchingRecordingListener(tracingRecordingListeners);
	}

	@Bean
	MicrometerRecordingListener micrometerRecordingListener(MeterRegistry meterRegistry) {
		return new MicrometerRecordingListener(meterRegistry);
	}
}

@Configuration
class BraveConfiguration {

	@Bean
	Sampler sampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	Tracing tracing(AsyncReporter<Span> reporter, Sampler sampler) {
		return Tracing.newBuilder()
				.addSpanHandler(ZipkinSpanHandler.newBuilder(reporter).build())
				.sampler(sampler)
				.build();
	}

	@Bean
	Tracer tracer(Tracing tracing) {
		return new BraveTracer(tracing.tracer());
	}

	@Bean
	CurrentTraceContext currentTraceContext(Tracing tracing) {
		return new BraveCurrentTraceContext(tracing.currentTraceContext());
	}

	@Bean
	HttpTracing httpTracing(Tracing tracing) {
		return HttpTracing.newBuilder(tracing)
				.clientRequestParser((request, context, span) -> {

				})
				.clientResponseParser((request, context, span) -> {

				})
				.build();
	}

	@Bean
	HttpClientHandler traceHttpClientHandler(HttpTracing httpTracing) {
		return new BraveHttpClientHandler(brave.http.HttpClientHandler.create(httpTracing));
	}
}

@Configuration
class ZipkinConfiguration {

	@Bean(destroyMethod = "close")
	AsyncReporter<Span> reporter() {
		return AsyncReporter
				.builder(new RestTemplateSender(new RestTemplate(), "http://localhost:9411/", null, SpanBytesEncoder.JSON_V2))
				.build();
	}
}

@Configuration
class InstrumentationConfiguration {
	@Bean
	ObservabilityClientHttpRequestInterceptor observabilityClientHttpRequestInterceptor(Recorder<?> recorder, ObservabilityClientHttpRequestInterceptorTagsProvider tagsProvider) {
		return new ObservabilityClientHttpRequestInterceptor(recorder, tagsProvider);
	}


	@Bean
	RestTemplate restTemplate(ObservabilityClientHttpRequestInterceptor observabilityClientHttpRequestInterceptor) {
		return new RestTemplateBuilder()
				.additionalInterceptors(observabilityClientHttpRequestInterceptor)
				.build();
	}

	@Bean
	ObservabilityClientHttpRequestInterceptorTagsProvider observabilityClientHttpRequestInterceptorTagsProvider() {
		return (urlTemplate, request, response) -> Collections.singletonList(Tag.of("http.method", request.method(), Cardinality.LOW));
	}
}