package com.example.observabilitytest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.OnlyOnceLoggingDenyMeterFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.observability.event.Recorder;
import org.springframework.observability.event.SimpleRecorder;
import org.springframework.observability.event.listener.RecordingListener;
import org.springframework.observability.event.listener.composite.AllMatchingCompositeRecordingListener;
import org.springframework.observability.event.listener.composite.CompositeRecordingListener;
import org.springframework.observability.event.listener.composite.FirstMatchingCompositeRecordingListener;
import org.springframework.observability.micrometer.listener.MetricsRecordingListener;
import org.springframework.observability.micrometer.listener.MicrometerRecordingListener;
import org.springframework.observability.time.Clock;
import org.springframework.observability.tracing.CurrentTraceContext;
import org.springframework.observability.tracing.Tracer;
import org.springframework.observability.tracing.brave.bridge.BraveCurrentTraceContext;
import org.springframework.observability.tracing.brave.bridge.BraveHttpClientHandler;
import org.springframework.observability.tracing.brave.bridge.BraveHttpServerHandler;
import org.springframework.observability.tracing.brave.bridge.BraveTracer;
import org.springframework.observability.tracing.http.HttpClientHandler;
import org.springframework.observability.tracing.http.HttpServerHandler;
import org.springframework.observability.tracing.listener.DefaultTracingRecordingListener;
import org.springframework.observability.tracing.listener.HttpClientTracingRecordingListener;
import org.springframework.observability.tracing.listener.HttpServerTracingRecordingListener;
import org.springframework.observability.tracing.listener.TracingRecordingListener;
import org.springframework.observability.tracing.reporter.zipkin.RestTemplateSender;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.observability.DefaultObservabilityClientHttpRequestInterceptorTagsProvider;
import org.springframework.web.client.observability.ObservabilityClientHttpRequestInterceptor;
import org.springframework.web.client.observability.ObservabilityClientHttpRequestInterceptorTagsProvider;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.observability.DefaultWebMvcTagsProvider;
import org.springframework.web.servlet.mvc.observability.HandlerParser;
import org.springframework.web.servlet.mvc.observability.RecordingCustomizingHandlerInterceptor;
import org.springframework.web.servlet.mvc.observability.WebMvcTagsContributor;
import org.springframework.web.servlet.mvc.observability.WebMvcTagsProvider;
import org.springframework.web.servlet.mvc.observability.WebMvcObservabilityFilter;

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
	HttpClientTracingRecordingListener httpClientTracingRecordingListener(Tracer tracer, CurrentTraceContext currentTraceContext, HttpClientHandler httpClientHandler) {
		return new HttpClientTracingRecordingListener(tracer, currentTraceContext, httpClientHandler);
	}

	@Bean
	@Order(value = Ordered.LOWEST_PRECEDENCE - 10)
	HttpServerTracingRecordingListener httpServerTracingRecordingListener(Tracer tracer, CurrentTraceContext currentTraceContext, HttpServerHandler httpServerHandler) {
		return new HttpServerTracingRecordingListener(tracer, currentTraceContext, httpServerHandler);
	}

	@Bean
	FirstMatchingCompositeRecordingListener tracingFirstMatchingRecordingListeners(List<TracingRecordingListener<?>> tracingRecordingListeners) {
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
				.serverRequestParser((request, context, span) -> {

				})
				.serverResponseParser((request, context, span) -> {

				})
				.build();
	}

	@Bean
	HttpClientHandler traceHttpClientHandler(HttpTracing httpTracing) {
		return new BraveHttpClientHandler(brave.http.HttpClientHandler.create(httpTracing));
	}

	@Bean
	HttpServerHandler traceHttpServerHandler(HttpTracing httpTracing) {
		return new BraveHttpServerHandler(brave.http.HttpServerHandler.create(httpTracing));
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
class InstrumentationHttpClientConfiguration {
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
		return new DefaultObservabilityClientHttpRequestInterceptorTagsProvider();
	}
}

@Configuration
class InstrumentationHttpServerConfiguration {

	private final MetricsProperties properties;

	public InstrumentationHttpServerConfiguration(MetricsProperties properties) {
		this.properties = properties;
	}

	@Bean
	@ConditionalOnMissingBean(WebMvcTagsProvider.class)
	public DefaultWebMvcTagsProvider webMvcTagsProvider(ObjectProvider<WebMvcTagsContributor> contributors) {
		return new DefaultWebMvcTagsProvider(this.properties.getWeb().getServer().getRequest().isIgnoreTrailingSlash(),
				contributors.orderedStream().collect(Collectors.toList()));
	}

	@Bean
	public FilterRegistrationBean<WebMvcObservabilityFilter> webMvcMetricsFilter(Recorder<?> registry,
			WebMvcTagsProvider tagsProvider) {
		String metricName = "http.server.requests";
		WebMvcObservabilityFilter filter = new WebMvcObservabilityFilter(registry, tagsProvider, metricName);
		FilterRegistrationBean<WebMvcObservabilityFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
		// TODO: Verify how we set this in Sleuth
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
		return registration;
	}

	@Bean
	@Order(0)
	public MeterFilter metricsHttpServerUriTagFilter() {
		String metricName = this.properties.getWeb().getServer().getRequest().getMetricName();
		MeterFilter filter = new OnlyOnceLoggingDenyMeterFilter(
				() -> String.format("Reached the maximum number of URI tags for '%s'.", metricName));
		// http.uri must be pushed to a constant
		return MeterFilter.maximumAllowableTags(metricName, "http.uri", this.properties.getWeb().getServer().getMaxUriTags(),
				filter);
	}

	@Bean
	HandlerParser handlerParser() {
		return new HandlerParser();
	}

	@Bean
	RecordingCustomizingHandlerInterceptor recordingCustomizingHandlerInterceptor(HandlerParser handlerParser) {
		return new RecordingCustomizingHandlerInterceptor(handlerParser);
	}

	@Bean
	ObservabilityWebMvcConfigurer observabilityWebMvcConfigurer(RecordingCustomizingHandlerInterceptor interceptor) {
		return new ObservabilityWebMvcConfigurer(interceptor);
	}

//
//	@Bean
//	RecordingCustomizingAsyncHandlerInterceptor recordingCustomizingAsyncHandlerInterceptor(HandlerParser handlerParser) {
//		return new RecordingCustomizingAsyncHandlerInterceptor(handlerParser);
//	}

	/**
	 * {@link WebMvcConfigurer} to add metrics interceptors.
	 */
	static class ObservabilityWebMvcConfigurer implements WebMvcConfigurer {

		private final RecordingCustomizingHandlerInterceptor interceptor;

		ObservabilityWebMvcConfigurer(RecordingCustomizingHandlerInterceptor interceptor) {
			this.interceptor = interceptor;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(this.interceptor);
		}

	}
}