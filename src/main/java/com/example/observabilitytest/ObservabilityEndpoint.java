package com.example.observabilitytest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.core.log.LogAccessor;
import org.springframework.core.metrics.StartupStep;
import org.springframework.observability.event.Recorder;
import org.springframework.observability.event.interval.IntervalEvent;
import org.springframework.observability.event.interval.IntervalRecording;
import org.springframework.observability.event.tag.Cardinality;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;

@Endpoint(id = "observability")
public class ObservabilityEndpoint {

	private static final LogAccessor log = new LogAccessor(ObservabilityEndpoint.class);

	private final BufferingApplicationStartup applicationStartup;

	private final Recorder<?> recorder;

	/**
	 * Creates a new {@code StartupEndpoint} that will describe the timeline of buffered
	 * application startup events.
	 * @param applicationStartup the application startup
	 * @param recorder recorder
	 */
	public ObservabilityEndpoint(BufferingApplicationStartup applicationStartup, Recorder<?> recorder) {
		this.applicationStartup = applicationStartup;
		this.recorder = recorder;
	}

	@ReadOperation
	public void observabilitySnapshot() {
		StartupTimeline startupTimeline = this.applicationStartup.getBufferedTimeline();
		observeStartupTimeline(startupTimeline);
	}

	private void observeStartupTimeline(StartupTimeline startupTimeline) {
		if (startupTimeline.getEvents().isEmpty()) {
			return;
		}

		Map<Long, Node> stepMap = startupTimeline.getEvents().stream()
				.map(Node::new)
				.collect(toMap(node -> node.startupStep.getId(), Function.identity()));


		Node artificalRoot = new Node(new StartupStep() {
			@Override
			public String getName() {
				return "application-context";
			}

			@Override
			public long getId() {
				return -100;
			}

			@Override
			public Long getParentId() {
				return null;
			}

			@Override
			public StartupStep tag(String key, String value) {
				return null;
			}

			@Override
			public StartupStep tag(String key, Supplier<String> value) {
				return null;
			}

			@Override
			public Tags getTags() {
				return null;
			}

			@Override
			public void end() {

			}
		}, toNanos(startupTimeline.getStartTime()), stepMap.entrySet().stream().max(Map.Entry.comparingByKey()).get().getValue().endTimeNanos);

		for (Map.Entry<Long, Node> entry : stepMap.entrySet()) {
			Node current = entry.getValue();
			Node parent = stepMap.get(current.startupStep.getParentId() != null ? current.startupStep.getParentId() : artificalRoot);
			parent = parent != null ? parent : artificalRoot;
			parent.children.add(current);
		}
		visit(artificalRoot);
	}

	private void visit(Node node) {
		if (node == null) {
			return;
		}
		String name = node.startupStep.getName();
		IntervalRecording<?> recording = recorder.recordingFor(new IntervalEvent() {
			@Override
			public String getName() {
				return nameFromEvent(name);
			}

			@Override
			public String getDescription() {
				return "";
			}
		}).tag(org.springframework.observability.event.tag.Tag.of("event", name, Cardinality.HIGH))
				.start(node.startTimeNanos);
		StartupStep.Tags tags = node.startupStep.getTags();
		if (tags != null) {
			for (StartupStep.Tag tag : tags) {
				String key = tag.getKey();
				String value = tag.getValue();
				if (key.equals("beanName") || key.equals("postProcessor")) {
					recording.name(EventNameUtil.toLowerHyphen(name(value)));
				}
				recording.tag(org.springframework.observability.event.tag.Tag.of(EventNameUtil.toLowerHyphen(key), value, Cardinality.HIGH));
			}
		}

		for (Node child : node.children) {
			visit(child);
		}
		recording.stop(node.endTimeNanos);
	}

	// TODO: Check if nano calculation is fine
	private long toNanos(Instant time) {
		return TimeUnit.SECONDS.toNanos(time.getEpochSecond()) + time.getNano();
	}

	private String nameFromEvent(String name) {
		String[] split = name.split("\\.");
		if (split.length > 1) {
			return split[split.length - 2] + "-" + split[split.length -1];
		}
		return name;
	}

	private String name(String name) {
		String afterDotOrDollar = afterDotOrDollar(name);
		int index = afterDotOrDollar.lastIndexOf("@");
		if (index != -1) {
			return afterDotOrDollar.substring(0, index);
		}
		return afterDotOrDollar;
	}

	private String afterDotOrDollar(String name) {
		int index = name.lastIndexOf("$");
		if (index != -1) {
			return name.substring(index + 1);
		}
		index = name.lastIndexOf(".");
		if (index != -1) {
			return name.substring(index + 1);
		}
		return name;
	}


	@WriteOperation
	public void observability() {
		StartupTimeline startupTimeline = this.applicationStartup.drainBufferedTimeline();
		observeStartupTimeline(startupTimeline);
	}

	static final class EventNameUtil {

		static final int MAX_NAME_LENGTH = 50;

		private EventNameUtil() {

		}

		/**
		 * Shortens the name of a span.
		 * @param name name to shorten
		 * @return shortened name
		 */
		public static String shorten(String name) {
			if (!StringUtils.hasText(name)) {
				return name;
			}
			int maxLength = Math.min(name.length(), MAX_NAME_LENGTH);
			return name.substring(0, maxLength);
		}

		/**
		 * Converts the name to a lower hyphen version.
		 * @param name name to change
		 * @return changed name
		 */
		public static String toLowerHyphen(String name) {
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < name.length(); i++) {
				char c = name.charAt(i);
				if (Character.isUpperCase(c)) {
					if (i != 0) {
						result.append('-');
					}
					result.append(Character.toLowerCase(c));
				}
				else {
					result.append(c);
				}
			}
			return EventNameUtil.shorten(result.toString());
		}

	}

	static class Node {
		private final StartupStep startupStep;
		private final long startTimeNanos;
		private final long endTimeNanos;
		List<Node> children = new ArrayList<>();

		Node(StartupTimeline.TimelineEvent timelineEvent) {
			this.startupStep = timelineEvent.getStartupStep();
			this.startTimeNanos = toNanos(timelineEvent.getStartTime());
			this.endTimeNanos = toNanos(timelineEvent.getEndTime());
		}

		Node(StartupStep startupStep, long startTimeNanos, long endTimeNanos) {
			this.startupStep = startupStep;
			this.startTimeNanos = startTimeNanos;
			this.endTimeNanos = endTimeNanos;
		}

		// TODO: Check if nano calculation is fine
		private long toNanos(Instant time) {
			return TimeUnit.SECONDS.toNanos(time.getEpochSecond()) + time.getNano();
		}
	}

}