package com.example.observabilitytest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.observability.event.Recorder;
import org.springframework.core.observability.event.interval.IntervalEvent;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.instrumentation.ContinuedRunnable;
import org.springframework.core.observability.instrumentation.ObservedRunnable;
import org.springframework.core.observability.tracing.Span;
import org.springframework.core.observability.tracing.Tracer;

@SpringBootTest
class ObservabilityTestApplicationTests {

	@Autowired Recorder<?> recorder;

	ExecutorService executorService = Executors.newCachedThreadPool();

	@Autowired TestSpanHandler testSpanHandler;

	@Autowired Tracer tracer;

	@BeforeEach
	void setup() {
		this.testSpanHandler.clear();
	}

	@Test
	void should_create_a_new_span_in_a_new_thread() throws ExecutionException, InterruptedException {

		Span span = this.tracer.nextSpan().start();
		System.out.println(span);
		try(Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			this.executorService.submit(new ObservedRunnable(this.recorder, () -> "my-runnable", () -> {
				BDDAssertions.then(tracer.currentSpan().context().traceId().equals(span.context().traceId()));
				System.out.println(tracer.currentSpan());
			})).get();
		}
		span.end();

		BDDAssertions.then(testSpanHandler.spans()).hasSize(2);
	}

	@Test
	void should_continue_a_new_span_in_a_new_thread() throws ExecutionException, InterruptedException {

		Span span = this.tracer.nextSpan().start();
		System.out.println(span);
		try(Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
			this.executorService.submit(new ContinuedRunnable(this.recorder, () -> "my-runnable", () -> {
				BDDAssertions.then(tracer.currentSpan().context().traceId().equals(span.context().traceId()));
				System.out.println(tracer.currentSpan());
			})).get();
		}
		span.end();

		BDDAssertions.then(testSpanHandler.spans()).hasSize(1);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		TestSpanHandler braveTestSpanHandler() {
			return new TestSpanHandler();
		}

	}
}

