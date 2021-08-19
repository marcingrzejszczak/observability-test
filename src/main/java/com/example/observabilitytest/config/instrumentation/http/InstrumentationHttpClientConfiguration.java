package com.example.observabilitytest.config.instrumentation.http;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.observability.event.Recorder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.observability.DefaultObservabilityClientHttpRequestInterceptorTagsProvider;
import org.springframework.web.client.observability.ObservabilityClientHttpRequestInterceptor;
import org.springframework.web.client.observability.ObservabilityClientHttpRequestInterceptorTagsProvider;

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
