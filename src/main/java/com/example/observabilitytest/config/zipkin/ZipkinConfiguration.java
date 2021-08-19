package com.example.observabilitytest.config.zipkin;

import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.observability.tracing.reporter.zipkin.RestTemplateSender;
import org.springframework.web.client.RestTemplate;

@Configuration
class ZipkinConfiguration {

	@Bean(destroyMethod = "close")
	AsyncReporter<Span> reporter() {
		return AsyncReporter
				.builder(new RestTemplateSender(new RestTemplate(), "http://localhost:9411/", null, SpanBytesEncoder.JSON_V2))
				.build();
	}
}
