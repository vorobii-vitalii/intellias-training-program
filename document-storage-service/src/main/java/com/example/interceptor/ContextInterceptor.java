package com.example.interceptor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.tracing.ContextExtractor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class ContextInterceptor implements ServerInterceptor {
	private static final Logger LOGGER = LoggerFactory.getLogger(ContextInterceptor.class);
	private static final String CONTEXT = "CONTEXT";

	public static final Context.Key<io.opentelemetry.context.Context> CONTEXT_KEY = Context.key(CONTEXT);

	private final ContextExtractor contextExtractor;

	public ContextInterceptor(ContextExtractor contextExtractor) {
		this.contextExtractor = contextExtractor;
	}

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call,
			Metadata headers,
			ServerCallHandler<ReqT, RespT> next
	) {
		var contextValue = headers.get(Metadata.Key.of(CONTEXT, ASCII_STRING_MARSHALLER));
		LOGGER.info("Context = {}", contextValue);
		var tracingContext = Optional.ofNullable(contextValue)
				.map(contextExtractor::extract)
				.orElseGet(io.opentelemetry.context.Context::current);

		Context context = Context.current().withValue(CONTEXT_KEY, tracingContext);
		return Contexts.interceptCall(context, call, headers, next);
	}
}
