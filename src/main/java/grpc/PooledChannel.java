package grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import tcp.server.RoundRobinProvider;

import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PooledChannel extends Channel {
    private final RoundRobinProvider<Channel> roundRobinProvider;

    public PooledChannel(int poolSize, Supplier<Channel> channelCreator) {
        roundRobinProvider = new RoundRobinProvider<>(IntStream.range(0, poolSize)
                .mapToObj(i -> channelCreator.get())
                .collect(Collectors.toList()));
    }

    @Override
    public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
        return roundRobinProvider.get().newCall(methodDescriptor, callOptions);
    }

    @Override
    public String authority() {
        return roundRobinProvider.get().authority();
    }
}
