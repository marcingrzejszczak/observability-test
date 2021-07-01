package com.example.observabilitytest;

import java.io.IOException;

import brave.Tracing;
import brave.sampler.Sampler;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.metrics.observability.ObservabilityApplicationStartup;
import org.springframework.observability.tracing.Tracer;
import org.springframework.observability.tracing.brave.bridge.BraveTracer;
import org.springframework.observability.tracing.reporter.zipkin.RestTemplateSender;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ObservabilityTestApplication {

	public static void main(String[] args) throws IOException {
		AsyncReporter<Span> reporter = AsyncReporter
				.builder(new RestTemplateSender(new RestTemplate(), "http://localhost:9411/", null, SpanBytesEncoder.JSON_V2))
				.build();
		Tracing tracing = Tracing.newBuilder()
				.addSpanHandler(ZipkinSpanHandler.newBuilder(reporter).build())
				.sampler(Sampler.ALWAYS_SAMPLE)
				.build();
		Tracer tracer = new BraveTracer(tracing.tracer());
		ObservabilityApplicationStartup observabilityApplicationStartup = new ObservabilityApplicationStartup(tracer);
		try {
			new SpringApplicationBuilder(ObservabilityTestApplication.class)
					.applicationStartup(observabilityApplicationStartup)
					.run(args);
		} finally {
			observabilityApplicationStartup.endRootSpan();
			reporter.flush();
			reporter.close();
		}
	}

}
