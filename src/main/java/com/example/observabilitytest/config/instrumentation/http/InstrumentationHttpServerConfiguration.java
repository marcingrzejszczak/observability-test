package com.example.observabilitytest.config.instrumentation.http;

import java.util.stream.Collectors;

import javax.servlet.DispatcherType;

import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.OnlyOnceLoggingDenyMeterFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.observability.event.Recorder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.observability.DefaultWebMvcTagsProvider;
import org.springframework.web.servlet.mvc.observability.HandlerParser;
import org.springframework.web.servlet.mvc.observability.RecordingCustomizingHandlerInterceptor;
import org.springframework.web.servlet.mvc.observability.WebMvcObservabilityFilter;
import org.springframework.web.servlet.mvc.observability.WebMvcTagsContributor;
import org.springframework.web.servlet.mvc.observability.WebMvcTagsProvider;

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
	InstrumentationHttpServerConfiguration.ObservabilityWebMvcConfigurer observabilityWebMvcConfigurer(RecordingCustomizingHandlerInterceptor interceptor) {
		return new InstrumentationHttpServerConfiguration.ObservabilityWebMvcConfigurer(interceptor);
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
