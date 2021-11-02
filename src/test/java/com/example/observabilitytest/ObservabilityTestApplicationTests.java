package com.example.observabilitytest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.Tracer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.observability.instrumentation.ContinuedRunnable;
import org.springframework.core.observability.instrumentation.ObservedRunnable;

@SpringBootTest
class ObservabilityTestApplicationTests {

	@Autowired MeterRegistry registry;

	ExecutorService executorService = Executors.newCachedThreadPool();

	@Autowired TestSpanHandler testSpanHandler;

	@Autowired Tracer tracer;

	@BeforeEach
	void setup() {
		this.testSpanHandler.clear();
	}

	@Test
	void should_create_a_new_span_in_a_new_thread() throws ExecutionException, InterruptedException {
		Timer.Sample sample = Timer.start(registry);
		Span span = this.tracer.currentSpan();
		System.out.println(toString(span));
		this.executorService.submit(new ObservedRunnable(this.registry, () -> {
			BDDAssertions.then(tracer.currentSpan().context().traceId().equals(span.context().traceId()));
			System.out.println(toString(tracer.currentSpan()));
		})).get();

		sample.stop(Timer.builder("test").register(registry));

		BDDAssertions.then(testSpanHandler.spans()).hasSize(2);
	}

	@Test
	void should_continue_a_new_span_in_a_new_thread() throws ExecutionException, InterruptedException {
		Timer.Sample sample = Timer.start(registry);
		Span span = this.tracer.currentSpan();
		System.out.println(toString(span));
			this.executorService.submit(new ContinuedRunnable(this.registry, () -> {
				BDDAssertions.then(tracer.currentSpan().context().traceId().equals(span.context().traceId()));
				System.out.println(toString(tracer.currentSpan()));
			})).get();

		BDDAssertions.then(testSpanHandler.spans()).hasSize(1);
	}

	private String toString(Span span) {
		return new StringBuilder(span.context().traceId())
				.append("/").append(span.context().parentId())
				.append(">").append(span.context().spanId())
				.toString();
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

