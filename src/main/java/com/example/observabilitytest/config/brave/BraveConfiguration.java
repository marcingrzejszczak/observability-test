package com.example.observabilitytest.config.brave;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.observability.tracing.CurrentTraceContext;
import org.springframework.core.observability.tracing.Tracer;
import org.springframework.core.observability.tracing.http.HttpClientHandler;
import org.springframework.core.observability.tracing.http.HttpServerHandler;
import org.springframework.observability.tracing.brave.bridge.BraveBaggageManager;
import org.springframework.observability.tracing.brave.bridge.BraveCurrentTraceContext;
import org.springframework.observability.tracing.brave.bridge.BraveHttpClientHandler;
import org.springframework.observability.tracing.brave.bridge.BraveHttpServerHandler;
import org.springframework.observability.tracing.brave.bridge.BraveTracer;

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
	BraveBaggageManager braveBaggageManager() {
		return new BraveBaggageManager();
	}

	@Bean
	Tracer tracer(Tracing tracing, BraveBaggageManager baggageManager) {
		return new BraveTracer(tracing.tracer(), tracing.currentTraceContext(), baggageManager);
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
