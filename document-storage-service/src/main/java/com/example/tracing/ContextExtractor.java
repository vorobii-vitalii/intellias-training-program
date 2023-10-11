package com.example.tracing;

import java.util.Collections;

import javax.annotation.Nullable;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

public class ContextExtractor {
	public static final String IGNORED = "ignored";
	private final OpenTelemetry openTelemetry;

	public ContextExtractor(OpenTelemetry openTelemetry) {
		this.openTelemetry = openTelemetry;
	}

	public Context extract(String str) {
		return openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), str, new TextMapGetter<>() {
			@Override
			public Iterable<String> keys(String ignored) {
				return Collections.singleton(IGNORED);
			}

			@Nullable
			@Override
			public String get(@Nullable String s, String ignored) {
				return s;
			}
		});
	}
}
